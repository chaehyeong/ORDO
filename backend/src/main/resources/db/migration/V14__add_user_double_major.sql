-- T19 복수전공(다전공 1개, 융합전공 포함). 계획: docs/plans/T19-T20-double-major-micro-degree.md
ALTER TABLE users
    ADD COLUMN double_major_id BIGINT NULL COMMENT '복수전공·융합전공. NULL 이면 단일전공' AFTER major_id,
    ADD CONSTRAINT fk_users_double_major FOREIGN KEY (double_major_id) REFERENCES majors (id);
