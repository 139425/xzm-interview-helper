-- Schema rebuild for xzm_interview_helper
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS helper_user (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  user_id     INT NOT NULL,
  username    VARCHAR(50) NOT NULL,
  password    VARCHAR(255) NOT NULL,
  user_type   VARCHAR(20) NOT NULL DEFAULT '普通用户',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id),
  UNIQUE KEY uk_username (username),
  KEY idx_user_type (user_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_conversation (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  user_id    INT NULL,
  memory_id  INT NOT NULL,
  question   VARCHAR(2000) NULL,
  message    TEXT NULL,
  record     TEXT NULL,
  thinking   TEXT NULL,
  chat_time  TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_memory_id (memory_id),
  KEY idx_user_id (user_id),
  KEY idx_chat_time (chat_time),
  KEY idx_memory_user (memory_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_conversation_session (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  user_id      INT NULL,
  memory_id    INT NOT NULL,
  title        VARCHAR(500) NULL,
  ceeate_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_memory_id (memory_id),
  KEY idx_user_memory (user_id, memory_id),
  KEY idx_ceeate_time (ceeate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_interview (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  user_id          INT NOT NULL,
  interview_id     BIGINT NOT NULL,
  user_description TEXT NULL,
  is_finish        TINYINT NOT NULL DEFAULT 0,
  create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_interview_id (interview_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_interview_question_reply (
  id                         BIGINT NOT NULL AUTO_INCREMENT,
  user_id                    INT NOT NULL,
  interview_id               BIGINT NOT NULL,
  question                   TEXT NULL,
  reply                      TEXT NULL,
  type                       VARCHAR(100) NULL,
  score                      TINYINT NULL,
  evaluation                 TEXT NULL,
  reference_answer_direction TEXT NULL,
  create_time                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_interview (user_id, interview_id),
  KEY idx_interview_id (interview_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_interview_summary (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  user_id      INT NOT NULL,
  interview_id BIGINT NOT NULL,
  summary      TEXT NULL,
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_interview (user_id, interview_id),
  KEY idx_interview_id (interview_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_interview_agent_session (
  id                     BIGINT NOT NULL AUTO_INCREMENT,
  public_id              CHAR(36) NOT NULL,
  user_id                INT NOT NULL,
  status                 VARCHAR(32) NOT NULL,
  resume_text            LONGTEXT NOT NULL,
  resume_file_name       VARCHAR(255) NULL,
  target_role            VARCHAR(255) NULL,
  model_provider         VARCHAR(64) NULL,
  model_name             VARCHAR(128) NULL,
  thinking_enabled       TINYINT(1) NOT NULL DEFAULT 0,
  total_question_count   INT NOT NULL DEFAULT 0,
  primary_question_count INT NOT NULL DEFAULT 0,
  follow_up_count        INT NOT NULL DEFAULT 0,
  summary                LONGTEXT NULL,
  started_at             DATETIME NULL,
  completed_at           DATETIME NULL,
  create_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_session_public_id (public_id),
  KEY idx_agent_session_user_created (user_id, create_time),
  KEY idx_agent_session_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_interview_agent_turn (
  id                       BIGINT NOT NULL AUTO_INCREMENT,
  session_id               BIGINT NOT NULL,
  sequence_no              INT NOT NULL,
  parent_turn_id           BIGINT NULL,
  question_kind            VARCHAR(32) NOT NULL,
  question                 LONGTEXT NOT NULL,
  answer                   LONGTEXT NULL,
  score                    TINYINT NULL,
  evaluation               LONGTEXT NULL,
  knowledge_tags           TEXT NULL,
  reference_answer         LONGTEXT NULL,
  agent_action             VARCHAR(32) NULL,
  decision_note            VARCHAR(1000) NULL,
  model_provider           VARCHAR(64) NULL,
  model_name               VARCHAR(128) NULL,
  created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  answered_at              DATETIME NULL,
  evaluated_at             DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_turn_sequence (session_id, sequence_no),
  KEY idx_agent_turn_session (session_id),
  KEY idx_agent_turn_parent (parent_turn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_interview_agent_event (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  session_id        BIGINT NOT NULL,
  turn_id           BIGINT NULL,
  sequence_no       INT NOT NULL,
  event_type        VARCHAR(32) NOT NULL,
  tool_name         VARCHAR(64) NULL,
  title             VARCHAR(255) NOT NULL,
  detail            VARCHAR(2000) NULL,
  payload_json      TEXT NULL,
  visibility        VARCHAR(32) NOT NULL DEFAULT 'candidate',
  create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_event_sequence (session_id, sequence_no),
  KEY idx_agent_event_session (session_id),
  KEY idx_agent_event_turn (turn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS algorithm_submission (
  id                   BIGINT NOT NULL AUTO_INCREMENT,
  user_id              INT NOT NULL,
  interview_session_id BIGINT NULL,
  problem_slug         VARCHAR(120) NOT NULL,
  problem_source       VARCHAR(64) NOT NULL,
  difficulty           VARCHAR(16) NOT NULL,
  language             VARCHAR(32) NOT NULL,
  source_code          LONGTEXT NOT NULL,
  status               VARCHAR(32) NOT NULL,
  passed_cases         INT NOT NULL DEFAULT 0,
  total_cases          INT NOT NULL DEFAULT 0,
  runtime_ms           BIGINT NULL,
  output               LONGTEXT NULL,
  error_message        TEXT NULL,
  ai_status             VARCHAR(32) NULL,
  ai_score              INT NULL,
  ai_evaluation         LONGTEXT NULL,
  ai_evaluated_at       DATETIME NULL,
  create_time          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_algorithm_submission_user_time (user_id, create_time),
  KEY idx_algorithm_submission_problem (problem_slug),
  KEY idx_algorithm_submission_interview (interview_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS algorithm_interview_challenge (
  id                   BIGINT NOT NULL AUTO_INCREMENT,
  interview_session_id BIGINT NOT NULL,
  turn_id              BIGINT NOT NULL,
  user_id              INT NOT NULL,
  problem_slug         VARCHAR(120) NOT NULL,
  difficulty           VARCHAR(16) NOT NULL,
  time_limit_minutes   INT NOT NULL,
  status               VARCHAR(32) NOT NULL,
  latest_submission_id BIGINT NULL,
  started_at           DATETIME NOT NULL,
  deadline_at          DATETIME NOT NULL,
  completed_at         DATETIME NULL,
  create_time          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_algorithm_challenge_session (interview_session_id),
  UNIQUE KEY uk_algorithm_challenge_turn (turn_id),
  KEY idx_algorithm_challenge_user_status (user_id, status),
  KEY idx_algorithm_challenge_deadline (deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table backing the AiConversionSession entity (camel-case columns)
CREATE TABLE IF NOT EXISTS ai_conversion_session (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  userId     BIGINT NOT NULL,
  memoryId   BIGINT NOT NULL,
  title      VARCHAR(500) NOT NULL,
  createTime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_userId (userId),
  KEY idx_memoryId (memoryId),
  KEY idx_user_memory (userId, memoryId),
  KEY idx_createTime (createTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- No default users are created here. Provision administrators through a
-- deployment-only bootstrap with a unique, securely hashed password.
