from types import SimpleNamespace

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
        def create(self, *, input, model):
            captured.extend(input)
            assert model == "test-model"
            return SimpleNamespace(
                data=[SimpleNamespace(embedding=[float(index)]) for index, _ in enumerate(input)]
            )

    adapter = object.__new__(SiliconFlowEmbedding)
    adapter._client = SimpleNamespace(embeddings=FakeEmbeddingsApi())
    adapter._model = "test-model"
    adapter._batch_size = 2
    adapter._max_tokens = 32

    original = "中文技术内容" * 30
    vectors = adapter([original, "short"])

    assert len(vectors) == 2
    assert len(captured) == 2
    assert estimate_tokens(captured[0]) <= 32
    assert len(captured[0]) <= 32
    assert captured[1] == "short"
    assert len(captured[0]) < len(original)
