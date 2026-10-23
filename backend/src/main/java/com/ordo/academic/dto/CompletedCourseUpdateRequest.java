package com.ordo.academic.dto;

import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.Term;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보낸 필드만 변경 (null = 그대로, 값을 비우는 기능은 없음). 바꾼 뒤 이수구분을 다시 판별한다 */
public record CompletedCourseUpdateRequest(
        @Size(max = 20) String courseCode,
        @Size(min = 1, max = 100) String courseName,
        @Min(0) @Max(30) Integer credits,
        Classification classification,
        @Min(1) @Max(5) Integer distributionArea,
        @Pattern(regexp = CompletedCourseRequest.GRADE_PATTERN,
                message = "A+ A0 B+ B0 C+ C0 D+ D0 F P NP 중 하나여야 합니다.") String grade,
        @Min(2000) @Max(2100) Integer year,
        Term term) {
}
