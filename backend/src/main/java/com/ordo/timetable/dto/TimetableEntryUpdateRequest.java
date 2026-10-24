package com.ordo.timetable.dto;

import com.ordo.global.common.Term;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/** 보낸 필드만 변경 (null = 그대로, 값을 비우는 기능은 없음) */
public record TimetableEntryUpdateRequest(
        @Min(2000) @Max(2100) Integer year,
        Term term,
        @Size(min = 1, max = 100) String courseName,
        @Size(max = 20) String courseCode,
        @Min(1) @Max(7) Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 100) String location,
        @Size(max = 50) String professor,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "#RRGGBB 형식이어야 합니다.") String color) {
}
