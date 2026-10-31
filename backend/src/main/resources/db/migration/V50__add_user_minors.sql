-- T21 부전공 추적. 과목별 최종 인정은 학과 확인이 필요하다.
CREATE TABLE user_minors (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    major_id   BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_minors_user (user_id),
    CONSTRAINT fk_user_minors_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_minors_major FOREIGN KEY (major_id) REFERENCES majors (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
