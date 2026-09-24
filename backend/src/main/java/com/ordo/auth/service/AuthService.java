package com.ordo.auth.service;

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
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final MajorRepository majorRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    // ponytail: 동시에 같은 이메일로 가입하면 DB 유일 제약이 막고 500 응답. 문제 되면 DataIntegrityViolation → EMAIL_DUPLICATED 변환
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        // 입학년도를 비우면 학번 앞 4자리 (명세 4.1)
        int admissionYear = request.admissionYear() != null
                ? request.admissionYear()
                : Integer.parseInt(request.studentNumber().substring(0, 4));
        Major major = majorRepository.findSelectable(request.majorId(), admissionYear)
                .orElseThrow(() -> new BusinessException(ErrorCode.MAJOR_NOT_FOUND));

        User user = userRepository.save(User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .nickname(request.nickname())
                .studentNumber(request.studentNumber())
                .phone(request.phone())
                .admissionYear(admissionYear)
                .major(major)
                .currentSemester(request.currentSemester())
                .build());
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(found -> found.getPassword() != null
                        && passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        return issueTokens(user);
    }

    /** 기존 refresh 토큰은 지우고 새 한 쌍을 발급한다 */
    // ponytail: 만료된 토큰 행은 지우지 않고 남는다. 쌓이는 게 문제 되면 주기적 삭제 추가
    @Transactional
    public TokenResponse refresh(String token) {
        RefreshToken saved = refreshTokenRepository.findByToken(token)
                .filter(found -> !found.isExpired(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        refreshTokenRepository.delete(saved);
        return issueTokens(saved.getUser());
    }

    @Transactional
    public void logout(Long userId, String token) {
        refreshTokenRepository.deleteByTokenAndUserId(token, userId);
    }

    private TokenResponse issueTokens(User user) {
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(new RefreshToken(user, refreshToken,
                LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenValiditySeconds())));
        return TokenResponse.of(jwtProvider.createAccessToken(user.getId()), refreshToken,
                jwtProvider.getAccessTokenValiditySeconds(), user);
    }
}
