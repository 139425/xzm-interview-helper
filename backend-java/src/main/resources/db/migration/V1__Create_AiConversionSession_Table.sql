-- 创建 AI 会话摘要表
CREATE TABLE IF NOT EXISTS ai_conversion_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    memory_id BIGINT NOT NULL COMMENT '会话记忆ID',
    title VARCHAR(500) NOT NULL COMMENT '会话标题（第一次对话的用户输入）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_memory_id (memory_id),
    INDEX idx_user_memory (user_id, memory_id),
    INDEX idx_create_time (create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话摘要表';
