"""
RAG 服务模块。
使用 SiliconFlow 嵌入模型 + ChromaDB 实现文档检索增强生成。
"""

import hashlib
import json
import logging
import os
import time
from pathlib import Path
from typing import Optional

import chromadb
from chromadb.api.types import EmbeddingFunction, Documents, Embeddings
from openai import OpenAI

from config import get_settings
from services.rag_pipeline import (
    BM25Index,
    RetrievalCandidate,
    RetrievalResult,
    estimate_tokens,
    expand_query,
    lexical_tokens,
    reciprocal_rank_fusion,
    rerank_candidates,
    select_diverse_candidates,
    should_abstain_query,
    structured_chunk_document,
)

logger = logging.getLogger(__name__)

SEMANTIC_RETRY_COOLDOWN_SECONDS = 300
MAX_LEXICAL_DOCUMENTS = 50_000
MAX_LEXICAL_DOCUMENT_CHARS = 4_000


class SiliconFlowEmbedding(EmbeddingFunction):
    """调用 SiliconFlow /v1/embeddings API 的嵌入函数。"""

    def __init__(
        self,
        api_key: str,
        base_url: str,
        model: str,
        batch_size: int = 64,
        max_tokens: int = 320,
    ):
        self._client = OpenAI(
            api_key=api_key,
            base_url=base_url,
            max_retries=0,
            timeout=15.0,
        )
        self._model = model
        self._batch_size = max(int(batch_size), 1)
        self._max_tokens = max(int(max_tokens), 32)
        self._max_characters = self._max_tokens

    def _prepare_input(self, value: str) -> str:
        """Keep provider inputs within the embedding model's token budget.

        Chroma still stores the original document. Only the representation sent
        to the embedding API is shortened, so lexical retrieval and citations
        retain the complete chunk.
        """
        text = str(value or "")
        max_characters = max(
            int(getattr(self, "_max_characters", self._max_tokens)),
            32,
        )
        if len(text) > max_characters:
            text = text[:max_characters]
        if estimate_tokens(text) <= self._max_tokens:
            return text

        low, high = 1, len(text)
        while low < high:
            middle = (low + high + 1) // 2
            if estimate_tokens(text[:middle]) <= self._max_tokens:
                low = middle
            else:
                high = middle - 1
        return text[:low].rstrip()

    def __call__(self, input: Documents) -> Embeddings:
        if not input:
            return []

        embeddings: Embeddings = []
        for start in range(0, len(input), self._batch_size):
            batch = [
                self._prepare_input(document)
                for document in input[start:start + self._batch_size]
            ]
            response = self._client.embeddings.create(input=batch, model=self._model)
            embeddings.extend([item.embedding for item in response.data])
        return embeddings


class RagService:
    """RAG 核心服务：文档索引与检索。"""

    def __init__(self):
        settings = get_settings()
        self._settings = settings
        self._embedding_fn = (
            SiliconFlowEmbedding(
                api_key=settings.siliconflow_api_key,
                base_url=settings.siliconflow_base_url,
                model=settings.embedding_model,
                batch_size=settings.embed_batch_size,
                max_tokens=getattr(settings, "embed_max_tokens", 320),
            )
            if settings.siliconflow_api_key.strip()
            else None
        )
        persist_dir = os.path.abspath(settings.chroma_persist_dir)
        self._client = chromadb.PersistentClient(path=persist_dir)
        self._collection = self._client.get_or_create_collection(
            name=settings.rag_collection_name,
            embedding_function=self._embedding_fn,
        )
        self._docs_dir = os.path.abspath(settings.docs_dir)
        self._chunk_size = settings.chunk_size
        self._chunk_overlap = settings.chunk_overlap
        self._top_k = settings.rag_top_k
        self._embed_batch_size = settings.embed_batch_size
        self._embed_max_chars_per_batch = settings.embed_max_chars_per_batch
        self._semantic_retry_after = 0.0
        self._lexical_documents_cache: Optional[list[str]] = None
        self._bm25_index_cache: Optional[BM25Index] = None

    # ------------------------------------------------------------------
    # 文件哈希与判重
    # ------------------------------------------------------------------

    @staticmethod
    def _compute_file_hash(path: str) -> str:
        """计算文件 SHA256 哈希。"""
        h = hashlib.sha256()
        with open(path, "rb") as f:
            for block in iter(lambda: f.read(8192), b""):
                h.update(block)
        return h.hexdigest()

    def _get_indexed_hashes(self) -> dict[str, set[str]]:
        """
        返回 {file_hash: {id1, id2, ...}} 映射，
        用于判重和删除旧数据。
        """
        result: dict[str, set[str]] = {}
        total = self._collection.count()
        if total == 0:
            return result
        data = self._collection.get(include=["metadatas"])
        for doc_id, meta in zip(data["ids"], data["metadatas"]):
            fh = meta.get("file_hash", "")
            result.setdefault(fh, set()).add(doc_id)
        return result
    # ------------------------------------------------------------------
    # 文本分块
    # ------------------------------------------------------------------

    @staticmethod
    def _load_document_text(path: str) -> str:
        extension = os.path.splitext(path)[1].lower()
        if extension == ".pdf":
            try:
                from pypdf import PdfReader
            except ImportError as exc:  # pragma: no cover - deployment guard
                raise RuntimeError("pypdf is required to index PDF documents") from exc
            reader = PdfReader(path)
            return "\n\n".join(page.extract_text() or "" for page in reader.pages)
        with open(path, "r", encoding="utf-8", errors="ignore") as file:
            return file.read()

    def _load_structured_chunks(self, path: str):
        source_path = os.path.relpath(path, self._docs_dir).replace("\\", "/")
        return structured_chunk_document(
            self._load_document_text(path),
            source_path=source_path,
            document_title=os.path.basename(path),
            target_tokens=int(getattr(self._settings, "rag_chunk_target_tokens", 420)),
            max_tokens=int(getattr(self._settings, "rag_chunk_max_tokens", 650)),
            overlap_tokens=int(getattr(self._settings, "rag_chunk_overlap_tokens", 60)),
        )

    def _load_and_split(self, path: str) -> list[str]:
        """Compatibility helper returning the new contextualized chunks."""
        return [chunk.indexed_text for chunk in self._load_structured_chunks(path)]

    # ------------------------------------------------------------------
    # 索引
    # ------------------------------------------------------------------

    _SUPPORTED_EXTS = {".txt", ".md", ".pdf"}

    def index_docs_directory(self) -> None:
        """扫描 docs/ 目录，跳过已索引文件，嵌入新文件。"""
        if not os.path.isdir(self._docs_dir):
            logger.warning(f"文档目录不存在，跳过索引: {self._docs_dir}")
            return

        indexed = self._get_indexed_hashes()

        files = sorted(
            str(path)
            for path in Path(self._docs_dir).rglob("*")
            if path.is_file() and path.suffix.lower() in self._SUPPORTED_EXTS
        )
        new_count = 0
        semantic_index_available = bool(
            str(getattr(self._settings, "siliconflow_api_key", "") or "").strip()
        )
        for filepath in files:
            if not semantic_index_available:
                break
            file_hash = self._compute_file_hash(filepath)
            file_name = os.path.basename(filepath)

            # Two-phase per-file update: write the new version before deleting
            # old ids so a failed embedding request does not erase good data.
            old_hashes_to_remove = [
                h for h, ids in indexed.items()
                if any(True for _ in ids)  # 存在旧记录
                and h != file_hash
                and self._file_name_in_ids(file_name, ids)
            ]
            structured_chunks = self._load_structured_chunks(filepath)
            chunks = [chunk.indexed_text for chunk in structured_chunks]
            if not chunks:
                continue

            ids = [chunk.chunk_id for chunk in structured_chunks]
            expected_ids = set(ids)
            indexed_ids = set(indexed.get(file_hash, set()))
            if expected_ids.issubset(indexed_ids):
                logger.info(f"文件未变化且索引完整，跳过: {file_name}")
                continue
            if indexed_ids:
                logger.info(
                    "检测到不完整或旧版分块索引，自动续建: %s (%s/%s)",
                    file_name,
                    len(indexed_ids & expected_ids),
                    len(expected_ids),
                )

            metadatas = [
                chunk.to_metadata(file_hash=file_hash)
                for chunk in structured_chunks
            ]
            try:
                added = self._add_in_batches(chunks, ids, metadatas)
            except Exception as exc:
                semantic_index_available = False
                self._semantic_retry_after = time.monotonic() + SEMANTIC_RETRY_COOLDOWN_SECONDS
                logger.warning(
                    "Semantic indexing unavailable (%s); keeping the previous vector index",
                    type(exc).__name__,
                )
                break
            stale_same_hash_ids = list(indexed_ids - expected_ids)
            if stale_same_hash_ids:
                self._collection.delete(ids=stale_same_hash_ids)
                logger.info(
                    "删除同文件旧版分块: %s (%s chunks)",
                    file_name,
                    len(stale_same_hash_ids),
                )
            for old_hash in old_hashes_to_remove:
                old_ids = list(indexed[old_hash] - set(ids))
                if old_ids:
                    self._collection.delete(ids=old_ids)
                    logger.info(f"删除旧索引: {file_name} ({len(old_ids)} chunks)")
            if added:
                self._lexical_documents_cache = None
                self._bm25_index_cache = None
            new_count += added
            logger.info(f"已索引: {file_name} ({added} chunks)")

        # Build the deterministic lexical index during startup.  Requests then
        # pay only query time, while a provider outage still leaves RAG usable.
        if self._bm25_index_cache is None:
            self._bm25_index_cache = BM25Index(self._load_retrieval_candidates())

        logger.info(
            f"索引完成: 扫描 {len(files)} 个文件, "
            f"新增 {new_count} 个 chunks, "
            f"总计 {self._collection.count()} 个 chunks"
        )

    def _add_in_batches(self, chunks: list[str], ids: list[str], metadatas: list[dict]) -> int:
        total_added = 0
        start = 0
        while start < len(chunks):
            batch_size = self._embed_batch_size
            batch_chars = 0
            end = start
            while end < len(chunks) and (end - start) < batch_size:
                next_chars = len(chunks[end])
                if end > start and (batch_chars + next_chars) > self._embed_max_chars_per_batch:
                    break
                batch_chars += next_chars
                end += 1

            if end == start:
                end = min(start + 1, len(chunks))

            try:
                write = getattr(self._collection, "upsert", self._collection.add)
                write(
                    documents=chunks[start:end],
                    ids=ids[start:end],
                    metadatas=metadatas[start:end],
                )
                total_added += (end - start)
                start = end
            except Exception:
                if (end - start) <= 1:
                    raise
                mid = start + (end - start) // 2
                total_added += self._add_in_batches(
                    chunks[start:mid], ids[start:mid], metadatas[start:mid]
                )
                total_added += self._add_in_batches(
                    chunks[mid:end], ids[mid:end], metadatas[mid:end]
                )
                start = end

        return total_added

    def _file_name_in_ids(self, file_name: str, ids: set[str]) -> bool:
        """检查给定 ids 对应的 metadata 中是否包含指定文件名。"""
        if not ids:
            return False
        data = self._collection.get(ids=list(ids), include=["metadatas"])
        return any(m.get("file_name") == file_name for m in data["metadatas"])
    # ------------------------------------------------------------------
    # 检索
    # ------------------------------------------------------------------

    def retrieve(self, query: str, top_k: Optional[int] = None) -> list[str]:
        """Hybrid retrieval with a backward-compatible list-of-text result."""

        return [candidate.content for candidate in self.retrieve_detailed(query, top_k).chunks]

    def retrieve_detailed(
        self,
        query: str,
        top_k: Optional[int] = None,
    ) -> RetrievalResult:
        """Run dense and BM25 recall, RRF fusion, reranking, and deduplication."""

        normalized_query = str(query or "").strip()
        if not normalized_query:
            return RetrievalResult(normalized_query, [], 0, 0, 0)
        if should_abstain_query(normalized_query):
            return RetrievalResult(
                normalized_query,
                [],
                0,
                0,
                0,
                degraded=False,
                degraded_reason="policy_abstention",
            )
        expanded_query = expand_query(normalized_query)
        k = max(1, min(int(top_k or self._top_k), 20))
        dense_candidates: list[RetrievalCandidate] = []
        lexical_candidates: list[RetrievalCandidate] = []
        degraded = False
        degraded_reason = ""
        dense_limit = max(k, int(getattr(self._settings, "rag_dense_candidates", 30)))
        lexical_limit = max(k, int(getattr(self._settings, "rag_lexical_candidates", 30)))
        fusion_limit = max(k, int(getattr(self._settings, "rag_fusion_candidates", 40)))
        has_semantic_credentials = bool(str(getattr(self._settings, "siliconflow_api_key", "") or "").strip())
        try:
            collection_count = self._collection.count()
        except Exception as exc:
            collection_count = 0
            degraded = True
            degraded_reason = "vector_store_unavailable"
            logger.warning(
                "Could not inspect persisted RAG collection (%s)",
                type(exc).__name__,
            )
        if (
            has_semantic_credentials
            and collection_count > 0
            and time.monotonic() >= self._semantic_retry_after
        ):
            try:
                results = self._collection.query(
                    query_texts=[normalized_query],
                    n_results=min(dense_limit, collection_count),
                    include=["documents", "metadatas", "distances"],
                )
                documents = (results.get("documents") or [[]])[0] or []
                ids = (results.get("ids") or [[]])[0] or []
                metadatas = (results.get("metadatas") or [[]])[0] or []
                distances = (results.get("distances") or [[]])[0] or []
                for index, document in enumerate(documents):
                    distance = float(distances[index]) if index < len(distances) and distances[index] is not None else float(index)
                    dense_candidates.append(
                        RetrievalCandidate(
                            chunk_id=str(ids[index]) if index < len(ids) else hashlib.sha256(str(document).encode()).hexdigest()[:32],
                            content=str(document),
                            metadata=dict(metadatas[index] or {}) if index < len(metadatas) else {},
                            dense_rank=index + 1,
                            dense_score=1.0 / (1.0 + max(0.0, distance)),
                            retrieval_channels={"dense"},
                        )
                    )
            except Exception as exc:
                self._semantic_retry_after = (
                    time.monotonic() + SEMANTIC_RETRY_COOLDOWN_SECONDS
                )
                degraded = True
                degraded_reason = "semantic_retrieval_unavailable"
                logger.warning(
                    "Semantic RAG unavailable (%s); using local lexical retrieval",
                    type(exc).__name__,
                )
        elif has_semantic_credentials:
            degraded = True
            degraded_reason = (
                "semantic_retrieval_cooldown"
                if time.monotonic() < self._semantic_retry_after
                else "semantic_index_empty"
            )
        else:
            degraded = True
            degraded_reason = "semantic_credentials_missing"

        lexical_candidates = self._lexical_search(expanded_query, lexical_limit)
        fused = reciprocal_rank_fusion(dense_candidates, lexical_candidates)[:fusion_limit]
        reranked = rerank_candidates(expanded_query, fused)
        selected = select_diverse_candidates(
            reranked,
            top_k=k,
            minimum_score=float(getattr(self._settings, "rag_min_rerank_score", 0.08)),
            minimum_query_coverage=float(getattr(self._settings, "rag_min_query_coverage", 0.25)),
            max_per_parent=int(getattr(self._settings, "rag_max_chunks_per_parent", 2)),
        )
        return RetrievalResult(
            query=normalized_query,
            chunks=selected,
            candidate_count=len(fused),
            dense_count=len(dense_candidates),
            lexical_count=len(lexical_candidates),
            degraded=degraded,
            degraded_reason=degraded_reason,
        )

    def _lexical_retrieve(self, query: str, top_k: int) -> list[str]:
        return [candidate.content for candidate in self._lexical_search(query, top_k)]

    def _lexical_search(self, query: str, top_k: int) -> list[RetrievalCandidate]:
        if getattr(self, "_bm25_index_cache", None) is None:
            candidates = self._load_retrieval_candidates()
            self._bm25_index_cache = BM25Index(candidates)
        return self._bm25_index_cache.search(query, top_k)

    def _load_retrieval_candidates(self) -> list[RetrievalCandidate]:
        candidates: list[RetrievalCandidate] = []
        try:
            if self._collection.count() > 0:
                stored = self._collection.get(
                    include=["documents", "metadatas"],
                    limit=MAX_LEXICAL_DOCUMENTS,
                )
                ids = stored.get("ids") or []
                documents = stored.get("documents") or []
                metadatas = stored.get("metadatas") or []
                for index, document in enumerate(documents):
                    candidates.append(
                        RetrievalCandidate(
                            chunk_id=str(ids[index]) if index < len(ids) else hashlib.sha256(str(document).encode()).hexdigest()[:32],
                            content=str(document)[:MAX_LEXICAL_DOCUMENT_CHARS],
                            metadata=dict(metadatas[index] or {}) if index < len(metadatas) else {},
                        )
                    )
        except Exception as exc:
            logger.warning("Could not build BM25 from vector store (%s)", type(exc).__name__)

        if not candidates and os.path.isdir(self._docs_dir):
            manifest = self._current_source_manifest()
            candidates = self._load_local_lexical_cache(manifest)
            if candidates:
                return candidates
            for path in sorted(Path(self._docs_dir).rglob("*")):
                if not path.is_file() or path.suffix.lower() not in {".md", ".txt"}:
                    continue
                try:
                    for chunk in self._load_structured_chunks(str(path)):
                        candidates.append(
                            RetrievalCandidate(
                                chunk_id=chunk.chunk_id,
                                content=chunk.indexed_text[:MAX_LEXICAL_DOCUMENT_CHARS],
                                metadata=chunk.to_metadata(file_hash=self._compute_file_hash(str(path))),
                            )
                        )
                        if len(candidates) >= MAX_LEXICAL_DOCUMENTS:
                            break
                except OSError as exc:
                    logger.warning("Could not read lexical document %s (%s)", path.name, type(exc).__name__)
                if len(candidates) >= MAX_LEXICAL_DOCUMENTS:
                    break
            self._write_local_lexical_cache(manifest, candidates)
        return candidates

    def _local_lexical_cache_path(self) -> Path:
        configured = getattr(self._settings, "chroma_persist_dir", "")
        persist_dir = (
            Path(os.path.abspath(configured))
            if configured
            else Path(self._docs_dir) / ".rag_cache"
        )
        return persist_dir / "rag_v2_lexical_cache.json"

    def _current_source_manifest(self) -> list[dict[str, str]]:
        manifest: list[dict[str, str]] = []
        for path in sorted(Path(self._docs_dir).rglob("*")):
            if not path.is_file() or path.suffix.lower() not in self._SUPPORTED_EXTS:
                continue
            manifest.append(
                {
                    "source_path": os.path.relpath(path, self._docs_dir).replace("\\", "/"),
                    "file_hash": self._compute_file_hash(str(path)),
                }
            )
        return manifest

    def _load_local_lexical_cache(
        self,
        manifest: list[dict[str, str]],
    ) -> list[RetrievalCandidate]:
        path = self._local_lexical_cache_path()
        if not path.exists():
            return []
        try:
            with path.open("r", encoding="utf-8") as file:
                payload = json.load(file)
            if payload.get("schema_version") != 1 or payload.get("manifest") != manifest:
                return []
            return [
                RetrievalCandidate(
                    chunk_id=item["chunk_id"],
                    content=item["content"],
                    metadata=item.get("metadata") or {},
                )
                for item in payload.get("candidates") or []
                if item.get("chunk_id") and item.get("content")
            ][:MAX_LEXICAL_DOCUMENTS]
        except (OSError, ValueError, TypeError, json.JSONDecodeError) as exc:
            logger.warning("Could not load lexical cache (%s)", type(exc).__name__)
            return []

    def _write_local_lexical_cache(
        self,
        manifest: list[dict[str, str]],
        candidates: list[RetrievalCandidate],
    ) -> None:
        path = self._local_lexical_cache_path()
        temporary_path = path.with_suffix(path.suffix + ".tmp")
        payload = {
            "schema_version": 1,
            "manifest": manifest,
            "candidates": [
                {
                    "chunk_id": candidate.chunk_id,
                    "content": candidate.content,
                    "metadata": candidate.metadata,
                }
                for candidate in candidates
            ],
        }
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            with temporary_path.open("w", encoding="utf-8") as file:
                json.dump(payload, file, ensure_ascii=False)
            os.replace(temporary_path, path)
        except OSError as exc:
            logger.warning("Could not persist lexical cache (%s)", type(exc).__name__)
            try:
                temporary_path.unlink(missing_ok=True)
            except OSError:
                pass

    def _load_lexical_documents(self) -> list[str]:
        if self._lexical_documents_cache is not None:
            return self._lexical_documents_cache

        candidates = [candidate.content for candidate in self._load_retrieval_candidates()]

        deduplicated: list[str] = []
        seen = set()
        for candidate in candidates:
            text = str(candidate or "").strip()[:MAX_LEXICAL_DOCUMENT_CHARS]
            if not text:
                continue
            fingerprint = hashlib.sha256(text.encode("utf-8")).digest()
            if fingerprint in seen:
                continue
            seen.add(fingerprint)
            deduplicated.append(text)
            if len(deduplicated) >= MAX_LEXICAL_DOCUMENTS:
                break

        self._lexical_documents_cache = deduplicated
        return deduplicated

    @staticmethod
    def _lexical_tokens(value: str) -> list[str]:
        return lexical_tokens(value)


# ------------------------------------------------------------------
# 单例
# ------------------------------------------------------------------

_rag_service: Optional[RagService] = None


def get_rag_service() -> RagService:
    """获取或创建 RAG 服务单例。"""
    global _rag_service
    if _rag_service is None:
        _rag_service = RagService()
    return _rag_service
