CREATE TABLE IF NOT EXISTS ai_conversation_identity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id INT NOT NULL,
    memory_id INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_public_id (public_id),
    UNIQUE KEY uk_conversation_user_memory (user_id, memory_id),
    KEY idx_conversation_identity_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
