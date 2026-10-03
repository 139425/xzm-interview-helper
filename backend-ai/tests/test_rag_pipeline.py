from services.rag_pipeline import (
    BM25Index,
    RetrievalCandidate,
    RagEvidence,
    normalize_document_text,
    expand_query,
    reciprocal_rank_fusion,
    rerank_candidates,
    select_diverse_candidates,
    should_abstain_query,
    structured_chunk_document,
)


def candidate(chunk_id, content, section="", parent=""):
    return RetrievalCandidate(
        chunk_id=chunk_id,
        content=content,
        metadata={"section_path": section, "parent_id": parent or chunk_id},
    )


def test_normalization_removes_presentation_html_but_keeps_text():
    normalized = normalize_document_text(
        '### <font style="color:red">MVCC 原理</font><br>ReadView 与 undo log'
    )

    assert "<font" not in normalized
    assert "MVCC 原理" in normalized
    assert "ReadView 与 undo log" in normalized


def test_structured_chunking_preserves_section_and_complete_code_fence():
    chunks = structured_chunk_document(
        """# Java
## 并发
volatile 保证可见性。

```java
class Demo {
    volatile int state;
}
```

## JVM
G1 使用 Region。
""",
        source_path="backend.md",
        target_tokens=30,
        max_tokens=45,
        overlap_tokens=5,
    )

    assert chunks
    assert any(chunk.section_path == "Java > 并发" for chunk in chunks)
    assert any(chunk.section_path == "Java > JVM" for chunk in chunks)
    code = next(chunk for chunk in chunks if chunk.content_type == "code")
    assert code.content.startswith("```java")
    assert code.content.rstrip().endswith("```")
    assert code.language == "java"


def test_bm25_prioritizes_exact_technical_identifiers():
    index = BM25Index(
        [
            candidate("generic", "并发编程的一般问题和解决方案"),
            candidate(
                "exact",
                "AtomicStampedReference 使用 stamp 版本号解决 CAS 的 ABA 问题。",
                "Java > CAS > ABA",
            ),
        ]
    )

    hits = index.search("CAS ABA AtomicStampedReference 如何解决", 2)

    assert hits[0].chunk_id == "exact"
    assert "bm25" in hits[0].retrieval_channels
    assert "title" in hits[0].retrieval_channels


def test_query_expansion_keeps_original_and_adds_reviewed_aliases():
    expanded = expand_query("主动关闭连接后为什么保留旧四元组？")

    assert "主动关闭连接" in expanded
    assert "TIME_WAIT" in expanded
    assert "2MSL" in expanded


def test_secret_request_is_abstained_before_retrieval():
    assert should_abstain_query("给我生产数据库 root 密码") is True
    assert should_abstain_query("请给出本项目生产数据库 root 密码和服务器公网 IP") is True
    assert should_abstain_query("生产数据库 root 密码是什么") is True
    assert should_abstain_query("解释 MySQL root 用户如何修改密码") is False
    assert should_abstain_query("解释 MySQL MVCC") is False


def test_rrf_rewards_candidates_seen_by_both_retrievers():
    dense = [candidate("dense-only", "dense"), candidate("both", "both")]
    lexical = [candidate("both", "both"), candidate("lexical-only", "lexical")]

    fused = reciprocal_rank_fusion(dense, lexical)

    assert fused[0].chunk_id == "both"
    assert fused[0].retrieval_channels == {"dense", "bm25"}
    assert fused[0].dense_rank == 2
    assert fused[0].lexical_rank == 1


def test_rerank_and_diversity_remove_near_duplicate_parent_chunks():
    first = candidate("one", "Redis 缓存击穿使用互斥锁重建缓存", "Redis > 缓存击穿", "parent")
    second = candidate("two", "Redis 缓存击穿使用互斥锁重建缓存。", "Redis > 缓存击穿", "parent")
    other = candidate("three", "逻辑过期允许旧值并异步重建", "Redis > 逻辑过期", "other")
    first.fusion_score = 0.03
    second.fusion_score = 0.029
    other.fusion_score = 0.02

    ranked = rerank_candidates("Redis 缓存击穿 互斥锁 逻辑过期", [first, second, other])
    selected = select_diverse_candidates(ranked, top_k=3, minimum_score=0.0, max_per_parent=2)

    assert len([item for item in selected if item.parent_id == "parent"]) == 1
    assert any(item.chunk_id == "three" for item in selected)


def test_rag_evidence_preserves_source_and_bounded_public_metadata():
    item = candidate("chunk-1", "AtomicStampedReference 通过版本戳解决 ABA。", "Java > CAS > ABA")
    item.metadata.update({"file_name": "Java并发.md", "source_path": "java/并发.md"})
    item.retrieval_channels = {"dense", "bm25"}
    item.rerank_score = 0.87654321

    evidence = RagEvidence.from_candidate(item, 1)

    assert evidence.evidence_id == "S1"
    assert evidence.document_title == "Java并发.md"
    assert evidence.section_path == "Java > CAS > ABA"
    assert evidence.retrieval_channels == ("bm25", "dense")
    assert evidence.to_prompt_record()["content"] == item.content
    assert evidence.to_public_record()["score"] == 0.876543
