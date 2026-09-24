package com.ordo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 프론트 SignUp.js 입력칸 기준 (명세 4.1). '비밀번호 확인'은 프론트에서만 비교 */
public record SignupRequest(
        @NotBlank @Size(max = 30) String name,
        @Size(max = 20) String phone,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "8~64자, 영문과 숫자를 모두 포함해야 합니다.")
        String password,
        @NotNull Long majorId,
        @NotBlank @Pattern(regexp = "^\\d{10}$", message = "숫자 10자리여야 합니다.") String studentNumber,
        Integer admissionYear,   // 비우면 학번 앞 4자리
        @Size(max = 30) String nickname,
        @Min(1) Integer currentSemester) {
}
