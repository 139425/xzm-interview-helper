package com.xzm.xzm_interview_helper.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Maps private, opaque URL ids to the integer memory ids used by the AI pipeline. */
@Service
public class ConversationIdentityService {

    private static final int MIN_MEMORY_ID = 100_000_000;
    private static final int MEMORY_ID_RANGE = 2_000_000_000;

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom random = new SecureRandom();

    public ConversationIdentityService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ConversationIdentity create(int userId) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int memoryId = MIN_MEMORY_ID + random.nextInt(MEMORY_ID_RANGE);
            Integer occupied = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ai_conversation WHERE user_id = ? AND memory_id = ?",
                    Integer.class,
                    userId,
                    memoryId
            );
            if (occupied != null && occupied > 0) continue;
            try {
                String publicId = UUID.randomUUID().toString();
                jdbcTemplate.update(
                        "INSERT INTO ai_conversation_identity(public_id, user_id, memory_id) VALUES (?, ?, ?)",
                        publicId,
                        userId,
                        memoryId
                );
                return new ConversationIdentity(publicId, memoryId);
            } catch (DuplicateKeyException ignored) {
                // Retry a cryptographically random id; a collision is exceptionally unlikely.
            }
        }
        throw new IllegalStateException("暂时无法创建新会话，请稍后重试");
    }

    public String getOrCreatePublicId(int userId, int memoryId) {
        List<String> existing = jdbcTemplate.queryForList(
                "SELECT public_id FROM ai_conversation_identity WHERE user_id = ? AND memory_id = ? LIMIT 1",
                String.class,
                userId,
                memoryId
        );
        if (!existing.isEmpty()) return existing.get(0);

        String publicId = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update(
                    "INSERT INTO ai_conversation_identity(public_id, user_id, memory_id) VALUES (?, ?, ?)",
                    publicId,
                    userId,
                    memoryId
            );
            return publicId;
        } catch (DuplicateKeyException ignored) {
            return jdbcTemplate.queryForObject(
                    "SELECT public_id FROM ai_conversation_identity WHERE user_id = ? AND memory_id = ? LIMIT 1",
                    String.class,
                    userId,
                    memoryId
            );
        }
    }

    public ConversationIdentity resolve(int userId, String publicId) {
        requireUuid(publicId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT public_id, memory_id FROM ai_conversation_identity WHERE public_id = ? AND user_id = ? LIMIT 1",
                publicId,
                userId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在或无权访问");
        }
        Map<String, Object> row = rows.get(0);
        return new ConversationIdentity(
                String.valueOf(row.get("public_id")),
                ((Number) row.get("memory_id")).intValue()
        );
    }

    public void delete(int userId, int memoryId) {
        jdbcTemplate.update(
                "DELETE FROM ai_conversation_identity WHERE user_id = ? AND memory_id = ?",
                userId,
                memoryId
        );
    }

    private void requireUuid(String publicId) {
        try {
            if (publicId == null || !UUID.fromString(publicId).toString().equals(publicId.toLowerCase())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话地址无效");
        }
    }

    public record ConversationIdentity(String conversationId, int memoryId) {}
}
