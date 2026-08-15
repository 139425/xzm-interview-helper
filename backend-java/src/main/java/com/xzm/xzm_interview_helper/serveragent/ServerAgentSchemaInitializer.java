package com.xzm.xzm_interview_helper.serveragent;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServerAgentSchemaInitializer implements InitializingBean {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS server_agent_approval (
                    id CHAR(36) NOT NULL,
                    user_id INT NOT NULL,
                    tool_name VARCHAR(32) NOT NULL,
                    action_hash CHAR(64) NOT NULL,
                    action_summary VARCHAR(2000) NOT NULL,
                    action_payload MEDIUMTEXT NULL,
                    token_hash CHAR(64) NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    expires_at DATETIME NOT NULL,
                    approved_at DATETIME NULL,
                    consumed_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_server_agent_approval_user (user_id, created_at),
                    KEY idx_server_agent_approval_expiry (status, expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        ensureColumn(
                "server_agent_approval",
                "action_payload",
                "ALTER TABLE server_agent_approval ADD COLUMN action_payload MEDIUMTEXT NULL AFTER action_summary"
        );
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS server_agent_audit (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    user_id INT NOT NULL,
                    operation VARCHAR(64) NOT NULL,
                    target VARCHAR(2000) NOT NULL DEFAULT '',
                    risk VARCHAR(16) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    approval_request_id CHAR(36) NULL,
                    exit_code INT NULL,
                    duration_ms BIGINT NOT NULL DEFAULT 0,
                    output_excerpt MEDIUMTEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_server_agent_audit_user (user_id, created_at),
                    KEY idx_server_agent_audit_status (status, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                table,
                column
        );
        if (count == null || count == 0) jdbcTemplate.execute(ddl);
    }
}
