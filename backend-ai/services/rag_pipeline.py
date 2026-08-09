"""Pure, deterministic building blocks for the version-two RAG pipeline.

The module deliberately has no database or provider dependency.  It can be
unit-tested offline and reused by the production retriever and the evaluation
harness.  Dense retrieval remains the responsibility of ``RagService``;
lexical retrieval, rank fusion, reranking, and structured chunking live here.
"""

from __future__ import annotations

import hashlib
import html
import math
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Optional


_CJK_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]+")
_ASCII_TOKEN_RE = re.compile(r"[a-z0-9][a-z0-9_+#.:-]{1,}")
_HEADING_RE = re.compile(r"^(#{1,6})\s*(.+?)\s*$")
_FENCE_RE = re.compile(r"^\s*```([\w+-]*)\s*$")
_LEXICAL_STOPWORDS = {
    "什么", "如何", "为什么", "哪些", "怎样", "怎么", "问题", "一个",
    "这种", "这个", "那个", "可以", "通过", "进行", "需要", "以及",
    "分别", "主要", "原理", "作用", "时候",
}


@dataclass(frozen=True)
class ChunkDocument:
    chunk_id: str
    parent_id: str
    document_id: str
    document_title: str
    source_path: str
    section_path: str
    content_type: str
    language: str
    content: str
    indexed_text: str
    token_count: int
    chunk_index: int
    previous_chunk_id: str = ""
    next_chunk_id: str = ""

    def to_metadata(self, *, file_hash: str) -> dict[str, object]:
        return {
            "file_hash": file_hash,
            "file_name": self.document_title,
            "source_path": self.source_path,
            "document_id": self.document_id,
            "parent_id": self.parent_id,
            "section_path": self.section_path,
            "content_type": self.content_type,
            "language": self.language,
            "token_count": self.token_count,
            "chunk_index": self.chunk_index,
            "index_version": "v2",
            "previous_chunk_id": self.previous_chunk_id,
            "next_chunk_id": self.next_chunk_id,
        }


@dataclass
class RetrievalCandidate:
    chunk_id: str
    content: str
    metadata: dict[str, object] = field(default_factory=dict)
    dense_rank: Optional[int] = None
    lexical_rank: Optional[int] = None
    title_rank: Optional[int] = None
    dense_score: float = 0.0
    lexical_score: float = 0.0
    fusion_score: float = 0.0
    rerank_score: float = 0.0
    query_coverage: float = 0.0
    retrieval_channels: set[str] = field(default_factory=set)

    @property
    def parent_id(self) -> str:
        return str(self.metadata.get("parent_id") or self.chunk_id)

    @property
    def source_path(self) -> str:
        return str(
            self.metadata.get("source_path")
            or self.metadata.get("file_name")
            or "unknown"
        )

    @property
    def section_path(self) -> str:
        return str(self.metadata.get("section_path") or "")


@dataclass(frozen=True)
class RetrievalResult:
    query: str
    chunks: list[RetrievalCandidate]
    candidate_count: int
    dense_count: int
    lexical_count: int
    degraded: bool = False
    degraded_reason: str = ""


def estimate_tokens(value: str) -> int:
    """Cheap, deterministic token estimate for mixed Chinese/English text."""

    text = str(value or "")
    cjk_chars = sum(len(match.group(0)) for match in _CJK_RE.finditer(text))
    ascii_chars = sum(1 for char in text if ord(char) < 128 and not char.isspace())
    other_chars = max(0, len(text) - cjk_chars - ascii_chars)
    return max(1, cjk_chars + math.ceil(ascii_chars / 4) + math.ceil(other_chars / 2))


def normalize_document_text(value: str) -> str:
    """Remove presentation noise while preserving Markdown/code semantics."""

    text = html.unescape(str(value or ""))
    text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"</?(?:font|span|div|p)\b[^>]*>", "", text, flags=re.IGNORECASE)
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", " ", text)
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{4,}", "\n\n\n", text)
    return text.strip()


def lexical_tokens(value: str) -> list[str]:
    """Tokens suitable for exact identifiers and Chinese BM25 retrieval."""

    text = str(value or "").casefold()
    tokens = _ASCII_TOKEN_RE.findall(text)
    expanded = list(tokens)
    for token in tokens:
        # Preserve the exact identifier and add camel/symbol components.
        expanded.extend(part for part in re.split(r"[_#.:-]+", token) if len(part) > 1)
    for sequence in _CJK_RE.findall(text):
        if len(sequence) == 1:
            expanded.append(sequence)
            continue
        expanded.extend(sequence[index:index + 2] for index in range(len(sequence) - 1))
    return [token for token in expanded if token not in _LEXICAL_STOPWORDS]


def expand_query(value: str) -> str:
    """Add reviewed domain aliases without replacing the user's original text."""

    query = str(value or "").strip()
    rules = [
        (r"旧数组|复制新数组|读多写少", "CopyOnWriteArrayList 写时复制"),
        (r"快照读|历史记录.*能看见|历史版本.*可见", "MVCC ReadView undo log 版本链"),
        (r"热点.*(?:key|键).*失效|请求.*打到数据库", "缓存击穿 互斥锁 逻辑过期"),
        (r"主动.*(?:断开|关闭).*连接|旧四元组.*保留", "TCP TIME_WAIT 2MSL"),
        (r"重复投递|重复消息", "重复消费 幂等 业务唯一键"),
        (r"值.*A.*B.*A|版本戳", "CAS ABA AtomicStampedReference"),
    ]
    additions = [addition for pattern, addition in rules if re.search(pattern, query, re.IGNORECASE)]
    return " ".join([query, *additions]).strip()


def should_abstain_query(value: str) -> bool:
    """Block retrieval only when a user explicitly asks to reveal a secret.

    Mentions of passwords in defensive or educational questions are allowed.
    This keeps the guardrail focused on exfiltration instead of becoming a
    broad keyword filter.
    """

    query = str(value or "")
    secret = re.search(r"密码|口令|api\s*key|密钥|private\s*key|token", query, re.IGNORECASE)
    protected_target = re.search(r"生产|root|服务器|公网\s*ip|数据库", query, re.IGNORECASE)
    reveal_intent = re.search(
        r"给我|给出|告诉我|提供|显示|输出|导出|查出|泄露|发我|是什么|是多少|"
        r"show\s+me|tell\s+me|reveal|print|dump|export|what\s+is",
        query,
        re.IGNORECASE,
    )
    return bool(secret and protected_target and reveal_intent)


def _stable_id(*parts: str) -> str:
    payload = "\x1f".join(parts).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()[:32]


def _split_long_block(block: str, target_tokens: int, overlap_tokens: int) -> list[str]:
    if estimate_tokens(block) <= target_tokens:
        return [block.strip()]

    is_code = block.lstrip().startswith("```")
    if is_code:
        lines = block.splitlines()
        opening = lines[0] if lines else "```"
        closing = "```" if len(lines) > 1 and lines[-1].strip() == "```" else ""
        body = lines[1:-1] if closing else lines[1:]
        pieces: list[str] = []
        current: list[str] = []
        for line in body:
            candidate = "\n".join([opening, *current, line, "```"])
            if current and estimate_tokens(candidate) > target_tokens:
                pieces.append("\n".join([opening, *current, "```"]))
                current = current[-3:] if overlap_tokens else []
            current.append(line)
        if current:
            pieces.append("\n".join([opening, *current, "```"]))
        return [piece for piece in pieces if piece.strip()]

    sentences = [
        part.strip()
        for part in re.split(r"(?<=[。！？!?；;])\s*|\n+", block)
        if part.strip()
    ]
    if len(sentences) <= 1:
        # Last-resort character slicing uses the token ratio only inside one
        # already oversized paragraph; normal chunks still follow structure.
        chars_per_token = max(1.0, len(block) / estimate_tokens(block))
        width = max(80, int(target_tokens * chars_per_token))
        overlap = max(0, int(overlap_tokens * chars_per_token))
        step = max(1, width - overlap)
        return [block[start:start + width].strip() for start in range(0, len(block), step)]

    pieces: list[str] = []
    current: list[str] = []
    for sentence in sentences:
        candidate = "\n".join([*current, sentence])
        if current and estimate_tokens(candidate) > target_tokens:
            pieces.append("\n".join(current))
            current = current[-1:] if overlap_tokens else []
        current.append(sentence)
    if current:
        pieces.append("\n".join(current))
    return pieces


def structured_chunk_document(
    text: str,
    *,
    source_path: str,
    document_title: Optional[str] = None,
    target_tokens: int = 420,
    max_tokens: int = 650,
    overlap_tokens: int = 60,
) -> list[ChunkDocument]:
    """Split Markdown/technical text without crossing section boundaries."""

    normalized = normalize_document_text(text)
    title = document_title or Path(source_path).name
    document_id = _stable_id(source_path.casefold())
    heading_stack: list[str] = []
    section_blocks: list[tuple[str, str, str]] = []
    paragraph: list[str] = []
    code_lines: list[str] = []
    code_language = ""
    in_code = False

    def section_path() -> str:
        return " > ".join(heading_stack) if heading_stack else title

    def flush_paragraph() -> None:
        if paragraph:
            block = "\n".join(paragraph).strip()
            if block:
                content_type = "table" if "|" in block and re.search(r"\|\s*:?-{3,}", block) else "text"
                section_blocks.append((section_path(), content_type, block))
            paragraph.clear()

    for raw_line in normalized.splitlines():
        fence = _FENCE_RE.match(raw_line)
        if fence:
            if not in_code:
                flush_paragraph()
                in_code = True
                code_language = fence.group(1).strip().lower()
                code_lines = [raw_line]
            else:
                code_lines.append(raw_line)
                section_blocks.append((section_path(), f"code:{code_language}" if code_language else "code", "\n".join(code_lines)))
                code_lines = []
                code_language = ""
                in_code = False
            continue
        if in_code:
            code_lines.append(raw_line)
            continue

        heading = _HEADING_RE.match(raw_line)
        if heading:
            flush_paragraph()
            level = len(heading.group(1))
            heading_text = re.sub(r"[*_`]+", "", heading.group(2)).strip()
            heading_stack[:] = heading_stack[: level - 1]
            heading_stack.append(heading_text)
            continue
        if not raw_line.strip():
            flush_paragraph()
            continue
        paragraph.append(raw_line)

    flush_paragraph()
    if code_lines:
        code_lines.append("```")
        section_blocks.append((section_path(), f"code:{code_language}" if code_language else "code", "\n".join(code_lines)))

    raw_chunks: list[tuple[str, str, str]] = []
    current_section = ""
    current_type = "text"
    current_blocks: list[str] = []

    def flush_current() -> None:
        if not current_blocks:
            return
        content = "\n\n".join(current_blocks).strip()
        if content:
            raw_chunks.append((current_section, current_type, content))
        current_blocks.clear()

    for section, content_type, block in section_blocks:
        block_parts = _split_long_block(block, target_tokens, overlap_tokens)
        for part in block_parts:
            candidate = "\n\n".join([*current_blocks, part])
            section_changed = current_blocks and section != current_section
            type_changed = current_blocks and content_type.startswith("code") != current_type.startswith("code")
            if current_blocks and (
                section_changed
                or type_changed
                or estimate_tokens(candidate) > max_tokens
                or estimate_tokens("\n\n".join(current_blocks)) >= target_tokens
            ):
                flush_current()
            if not current_blocks:
                current_section = section
                current_type = content_type
            current_blocks.append(part)
    flush_current()

    chunks: list[ChunkDocument] = []
    for index, (section, content_type, content) in enumerate(raw_chunks):
        parent_id = _stable_id(document_id, section)
        language = content_type.split(":", 1)[1] if content_type.startswith("code:") else ""
        normalized_type = "code" if content_type.startswith("code") else content_type
        chunk_id = _stable_id(document_id, section, str(index), content)
        prefix = f"文档：{title}\n章节：{section}\n类型：{normalized_type}"
        indexed_text = f"{prefix}\n\n{content}".strip()
        chunks.append(
            ChunkDocument(
                chunk_id=chunk_id,
                parent_id=parent_id,
                document_id=document_id,
                document_title=title,
                source_path=source_path,
                section_path=section,
                content_type=normalized_type,
                language=language,
                content=content,
                indexed_text=indexed_text,
                token_count=estimate_tokens(indexed_text),
                chunk_index=index,
            )
        )

    linked: list[ChunkDocument] = []
    for index, chunk in enumerate(chunks):
        linked.append(
            ChunkDocument(
                **{
                    **chunk.__dict__,
                    "previous_chunk_id": chunks[index - 1].chunk_id if index > 0 else "",
                    "next_chunk_id": chunks[index + 1].chunk_id if index + 1 < len(chunks) else "",
                }
            )
        )
    return linked


class BM25Index:
    """Small in-memory BM25 index appropriate for the current 10k corpus."""

    def __init__(self, candidates: Iterable[RetrievalCandidate], *, k1: float = 1.5, b: float = 0.75):
        self._candidates = list(candidates)
        self._k1 = k1
        self._b = b
        self._term_frequencies: list[Counter[str]] = []
        self._document_lengths: list[int] = []
        self._document_frequency: Counter[str] = Counter()
        self._title_tokens: list[set[str]] = []
        for candidate in self._candidates:
            body_tokens = lexical_tokens(candidate.content)
            title_tokens = lexical_tokens(candidate.section_path)
            frequencies = Counter([*body_tokens, *title_tokens, *title_tokens])
            self._term_frequencies.append(frequencies)
            self._document_lengths.append(max(1, sum(frequencies.values())))
            self._document_frequency.update(frequencies.keys())
            self._title_tokens.append(set(title_tokens))
        self._average_length = (
            sum(self._document_lengths) / len(self._document_lengths)
            if self._document_lengths
            else 1.0
        )

    def search(self, query: str, top_k: int) -> list[RetrievalCandidate]:
        query_terms = Counter(lexical_tokens(query))
        if not query_terms or not self._candidates:
            return []
        document_count = len(self._candidates)
        scored: list[tuple[float, int, float]] = []
        for index, frequencies in enumerate(self._term_frequencies):
            score = 0.0
            title_matches = 0
            length = self._document_lengths[index]
            for term, query_frequency in query_terms.items():
                frequency = frequencies.get(term, 0)
                if not frequency:
                    continue
                document_frequency = self._document_frequency.get(term, 0)
                inverse_document_frequency = math.log(
                    1.0 + (document_count - document_frequency + 0.5) / (document_frequency + 0.5)
                )
                denominator = frequency + self._k1 * (
                    1.0 - self._b + self._b * length / self._average_length
                )
                score += query_frequency * inverse_document_frequency * (
                    frequency * (self._k1 + 1.0) / denominator
                )
                if term in self._title_tokens[index]:
                    title_matches += 1
            if score > 0:
                scored.append((score, index, title_matches / max(1, len(query_terms))))
        scored.sort(key=lambda item: (-item[0], -item[2], item[1]))
        results: list[RetrievalCandidate] = []
        for rank, (score, index, title_coverage) in enumerate(scored[:top_k], start=1):
            original = self._candidates[index]
            candidate = RetrievalCandidate(
                chunk_id=original.chunk_id,
                content=original.content,
                metadata=dict(original.metadata),
                lexical_rank=rank,
                title_rank=rank if title_coverage > 0 else None,
                lexical_score=score,
                retrieval_channels={"bm25", *( ["title"] if title_coverage > 0 else [] )},
            )
            results.append(candidate)
        return results


def reciprocal_rank_fusion(
    dense: Iterable[RetrievalCandidate],
    lexical: Iterable[RetrievalCandidate],
    *,
    rank_constant: int = 60,
    dense_weight: float = 1.0,
    lexical_weight: float = 1.0,
    title_weight: float = 0.25,
) -> list[RetrievalCandidate]:
    merged: dict[str, RetrievalCandidate] = {}

    def accept(candidate: RetrievalCandidate) -> RetrievalCandidate:
        existing = merged.get(candidate.chunk_id)
        if existing is None:
            existing = RetrievalCandidate(
                chunk_id=candidate.chunk_id,
                content=candidate.content,
                metadata=dict(candidate.metadata),
            )
            merged[candidate.chunk_id] = existing
        existing.retrieval_channels.update(candidate.retrieval_channels)
        return existing

    for rank, candidate in enumerate(dense, start=1):
        item = accept(candidate)
        item.retrieval_channels.add("dense")
        item.dense_rank = candidate.dense_rank or rank
        item.dense_score = candidate.dense_score
        item.fusion_score += dense_weight / (rank_constant + item.dense_rank)
    for rank, candidate in enumerate(lexical, start=1):
        item = accept(candidate)
        item.retrieval_channels.add("bm25")
        item.lexical_rank = candidate.lexical_rank or rank
        item.lexical_score = candidate.lexical_score
        item.title_rank = candidate.title_rank
        item.fusion_score += lexical_weight / (rank_constant + item.lexical_rank)
        if item.title_rank is not None:
            item.fusion_score += title_weight / (rank_constant + item.title_rank)

    return sorted(merged.values(), key=lambda item: (-item.fusion_score, item.chunk_id))


def rerank_candidates(query: str, candidates: Iterable[RetrievalCandidate]) -> list[RetrievalCandidate]:
    """Deterministic cross-feature reranker with no additional model call."""

    items = list(candidates)
    if not items:
        return []
    query_terms = set(lexical_tokens(query))
    max_fusion = max(item.fusion_score for item in items) or 1.0
    max_lexical = max((item.lexical_score for item in items), default=0.0) or 1.0
    normalized_query = re.sub(r"\s+", "", query.casefold())
    for item in items:
        content_terms = set(lexical_tokens(item.content))
        section_terms = set(lexical_tokens(item.section_path))
        coverage = len(query_terms & content_terms) / max(1, len(query_terms))
        item.query_coverage = coverage
        section_coverage = len(query_terms & section_terms) / max(1, len(query_terms))
        exact_phrase = 1.0 if normalized_query and normalized_query in re.sub(r"\s+", "", item.content.casefold()) else 0.0
        dense_signal = 1.0 / item.dense_rank if item.dense_rank else 0.0
        item.rerank_score = (
            0.38 * (item.fusion_score / max_fusion)
            + 0.27 * coverage
            + 0.12 * section_coverage
            + 0.10 * exact_phrase
            + 0.08 * (item.lexical_score / max_lexical)
            + 0.05 * dense_signal
        )
    return sorted(items, key=lambda item: (-item.rerank_score, -item.fusion_score, item.chunk_id))


def select_diverse_candidates(
    candidates: Iterable[RetrievalCandidate],
    *,
    top_k: int,
    minimum_score: float = 0.08,
    minimum_query_coverage: float = 0.25,
    max_per_parent: int = 2,
) -> list[RetrievalCandidate]:
    selected: list[RetrievalCandidate] = []
    selected_tokens: list[set[str]] = []
    parent_counts: defaultdict[str, int] = defaultdict(int)
    for candidate in candidates:
        if candidate.rerank_score < minimum_score:
            continue
        if (
            candidate.query_coverage < minimum_query_coverage
            and not (candidate.dense_rank is not None and candidate.dense_rank <= 3)
        ):
            continue
        if parent_counts[candidate.parent_id] >= max_per_parent:
            continue
        tokens = set(lexical_tokens(candidate.content))
        duplicate = False
        for previous in selected_tokens:
            union = tokens | previous
            if union and len(tokens & previous) / len(union) >= 0.88:
                duplicate = True
                break
        if duplicate:
            continue
        selected.append(candidate)
        selected_tokens.append(tokens)
        parent_counts[candidate.parent_id] += 1
        if len(selected) >= top_k:
            break
    return selected
