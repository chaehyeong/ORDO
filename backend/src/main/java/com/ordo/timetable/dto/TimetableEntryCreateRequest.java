package com.ordo.timetable.dto;

import com.ordo.global.common.Term;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/** online=true 면 요일·시간은 보내도 저장하지 않는다. 아니면 셋 다 필수(없으면 INVALID_TIME_RANGE) */
public record TimetableEntryCreateRequest(
        @NotNull @Min(2000) @Max(2100) Integer year,
        @NotNull Term term,
        @NotBlank @Size(max = 100) String courseName,
        @Size(max = 20) String courseCode,
        @Min(1) @Max(7) Integer dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 100) String location,
        @Size(max = 50) String professor,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "#RRGGBB 형식이어야 합니다.") String color,
        Boolean online) {
}
