package com.ordo.academic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 여러 건 등록. 하나라도 잘못되면 전부 저장하지 않는다 */
public record CompletedCourseBulkRequest(
        @NotEmpty @Size(max = 100) List<@Valid @NotNull CompletedCourseRequest> courses) {
}
