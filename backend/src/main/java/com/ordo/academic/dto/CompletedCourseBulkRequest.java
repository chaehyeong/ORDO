package com.ordo.academic.dto;

import com.ordo.global.common.Term;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 여러 건 등록. 하나라도 잘못되면 전부 저장하지 않는다.
 * replaceTerms: 그 학기들의 내 이수내역을 지우고 새로 넣음(성적표 재업로드해도 중복 없음, T12)
 * keepClassification: true 면 이수구분이 있는 과목은 자동 판별을 건너뛰고 요청값을 씀(성적표가 공식 기록, 명세 9장 17)
 */
public record CompletedCourseBulkRequest(
        @NotEmpty @Size(max = 100) List<@Valid @NotNull CompletedCourseRequest> courses,
        @Size(max = 20) List<@Valid @NotNull TermRef> replaceTerms,
        Boolean keepClassification) {

    public record TermRef(@NotNull @Min(2000) @Max(2100) Integer year, @NotNull Term term) {
    }
}
