package com.ordo.academic.dto;

import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.Term;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이수 과목 등록(1건·여러 건 공통). courseCode 가 내 전공 교과목에 있으면 classification·빈 courseName 은 서버가 채운다.
 * 그렇지 않으면 courseName·classification 필수 (명세 4.6)
 */
public record CompletedCourseRequest(
        @Size(max = 20) String courseCode,
        @Size(max = 100) String courseName,
        @NotNull @Min(0) @Max(30) Integer credits,
        Classification classification,
        @Min(1) @Max(5) Integer distributionArea,
        @Pattern(regexp = GRADE_PATTERN, message = "A+ A0 B+ B0 C+ C0 D+ D0 F P NP 중 하나여야 합니다.") String grade,
        @NotNull @Min(2000) @Max(2100) Integer year,
        @NotNull Term term) {

    public static final String GRADE_PATTERN = "^(A\\+|A0|B\\+|B0|C\\+|C0|D\\+|D0|F|P|NP)$";
}
