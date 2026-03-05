-- init.sql
-- 목적: MySQL DB/계정 생성 및 권한 부여
-- 실행 계정: CREATE/GRANT 권한이 있는 관리자(root 등)

-- 1) 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS chatbot;

-- 2) 애플리케이션 계정 생성
-- 필요 시 비밀번호를 강한 값으로 변경하세요.
CREATE USER IF NOT EXISTS 'chatbot_app'@'localhost' IDENTIFIED BY '<CHANGE_ME_STRONG_PASSWORD>';

-- 3) 최소 권한 부여 (chatbot DB 범위)
GRANT ALL PRIVILEGES ON chatbot.* TO 'chatbot_app'@'localhost';

-- 5) 확인용 (선택)
-- SHOW GRANTS FOR 'chatbot_app'@'localhost';
