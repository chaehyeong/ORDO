package com.ordo.timetable.dto;

import com.ordo.global.common.Term;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record TimetableEntryCreateRequest(
        @NotNull @Min(2000) @Max(2100) Integer year,
        @NotNull Term term,
        @NotBlank @Size(max = 100) String courseName,
        @Size(max = 20) String courseCode,
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Size(max = 100) String location,
        @Size(max = 50) String professor,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "#RRGGBB 형식이어야 합니다.") String color) {
}
