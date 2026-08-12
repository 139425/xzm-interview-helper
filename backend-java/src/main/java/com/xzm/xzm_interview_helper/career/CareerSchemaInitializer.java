package com.xzm.xzm_interview_helper.career;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CareerSchemaInitializer implements InitializingBean {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS job_application (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    user_id INT NOT NULL,
                    recruitment_posting_id BIGINT UNSIGNED NULL,
                    company VARCHAR(200) NOT NULL,
                    role_name VARCHAR(300) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'TO_APPLY',
                    location VARCHAR(300) NOT NULL DEFAULT '',
                    apply_url VARCHAR(1024) NOT NULL DEFAULT '',
                    source_url VARCHAR(1024) NOT NULL DEFAULT '',
                    deadline DATE NULL,
                    next_action VARCHAR(500) NOT NULL DEFAULT '',
                    next_action_at DATETIME NULL,
                    notes TEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_application_user_recruitment (user_id, recruitment_posting_id),
                    KEY idx_application_user_updated (user_id, updated_at),
                    KEY idx_application_user_status (user_id, status),
                    KEY idx_application_user_reminder (user_id, next_action_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS personal_knowledge_document (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    user_id INT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    source_type VARCHAR(32) NOT NULL,
                    original_filename VARCHAR(255) NOT NULL DEFAULT '',
                    content MEDIUMTEXT NOT NULL,
                    content_chars INT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_knowledge_user_updated (user_id, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }
}
