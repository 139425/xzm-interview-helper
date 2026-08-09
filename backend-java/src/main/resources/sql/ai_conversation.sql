-- 创建AI对话记录表
CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` int DEFAULT 1 COMMENT '用户ID',
  `memory_id` int NOT NULL COMMENT '会话记忆ID',
  `question` varchar(2000) DEFAULT NULL COMMENT '用户问题',
  `message` text COMMENT '完整的对话消息JSON',
  `chat_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '对话时间',
  PRIMARY KEY (`id`),
  KEY `idx_memory_id` (`memory_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_chat_time` (`chat_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='存储 AI 对话记录：用户提问与 AI 回复';


ALTER TABLE `ai_conversation` MODIFY COLUMN `user_id` int DEFAULT 1 COMMENT '用户ID';

-- 添加record字段存储AI回复
ALTER TABLE `ai_conversation` ADD COLUMN `record` text COMMENT 'AI回复内容' AFTER `message`;







-- Do not seed default accounts from a reusable migration.
