package com.xzm.xzm_interview_helper.career;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PersonalKnowledgeRepository {
    public static final int MAX_DOCUMENTS_PER_USER = 30;
    public static final int MAX_TOTAL_CHARACTERS_PER_USER = 600_000;

    private final JdbcTemplate jdbcTemplate;

    public record Document(
            long id,
            String title,
            String sourceType,
            String originalFilename,
            int contentChars,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record DocumentContent(long id, String title, String sourceType, String content) {
    }

    public List<Document> findAll(int userId) {
        return jdbcTemplate.query("""
                        SELECT id, title, source_type, original_filename, content_chars, created_at, updated_at
                        FROM personal_knowledge_document WHERE user_id = ? ORDER BY updated_at DESC, id DESC
                        """,
                (rs, rowNum) -> new Document(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("original_filename"),
                        rs.getInt("content_chars"),
                        timestamp(rs.getTimestamp("created_at")),
                        timestamp(rs.getTimestamp("updated_at"))
                ),
                userId
        );
    }

    public List<DocumentContent> loadContents(int userId) {
        return jdbcTemplate.query(
                "SELECT id, title, source_type, content FROM personal_knowledge_document WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?",
                (rs, rowNum) -> new DocumentContent(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("content")
                ),
                userId,
                MAX_DOCUMENTS_PER_USER
        );
    }

    public Document create(int userId, String title, String sourceType, String filename, String content) {
        enforceQuota(userId, content.length());
        jdbcTemplate.update("""
                        INSERT INTO personal_knowledge_document (
                            user_id, title, source_type, original_filename, content, content_chars
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                userId,
                clip(title, 255),
                normalizeSourceType(sourceType),
                clip(filename, 255),
                content,
                content.length()
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return findMetadata(userId, id == null ? 0 : id);
    }

    public void delete(int userId, long id) {
        if (jdbcTemplate.update(
                "DELETE FROM personal_knowledge_document WHERE id = ? AND user_id = ?",
                id,
                userId
        ) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在");
        }
    }

    private void enforceQuota(int userId, int incomingCharacters) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM personal_knowledge_document WHERE user_id = ?",
                Integer.class,
                userId
        );
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(content_chars), 0) FROM personal_knowledge_document WHERE user_id = ?",
                Long.class,
                userId
        );
        if (count != null && count >= MAX_DOCUMENTS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "个人资料最多保存 30 份，请先删除不再使用的资料");
        }
        if ((total == null ? 0 : total) + incomingCharacters > MAX_TOTAL_CHARACTERS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "个人资料总文本不能超过 60 万字符");
        }
    }

    private Document findMetadata(int userId, long id) {
        List<Document> rows = jdbcTemplate.query("""
                        SELECT id, title, source_type, original_filename, content_chars, created_at, updated_at
                        FROM personal_knowledge_document WHERE id = ? AND user_id = ?
                        """,
                (rs, rowNum) -> new Document(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("original_filename"),
                        rs.getInt("content_chars"),
                        timestamp(rs.getTimestamp("created_at")),
                        timestamp(rs.getTimestamp("updated_at"))
                ),
                id,
                userId
        );
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在");
        return rows.get(0);
    }

    private static String normalizeSourceType(String value) {
        return "CAREER_CONTEXT".equalsIgnoreCase(value) ? "CAREER_CONTEXT" : "DOCUMENT";
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String normalized = value.strip();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
