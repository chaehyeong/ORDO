package com.ordo.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/** 프론트 SignUp.js 입력칸 기준 (명세 4.1). 칸마다 오류 문구가 하나만 나오도록, 형식 검사가 있는 칸은 빈 값도 형식 검사로 거른다 */
public record SignupRequest(
        @NotBlank(message = "이름을 입력해 주세요.") @Size(max = 30, message = "이름은 30자 이하여야 합니다.") String name,
        @NotNull(message = "전화번호를 입력해 주세요.")
        @Pattern(regexp = "^01\\d-?\\d{3,4}-?\\d{4}$", message = "전화번호는 010-1234-5678 형식이어야 합니다.")
        String phone,
        @NotNull(message = "이메일을 입력해 주세요.")
        @Email(regexp = ".+@.+\\..+", message = "이메일 주소 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,
        @NotNull(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "비밀번호는 8~64자, 영문과 숫자를 모두 포함해야 합니다.")
        String password,
        String passwordConfirm,
        @NotNull(message = "학과를 선택해 주세요.") Long majorId,
        @NotNull(message = "학번을 입력해 주세요.")
        @Pattern(regexp = "^\\d{10}$", message = "학번은 숫자 10자리여야 합니다.")
        String studentNumber,
        Integer admissionYear,   // 비우면 학번 앞 4자리
        @Size(max = 30) String nickname,
        @Min(1) Integer currentSemester,
        // ponytail: 동의 기록은 저장하지 않는다. 기록이 필요해지면 users 에 동의 시각 컬럼 추가
        @AssertTrue(message = "이용약관에 동의해야 합니다.") boolean termsAgreed) {

    @JsonIgnore
    @AssertTrue(message = "비밀번호와 비밀번호 확인이 일치하지 않습니다.")
    public boolean isPasswordConfirmed() {
        return Objects.equals(password, passwordConfirm);
    }
}
