package com.ordo.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 보낸 필드만 변경 (null = 그대로) */
public record UserUpdateRequest(
        @Size(min = 1, max = 30) String name,
        @Size(max = 30) String nickname,
        @Pattern(regexp = "^\\d{10}$", message = "숫자 10자리여야 합니다.") String studentNumber,
        @Size(max = 20) String phone,
        Integer admissionYear,
        Long majorId,
        @Min(1) Integer currentSemester,
        @Size(max = 500) String profileImageUrl) {
}
