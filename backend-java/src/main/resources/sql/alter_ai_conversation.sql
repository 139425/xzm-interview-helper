-- 修改现有表的user_id字段，设置默认值为1
ALTER TABLE `ai_conversation` MODIFY COLUMN `user_id` int DEFAULT 1 COMMENT '用户ID';