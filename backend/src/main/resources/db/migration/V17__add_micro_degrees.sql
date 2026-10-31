-- T20 마이크로디그리. 계획: docs/plans/T19-T20-double-major-micro-degree.md
-- micro_degrees·micro_degree_courses 는 기준정보(V18 시드), user_micro_degrees 는 사용자가 등록한 MD
CREATE TABLE micro_degrees (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(100) NOT NULL,
    scope                 VARCHAR(10)  NOT NULL COMMENT '국제 / 공통',
    college_name          VARCHAR(50)  NOT NULL COMMENT '운영(주관)대학',
    host_department       VARCHAR(50)  NOT NULL COMMENT '주관학과',
    required_course_count INT          NOT NULL COMMENT '판정은 과목 수 기준',
    required_credits      INT          NOT NULL COMMENT '화면 표시용',
    min_departments       INT          NOT NULL DEFAULT 0 COMMENT '2 면 참여학과 2개 이상 조건 적용 (운영지침 제5조②)',
    selective             BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '선발제(자동 승인 아님)',
    requirement_text      VARCHAR(200) NOT NULL COMMENT '이수요건 안내 문구',
    guide_url             VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_micro_degrees_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- course_code 는 courses 에 FK 를 걸지 않는다(후마니타스·타대학 과목은 courses 에 없음)
CREATE TABLE micro_degree_courses (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    micro_degree_id BIGINT       NOT NULL,
    course_code     VARCHAR(20)  NULL     COMMENT '현행 학수번호. NULL 이면 과목명으로 찾음',
    alias_code      VARCHAR(20)  NULL     COMMENT '구 학수번호·사이트 오기',
    course_name     VARCHAR(100) NOT NULL,
    department_name VARCHAR(50)  NOT NULL COMMENT '개설학과. 참여학과 수를 셀 때 쓴다',
    credits         INT          NULL     COMMENT 'NULL = 자료에서 확인 못함',
    mandatory       BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '반드시 이수',
    group_key       VARCHAR(10)  NULL     COMMENT '선택그룹',
    group_min       INT          NULL     COMMENT '그룹에서 최소 이수 과목 수',
    group_max       INT          NULL     COMMENT '그룹에서 인정하는 최대 과목 수. NULL = 제한 없음',
    PRIMARY KEY (id),
    KEY idx_micro_degree_courses_degree (micro_degree_id),
    CONSTRAINT fk_micro_degree_courses_degree FOREIGN KEY (micro_degree_id) REFERENCES micro_degrees (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_micro_degrees (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    micro_degree_id BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_micro_degrees (user_id, micro_degree_id),
    CONSTRAINT fk_user_micro_degrees_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_micro_degrees_degree FOREIGN KEY (micro_degree_id) REFERENCES micro_degrees (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
