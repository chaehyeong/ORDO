package com.ordo.timetable.dto;

import com.ordo.global.common.Term;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

/**
 * 한 학기 시간표 여러 칸 저장 (T12 파일 가져오기용).
 * REPLACE: 그 학기의 내 칸을 모두 지우고 새로 넣음(재업로드해도 결과 같음), MERGE: 기존 칸은 두고 같은 칸은 건너뜀
 */
public record TimetableEntryBulkRequest(
        @NotNull @Min(2000) @Max(2100) Integer year,
        @NotNull Term term,
        @NotNull Mode mode,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull Entry> entries) {

    public enum Mode { REPLACE, MERGE }

    public record Entry(
            @NotBlank @Size(max = 100) String courseName,
            @Size(max = 20) String courseCode,
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Size(max = 100) String location,
            @Size(max = 50) String professor,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "#RRGGBB 형식이어야 합니다.") String color) {
    }
}
