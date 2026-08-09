-- 用户认证系统数据库迁移脚本
-- 为helper_user表添加status字段
ALTER TABLE helper_user ADD COLUMN status VARCHAR(20) DEFAULT 'ENABLED' NOT NULL COMMENT '用户状态：ENABLED-启用，DISABLED-禁用，LOCKED-锁定';

-- 创建登录尝试记录表
CREATE TABLE login_attempt (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    ip_address VARCHAR(45) NOT NULL COMMENT 'IP地址',
    attempt_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '尝试时间',
    success BOOLEAN DEFAULT FALSE NOT NULL COMMENT '是否成功',
    failure_reason VARCHAR(100) COMMENT '失败原因',
    user_agent VARCHAR(500) COMMENT '用户代理',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_username (username),
    INDEX idx_ip_address (ip_address),
    INDEX idx_attempt_time (attempt_time)
) COMMENT '登录尝试记录表' COLLATE = utf8mb4_unicode_ci;

-- 创建用户会话管理表
CREATE TABLE user_session (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    ip_address VARCHAR(45) NOT NULL COMMENT 'IP地址',
    user_agent VARCHAR(500) COMMENT '用户代理',
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '登录时间',
    last_access_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '最后访问时间',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL COMMENT '会话状态：ACTIVE-活跃，EXPIRED-过期，LOGOUT-已登出',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_username (username),
    INDEX idx_expire_time (expire_time),
    INDEX idx_status (status)
) COMMENT '用户会话管理表' COLLATE = utf8mb4_unicode_ci;

-- 为helper_user表添加status字段的索引
CREATE INDEX idx_status ON helper_user (status);

-- Administrators must be provisioned by a deployment-only bootstrap.
