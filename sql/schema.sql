-- chat_log 테이블 (현재 엔티티 기준)
CREATE TABLE IF NOT EXISTS chat_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mode VARCHAR(255) NULL,
  session_id VARCHAR(64) NOT NULL,
  question LONGTEXT NOT NULL,
  answer LONGTEXT NULL,
  created_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  INDEX idx_chat_log_session_id (session_id),
  INDEX idx_chat_log_created_at (created_at)
);
