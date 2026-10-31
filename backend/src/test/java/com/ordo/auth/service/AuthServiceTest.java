package com.ordo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordo.auth.domain.RefreshToken;
import com.ordo.auth.dto.LoginRequest;
import com.ordo.auth.dto.SignupRequest;
import com.ordo.auth.dto.TokenResponse;
import com.ordo.auth.repository.RefreshTokenRepository;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.global.security.JwtProvider;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "haeun@khu.ac.kr";
    private static final String PASSWORD = "ordo1234!";
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    UserRepository userRepository;
    @Mock
    MajorRepository majorRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtProvider jwtProvider = new JwtProvider("test-secret-key-must-be-at-least-32-bytes!!", 3600, 1209600);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, majorRepository, refreshTokenRepository, passwordEncoder, jwtProvider);
    }

    @Test
    void signupFillsAdmissionYearFromStudentNumberAndEncodesPassword() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(majorRepository.findSelectable(35L, 2026)).willReturn(Optional.of(mock(Major.class)));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.signup(signupRequest());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getAdmissionYear()).isEqualTo(2026);
        assertThat(saved.getValue().getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getValue().getPassword())).isTrue();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.accessTokenExpiresIn()).isEqualTo(3600);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void signupWithDuplicatedEmailFails() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertError(() -> authService.signup(signupRequest()), ErrorCode.EMAIL_DUPLICATED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void signupRaceOnSameEmailIsReportedAsDuplicated() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(majorRepository.findSelectable(35L, 2026)).willReturn(Optional.of(mock(Major.class)));
        given(userRepository.save(any(User.class))).willThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertError(() -> authService.signup(signupRequest()), ErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    void signupWithMajorNotOfferedForAdmissionYearFails() {
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(majorRepository.findSelectable(35L, 2026)).willReturn(Optional.empty());

        assertError(() -> authService.signup(signupRequest()), ErrorCode.MAJOR_NOT_FOUND);
        verify(userRepository, never()).save(any());
    }

    @Test
    void signupRejectsEachBadInputWithOneFieldError() {
        Object[][] badInputs = {
                {"name", null}, {"name", ""}, {"name", "   "}, {"name", "가".repeat(31)},
                {"phone", null}, {"phone", ""}, {"phone", "abc"}, {"phone", "02-1234-5678"}, {"phone", "010-12-5678"},
                {"email", null}, {"email", ""}, {"email", "   "}, {"email", "abc"}, {"email", "a@test"},
                {"email", "a".repeat(60) + "@" + "b".repeat(40) + ".kr"},
                {"password", null}, {"password", ""}, {"password", "abc1234"}, {"password", "abcdefgh"}, {"password", "12345678"},
                {"passwordConfirm", null}, {"passwordConfirm", ""}, {"passwordConfirm", "ordo12345!"},
                {"majorId", null},
                {"studentNumber", null}, {"studentNumber", ""}, {"studentNumber", "202610563"}, {"studentNumber", "abcdefghij"},
                {"termsAgreed", null}, {"termsAgreed", false}};

        for (Object[] input : badInputs) {
            String field = (String) input[0];
            Map<String, Object> json = validSignupJson();
            json.put(field, input[1]);
            if (field.equals("password")) {
                json.put("passwordConfirm", input[1]);
            }
            assertThat(VALIDATOR.validate(MAPPER.convertValue(json, SignupRequest.class)))
                    .as("%s=%s", field, input[1])
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly(field.equals("passwordConfirm") ? "passwordConfirmed" : field);
        }
    }

    @Test
    void signupWithoutTermsAgreedFieldFails() {
        Map<String, Object> json = validSignupJson();
        json.remove("termsAgreed");

        assertThat(VALIDATOR.validate(MAPPER.convertValue(json, SignupRequest.class)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("termsAgreed");
    }

    @Test
    void signupAcceptsValidInputWithOrWithoutPhoneHyphens() {
        assertThat(VALIDATOR.validate(signupRequest())).isEmpty();
        for (String phone : new String[]{"010-1234-5678", "01012345678", "011-123-4567"}) {
            Map<String, Object> json = validSignupJson();
            json.put("phone", phone);
            assertThat(VALIDATOR.validate(MAPPER.convertValue(json, SignupRequest.class))).as(phone).isEmpty();
        }
    }

    @Test
    void loginIssuesTokens() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user()));

        TokenResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD));

        assertThat(response.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginWithWrongPasswordFails() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user()));

        assertError(() -> authService.login(new LoginRequest(EMAIL, "wrong1234")), ErrorCode.LOGIN_FAILED);
    }

    @Test
    void loginWithUnknownEmailFails() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertError(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)), ErrorCode.LOGIN_FAILED);
    }

    @Test
    void refreshDeletesOldTokenAndIssuesNewOne() {
        RefreshToken old = new RefreshToken(user(), "old-token", LocalDateTime.now().plusDays(1));
        given(refreshTokenRepository.findByToken("old-token")).willReturn(Optional.of(old));
        given(refreshTokenRepository.deleteByToken("old-token")).willReturn(1);

        TokenResponse response = authService.refresh("old-token");

        verify(refreshTokenRepository).deleteByToken("old-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(response.refreshToken()).isNotEqualTo("old-token");
    }

    @Test
    void refreshFailsWhenConcurrentRequestDeletedTheTokenFirst() {
        RefreshToken old = new RefreshToken(user(), "old-token", LocalDateTime.now().plusDays(1));
        given(refreshTokenRepository.findByToken("old-token")).willReturn(Optional.of(old));
        given(refreshTokenRepository.deleteByToken("old-token")).willReturn(0);

        assertError(() -> authService.refresh("old-token"), ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void refreshWithUnknownTokenFails() {
        given(refreshTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

        assertError(() -> authService.refresh("unknown"), ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void refreshWithExpiredTokenFails() {
        RefreshToken expired = new RefreshToken(user(), "expired", LocalDateTime.now().minusSeconds(1));
        given(refreshTokenRepository.findByToken("expired")).willReturn(Optional.of(expired));

        assertError(() -> authService.refresh("expired"), ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository, never()).save(any());
    }

    private SignupRequest signupRequest() {
        return new SignupRequest("이하은", "010-1234-5678", EMAIL, PASSWORD, PASSWORD, 35L, "2026105632", null, null, null, true);
    }

    private static Map<String, Object> validSignupJson() {
        return new HashMap<>(Map.of("name", "이하은", "phone", "010-1234-5678", "email", EMAIL, "password", PASSWORD,
                "passwordConfirm", PASSWORD, "majorId", 35L, "studentNumber", "2026105632", "termsAgreed", true));
    }

    private User user() {
        return User.builder().email(EMAIL).password(passwordEncoder.encode(PASSWORD)).name("이하은").build();
    }

    private static void assertError(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }
}
