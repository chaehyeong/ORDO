-- e캠퍼스(Canvas) 개인 캘린더 피드 연동 (백엔드 A)
-- 피드 주소 전체가 아니라 'user_...' 토큰만 저장한다. 주소는 서버가 khcanvas.khu.ac.kr 고정 경로로 만든다.
ALTER TABLE users
    ADD COLUMN ecampus_feed_token VARCHAR(100) NULL COMMENT 'Canvas 캘린더 피드 토큰 (user_...). NULL=연결 안 함'
        AFTER notification_enabled;
