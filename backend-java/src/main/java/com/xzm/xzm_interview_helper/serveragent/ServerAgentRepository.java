package com.xzm.xzm_interview_helper.serveragent;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ServerAgentRepository {
    private final JdbcTemplate jdbcTemplate;

    public void createApproval(
            String id,
            int userId,
            ServerToolName tool,
            String actionHash,
            String actionSummary,
            String actionPayload,
            LocalDateTime expiresAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO server_agent_approval (
                            id, user_id, tool_name, action_hash, action_summary, action_payload, expires_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                userId,
                tool.name(),
                actionHash,
                actionSummary,
                actionPayload,
                Timestamp.valueOf(expiresAt)
        );
    }

    public boolean approve(String id, int userId, String tokenHash) {
        return jdbcTemplate.update("""
                        UPDATE server_agent_approval
                        SET status = 'APPROVED', token_hash = ?, approved_at = NOW()
                        WHERE id = ? AND user_id = ? AND status = 'PENDING' AND expires_at > NOW()
                        """,
                tokenHash,
                id,
                userId
        ) == 1;
    }

    public boolean consume(String id, int userId, String actionHash, String tokenHash) {
        return jdbcTemplate.update("""
                        UPDATE server_agent_approval
                        SET status = 'CONSUMED', consumed_at = NOW()
                        WHERE id = ? AND user_id = ? AND action_hash = ? AND token_hash = ?
                          AND status = 'APPROVED' AND expires_at > NOW()
                        """,
                id,
                userId,
                actionHash,
                tokenHash
        ) == 1;
    }

    @Transactional
    public Optional<String> consumeAndLoad(String id, int userId, String tokenHash) {
        int updated = jdbcTemplate.update("""
                        UPDATE server_agent_approval
                        SET status = 'CONSUMED', consumed_at = NOW()
                        WHERE id = ? AND user_id = ? AND token_hash = ?
                          AND status = 'APPROVED' AND expires_at > NOW()
                        """,
                id,
                userId,
                tokenHash
        );
        if (updated != 1) return Optional.empty();
        String payload = jdbcTemplate.queryForObject(
                "SELECT action_payload FROM server_agent_approval WHERE id = ? AND user_id = ?",
                String.class,
                id,
                userId
        );
        return Optional.ofNullable(payload);
    }

    public void appendAudit(
            int userId,
            String operation,
            String target,
            ServerRisk risk,
            String status,
            String approvalRequestId,
            Integer exitCode,
            long durationMs,
            String outputExcerpt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO server_agent_audit (
                            user_id, operation, target, risk, status, approval_request_id,
                            exit_code, duration_ms, output_excerpt
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                userId,
                operation,
                target,
                risk.name(),
                status,
                approvalRequestId,
                exitCode,
                durationMs,
                outputExcerpt
        );
    }

    public List<Map<String, Object>> findRecentAudits(int userId, int requestedLimit) {
        int limit = Math.max(1, Math.min(500, requestedLimit));
        return jdbcTemplate.queryForList("""
                        SELECT id, operation, target, risk, status, approval_request_id AS approvalRequestId,
                               exit_code AS exitCode, duration_ms AS durationMs,
                               output_excerpt AS outputExcerpt, created_at AS createdAt
                        FROM server_agent_audit
                        WHERE user_id = ?
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                userId,
                limit
        );
    }
}
