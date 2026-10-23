-- T11 일정 알림(웹푸시) (백엔드 A). 계획: docs/plans/T11-web-push.md
-- 기기(브라우저) 구독 1개 = 1행. 서버가 endpoint 로 직접 요청하므로 허용한 푸시 서비스 주소만 저장한다(서비스에서 검사).
CREATE TABLE push_subscriptions (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    endpoint   VARCHAR(700) NOT NULL COMMENT '푸시 서비스 주소(허용 목록 호스트만)',
    p256dh     VARCHAR(100) NOT NULL COMMENT '브라우저 공개키 Base64url',
    auth       VARCHAR(30)  NOT NULL COMMENT '인증 비밀 Base64url',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_subscriptions_endpoint (endpoint),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 중복 발송 방지: 처리한 발송 예정 시각을 기록(조건부 UPDATE 로 선점). 매분 날짜로 후보를 찾는 인덱스
ALTER TABLE schedules
    ADD COLUMN alarm_sent_for DATETIME NULL COMMENT '알림을 처리한 발송 예정 시각(중복 방지)',
    ADD KEY idx_schedules_date (schedule_date);

-- 시간 없는 할 일의 알림 기준 시각 (사용자가 마이페이지에서 바꿈)
ALTER TABLE users
    ADD COLUMN todo_alarm_time TIME NOT NULL DEFAULT '09:00:00' COMMENT '시간 없는 할 일의 알림 기준 시각'
        AFTER notification_enabled;
