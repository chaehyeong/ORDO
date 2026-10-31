-- 부전공 지정 과목. 행이 없는 전공은 그 학과 전공필수·전공선택으로 판정한다 (명세 5장 14번, V23 시드)
CREATE TABLE minor_designated_courses (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    major_id    BIGINT      NOT NULL,
    course_code VARCHAR(20) NOT NULL,
    role        VARCHAR(20) NOT NULL COMMENT 'REQUIRED 지정 필수 / ELECTIVE 지정 인정',
    PRIMARY KEY (id),
    UNIQUE KEY uk_minor_designated_courses (major_id, course_code),
    CONSTRAINT fk_minor_designated_courses_major FOREIGN KEY (major_id) REFERENCES majors (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
