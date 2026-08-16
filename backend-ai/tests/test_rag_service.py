from types import SimpleNamespace
import threading

from services.rag_service import RagService, SiliconFlowEmbedding
from services.rag_pipeline import estimate_tokens


class FakeCollection:
    def __init__(self, documents, semantic_error=None, count_error=None):
        self.documents = documents
        self.semantic_error = semantic_error
        self.count_error = count_error
        self.query_count = 0

    def count(self):
        if self.count_error:
            raise self.count_error
        return len(self.documents)

    def query(self, **_kwargs):
        self.query_count += 1
        if self.semantic_error:
            raise self.semantic_error
        return {"documents": [self.documents]}

    def get(self, **_kwargs):
        return {"documents": self.documents}


class MutableCollection:
    def __init__(self, records=None, fail_after_write=False):
        self.records = dict(records or {})
        self.fail_after_write = fail_after_write

    def count(self):
        return len(self.records)

    def get(self, ids=None, **_kwargs):
        selected = ids or list(self.records)
        rows = [(item_id, self.records[item_id]) for item_id in selected if item_id in self.records]
        return {
            "ids": [item_id for item_id, _ in rows],
            "documents": [row["document"] for _, row in rows],
            "metadatas": [row["metadata"] for _, row in rows],
        }

    def upsert(self, *, documents, ids, metadatas):
        for item_id, document, metadata in zip(ids, documents, metadatas):
            self.records[item_id] = {"document": document, "metadata": metadata}
        if self.fail_after_write:
            raise RuntimeError("provider failed after partial write")

    def delete(self, *, ids):
        for item_id in ids:
            self.records.pop(item_id, None)


def make_service(collection, docs_dir):
    service = object.__new__(RagService)
    service._settings = SimpleNamespace(siliconflow_api_key="configured")
    service._collection = collection
    service._docs_dir = str(docs_dir)
    service._chunk_size = 500
    service._chunk_overlap = 100
    service._top_k = 5
    service._semantic_retry_after = 0.0
    service._lexical_documents_cache = None
    return service


def make_index_service(collection, docs_dir, *, semantic_key=""):
    service = make_service(collection, docs_dir)
    service._settings = SimpleNamespace(
        siliconflow_api_key=semantic_key,
        embedding_model="BAAI/bge-large-zh-v1.5",
        embedding_dimensions=None,
        rag_chunk_target_tokens=280,
        rag_chunk_max_tokens=360,
        rag_chunk_overlap_tokens=60,
        chroma_persist_dir=str(docs_dir / "cache"),
    )
    service._embed_batch_size = 64
    service._embed_max_chars_per_batch = 200_000
    service._bm25_index_cache = None
    return service


def test_remote_embedding_failure_falls_back_and_opens_a_short_circuit(tmp_path):
    collection = FakeCollection(
        [
            "Redis 缓存击穿可以使用互斥锁、逻辑过期和请求合并来治理。",
            "JVM 垃圾回收器需要结合暂停时间和吞吐量选择。",
        ],
        semantic_error=RuntimeError("provider unavailable"),
    )
    service = make_service(collection, tmp_path)

    first = service.retrieve("Redis 缓存击穿与互斥锁", top_k=1)
    second = service.retrieve("JVM 垃圾回收", top_k=1)

    assert "缓存击穿" in first[0]
    assert "垃圾回收" in second[0]
    assert collection.query_count == 1


def test_empty_vector_store_can_retrieve_from_local_markdown(tmp_path):
    (tmp_path / "backend.md").write_text(
        "RocketMQ 消息幂等可以使用业务唯一键和消费记录表。",
        encoding="utf-8",
    )
    service = make_service(FakeCollection([]), tmp_path)

    hits = service.retrieve("RocketMQ 消息幂等", top_k=3)

    assert hits
    assert "业务唯一键" in hits[0]


def test_corrupt_vector_store_does_not_disable_local_knowledge_files(tmp_path):
    (tmp_path / "database.md").write_text(
        "MySQL 幻读可以结合隔离级别、MVCC 和 next-key lock 分析。",
        encoding="utf-8",
    )
    service = make_service(
        FakeCollection([], count_error=RuntimeError("corrupt collection")),
        tmp_path,
    )

    hits = service.retrieve("MySQL MVCC 幻读", top_k=1)

    assert hits
    assert "next-key lock" in hits[0]


def test_embedding_adapter_caps_provider_input_without_changing_batch_shape():
    captured = []

    class FakeEmbeddingsApi:
        def create(self, *, input, model, encoding_format):
            captured.extend(input)
            assert model == "test-model"
            assert encoding_format == "float"
            return SimpleNamespace(
                data=[
                    SimpleNamespace(index=index, embedding=[float(index), 1.0])
                    for index, _ in enumerate(input)
                ]
            )

    adapter = object.__new__(SiliconFlowEmbedding)
    adapter._client = SimpleNamespace(embeddings=FakeEmbeddingsApi())
    adapter._model = "test-model"
    adapter._batch_size = 2
    adapter._max_tokens = 32
    adapter._dimensions = None

    original = "中文技术内容" * 30
    vectors = adapter([original, "short"])

    assert len(vectors) == 2
    assert len(captured) == 2
    assert estimate_tokens(captured[0]) <= 32
    assert len(captured[0]) <= 32
    assert captured[1] == "short"
    assert len(captured[0]) < len(original)


def test_embedding_adapter_rejects_empty_provider_inputs():
    adapter = object.__new__(SiliconFlowEmbedding)

    try:
        adapter(["valid", "  "])
    except ValueError as exc:
        assert "empty text" in str(exc)
        assert "1" in str(exc)
    else:  # pragma: no cover
        raise AssertionError("empty embedding input should be rejected")


def test_index_run_removes_chunks_for_deleted_source_files(tmp_path):
    (tmp_path / "current.md").write_text("# Redis\n缓存击穿使用互斥锁。", encoding="utf-8")
    collection = MutableCollection({
        "stale-id": {
            "document": "已经删除的旧文档",
            "metadata": {"source_path": "deleted.md", "file_name": "deleted.md", "file_hash": "old-hash"},
        },
    })
    service = make_index_service(collection, tmp_path, semantic_key="")

    service.index_docs_directory()

    assert "stale-id" not in collection.records
    assert service._lexical_search("缓存击穿", 1)[0].source_path == "current.md"


def test_failed_semantic_file_update_rolls_back_new_chunk_ids(tmp_path):
    (tmp_path / "java.md").write_text("# Java\nvolatile 保证可见性。", encoding="utf-8")
    collection = MutableCollection(fail_after_write=True)
    service = make_index_service(collection, tmp_path, semantic_key="configured")

    service.index_docs_directory()

    assert collection.records == {}


def test_lexical_index_prefers_current_source_files_over_stale_vector_text(tmp_path):
    (tmp_path / "current.md").write_text("# RocketMQ\n消费幂等使用业务唯一键。", encoding="utf-8")
    collection = MutableCollection({
        "old": {"document": "旧向量中的无关内容", "metadata": {"source_path": "old.md", "file_hash": "old"}},
    })
    service = make_index_service(collection, tmp_path)

    hits = service._lexical_search("RocketMQ 消费幂等", 3)

    assert hits
    assert hits[0].source_path == "current.md"


def test_duplicate_in_process_index_run_is_skipped(tmp_path):
    service = make_index_service(MutableCollection(), tmp_path)
    service._index_lock = threading.Lock()
    service._index_lock.acquire()

    try:
        service.index_docs_directory()
    finally:
        service._index_lock.release()

    assert service._index_lock.locked() is False


def test_versioned_chunk_ids_keep_file_generations_disjoint():
    logical_id = "logical-chunk"

    first = RagService._versioned_chunk_id("java/readme.md", "file-hash-v1", logical_id)
    second = RagService._versioned_chunk_id("java/readme.md", "file-hash-v2", logical_id)
    same_content_other_source = RagService._versioned_chunk_id("mysql/readme.md", "file-hash-v1", logical_id)

    assert first != second
    assert first != same_content_other_source
    assert first == RagService._versioned_chunk_id("java/readme.md", "file-hash-v1", logical_id)
