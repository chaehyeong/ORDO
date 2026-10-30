-- T18 학기별 배치하기 (백엔드 A). 계획: docs/plans/T15-figma-final.md
-- 아직 듣지 않은 계획이라 이수현황·평점에 넣지 않는다. 사용자별로 통째로 교체해서 저장한다
CREATE TABLE planned_courses (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    academic_year  INT          NOT NULL,
    term           VARCHAR(10)  NOT NULL COMMENT 'FIRST / SECOND / SUMMER / WINTER',
    course_name    VARCHAR(100) NOT NULL,
    course_code    VARCHAR(20)  NULL,
    classification VARCHAR(30)  NULL COMMENT '직접 추가한 과목은 없을 수 있음',
    credits        INT          NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_planned_courses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
