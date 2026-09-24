-- ============================================================
-- V1__init_schema.sql — ORDO MVP 스키마
-- 규칙: 테이블/컬럼 snake_case, enum 은 VARCHAR 에 영문 대문자 이름 저장,
--       모든 사용자 데이터 테이블은 created_at / updated_at 보유.
-- 이 파일은 이미 적용된 뒤에는 절대 수정하지 않는다. 변경은 새 버전 파일로.
-- ============================================================

-- ---------- 학사 기준정보 (읽기 전용, V2 에서 시드) ----------

CREATE TABLE colleges (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_colleges_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 학번마다 학과 이름이 달라서, '학번 x 전공' 조합은 graduation_requirements 가 표현한다.
-- 회원가입 화면의 전공 목록 = 선택한 입학년도에 graduation_requirements 행이 있는 전공.
CREATE TABLE majors (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    college_id      BIGINT        NOT NULL,
    department_name VARCHAR(50)   NOT NULL COMMENT '학부(과)',
    major_name      VARCHAR(50)   NULL     COMMENT '전공 (학부 아래 전공이 있을 때)',
    display_name    VARCHAR(100)  NOT NULL COMMENT '화면 표시명: 학부 + 전공',
    course_unit     VARCHAR(100)  NULL     COMMENT 'courses.unit_name 매칭 키 (NULL=교과목 자동판별 불가)',
    convergence     BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '융합전공 여부(다전공 전용)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_majors_display (display_name),
    CONSTRAINT fk_majors_college FOREIGN KEY (college_id) REFERENCES colleges (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- NULL = 해당 없음('-' 또는 자료에 없음)
CREATE TABLE graduation_requirements (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    major_id                    BIGINT       NOT NULL,
    admission_year              INT          NOT NULL,
    total_credits               INT          NULL COMMENT '졸업학점',
    basic_credits               INT          NULL COMMENT '단일전공 전공기초',
    required_credits            INT          NULL COMMENT '단일전공 전공필수',
    elective_credits            INT          NULL COMMENT '단일전공 전공선택',
    major_total_credits         INT          NULL COMMENT '단일전공 전공 계',
    other_major_credits         INT          NULL COMMENT '단일전공 타전공 인정 한도',
    double_basic_credits        INT          NULL,
    double_required_credits     INT          NULL,
    double_elective_credits     INT          NULL,
    double_total_credits        INT          NULL,
    double_other_major_credits  INT          NULL,
    minor_required_credits      INT          NULL,
    minor_elective_credits      INT          NULL,
    minor_total_credits         INT          NULL,
    sw_requirement              VARCHAR(50)  NULL COMMENT 'SW기초교육 (2026만 자료 있음)',
    english_lecture_requirement VARCHAR(50)  NULL COMMENT '영어강의 의무',
    thesis_requirement          VARCHAR(50)  NULL COMMENT '졸업논문',
    topik_requirement           VARCHAR(100) NULL COMMENT 'TOPIK(외국인)',
    competency_certification    VARCHAR(50)  NULL COMMENT '졸업능력인증제',
    PRIMARY KEY (id),
    UNIQUE KEY uk_grad_req_major_year (major_id, admission_year),
    CONSTRAINT fk_grad_req_major FOREIGN KEY (major_id) REFERENCES majors (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 후마니타스칼리지 교양 기본구조 (현재 2026 자료만 존재 → 다른 학번은 2026 값으로 근사)
CREATE TABLE general_education_requirements (
    id                     BIGINT NOT NULL AUTO_INCREMENT,
    admission_year         INT    NOT NULL,
    required_credits       INT    NOT NULL COMMENT '필수교과',
    distribution_credits   INT    NOT NULL COMMENT '배분이수',
    distribution_min_areas INT    NOT NULL COMMENT '배분이수 최소 영역 수 (5개 중)',
    free_credits           INT    NOT NULL COMMENT '자유이수',
    total_credits          INT    NOT NULL COMMENT '교양 합계',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_req_year (admission_year)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE general_education_required_courses (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    admission_year    INT          NOT NULL,
    group_name        VARCHAR(50)  NULL COMMENT '문명전개의 지구적 문맥 / 글쓰기 / 영어',
    course_name       VARCHAR(100) NOT NULL,
    credits           INT          NOT NULL,
    recommended_grade VARCHAR(10)  NULL,
    note              VARCHAR(200) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 2026 전공 교과목 마스터. 같은 학수번호가 여러 학과에 다른 이수구분으로 존재하므로
-- (unit_name, course_code) 로 조회한다. course_code 단독 UNIQUE 아님!
CREATE TABLE courses (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    college_name     VARCHAR(50)  NOT NULL,
    unit_name        VARCHAR(100) NOT NULL COMMENT '교육과정 편성 단위(학과/학부/융합전공)',
    course_code      VARCHAR(20)  NULL     COMMENT '학수번호',
    name             VARCHAR(100) NOT NULL,
    classification   VARCHAR(30)  NOT NULL COMMENT 'MAJOR_BASIC/MAJOR_REQUIRED/MAJOR_ELECTIVE/TEACHING/TEACHING_MAJOR',
    credits          INT          NOT NULL,
    variable_credits BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '학점이 1-3 처럼 가변',
    target_grade     VARCHAR(10)  NULL COMMENT '권장 이수학년 (1, 3-4 ...)',
    open_semester    VARCHAR(10)  NULL COMMENT '1 / 2 / 1,2',
    track            VARCHAR(200) NULL,
    PRIMARY KEY (id),
    KEY idx_courses_unit_code (unit_name, course_code),
    KEY idx_courses_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------- 사용자 / 인증 ----------

CREATE TABLE users (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    email                VARCHAR(100) NOT NULL,
    password             VARCHAR(255) NULL COMMENT 'BCrypt. 소셜 로그인 사용자는 NULL',
    provider             VARCHAR(20)  NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL / KAKAO',
    role                 VARCHAR(20)  NOT NULL DEFAULT 'USER',
    name                 VARCHAR(30)  NOT NULL,
    nickname             VARCHAR(30)  NULL,
    student_number       VARCHAR(20)  NULL COMMENT '학번 (예: 2026105632)',
    phone                VARCHAR(20)  NULL,
    profile_image_url    VARCHAR(500) NULL,
    admission_year       INT          NULL COMMENT '입학년도(학번 기준 교육과정 선택용)',
    major_id             BIGINT       NULL,
    current_semester     INT          NULL COMMENT '현재 몇 번째 학기 (1~8+)',
    notification_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT fk_users_major FOREIGN KEY (major_id) REFERENCES majors (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_token (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------- 일정 / 할 일 ----------
-- start_time 이 있으면 '일정'(타임라인), 없으면 '할 일'(체크박스)로 화면에 표시.

CREATE TABLE schedules (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    title                VARCHAR(100) NOT NULL,
    category             VARCHAR(20)  NOT NULL COMMENT 'LECTURE(수업) / ASSIGNMENT(과제) / PERSONAL(개인일정)',
    schedule_date        DATE         NOT NULL,
    start_time           TIME         NULL,
    end_time             TIME         NULL,
    location             VARCHAR(100) NULL,
    memo                 VARCHAR(1000) NULL,
    done                 BOOLEAN      NOT NULL DEFAULT FALSE,
    alarm_minutes_before INT          NULL COMMENT 'NULL=알림 없음, 10/60/1440 ...',
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_schedules_user_date (user_id, schedule_date),
    CONSTRAINT fk_schedules_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------- 시간표 (주간 반복) ----------

CREATE TABLE timetable_entries (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    academic_year INT        NOT NULL,
    term        VARCHAR(10)  NOT NULL COMMENT 'FIRST / SECOND / SUMMER / WINTER',
    course_name VARCHAR(100) NOT NULL,
    course_code VARCHAR(20)  NULL,
    day_of_week INT          NOT NULL COMMENT '1=월 ... 7=일 (ISO)',
    start_time  TIME         NOT NULL,
    end_time    TIME         NOT NULL,
    location    VARCHAR(100) NULL,
    professor   VARCHAR(50)  NULL,
    color       VARCHAR(7)   NULL COMMENT '#RRGGBB',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_timetable_user_term (user_id, academic_year, term),
    CONSTRAINT fk_timetable_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------- 내 이수내역 ----------

CREATE TABLE completed_courses (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    course_code       VARCHAR(20)  NULL,
    course_name       VARCHAR(100) NOT NULL,
    credits           INT          NOT NULL,
    classification    VARCHAR(30)  NOT NULL COMMENT 'Classification enum',
    distribution_area INT          NULL COMMENT '배분이수 영역 1~5 (GEN_DISTRIBUTION 일 때 필수)',
    grade             VARCHAR(3)   NULL COMMENT 'A+ A0 B+ ... F / P / NP',
    academic_year     INT          NOT NULL,
    term              VARCHAR(10)  NOT NULL COMMENT 'FIRST / SECOND / SUMMER / WINTER',
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_completed_user (user_id),
    CONSTRAINT fk_completed_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
