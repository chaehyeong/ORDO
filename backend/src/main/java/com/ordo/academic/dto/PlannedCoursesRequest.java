package com.ordo.academic.dto;

import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.Term;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 학기별 배치 전체 (T18). 이 목록으로 통째로 바꾼다. 빈 목록 = 초기화 */
public record PlannedCoursesRequest(@NotNull @Size(max = 100) List<@Valid @NotNull Course> courses) {

    /** credits 0 은 졸업논문 같은 0학점 전공필수, classification 은 직접 추가한 과목이면 없을 수 있다 */
    public record Course(
            @NotNull @Min(2000) @Max(2100) Integer year,
            @NotNull Term term,
            @NotBlank @Size(max = 100) String courseName,
            @Size(max = 20) String courseCode,
            Classification classification,
            @NotNull @Min(0) @Max(30) Integer credits) {
    }
}
