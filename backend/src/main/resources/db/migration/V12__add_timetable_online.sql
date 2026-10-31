-- T15 온라인 강의(시간 없는 수업). online = TRUE 면 요일·시간이 NULL
ALTER TABLE timetable_entries
    MODIFY day_of_week INT NULL COMMENT '1=월 ... 7=일 (ISO). 온라인 강의는 NULL',
    MODIFY start_time TIME NULL,
    MODIFY end_time TIME NULL,
    ADD COLUMN online BOOLEAN NOT NULL DEFAULT FALSE COMMENT '온라인 강의(시간 없음)' AFTER color;
