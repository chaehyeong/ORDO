package com.ordo.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey key;
    @Getter
    private final long accessTokenValiditySeconds;
    @Getter
    private final long refreshTokenValiditySeconds;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
                       @Value("${app.jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));  // 32바이트 미만이면 부팅 실패
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, ACCESS, accessTokenValiditySeconds);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, REFRESH, refreshTokenValiditySeconds);
    }

    /**
     * 액세스 토큰의 서명·만료를 검증하고 userId 를 꺼낸다.
     * 만료면 ExpiredJwtException, 그 외 잘못된 토큰(refresh 토큰 포함)이면 JwtException.
     */
    public Long getUserId(String accessToken) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken).getPayload();
        if (!ACCESS.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new MalformedJwtException("액세스 토큰이 아닙니다.");
        }
        return Long.valueOf(claims.getSubject());
    }

    private String createToken(Long userId, String type, long validitySeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYPE_CLAIM, type)
                .id(UUID.randomUUID().toString())  // 같은 초에 발급해도 토큰이 겹치지 않게 (refresh_tokens.token 유일)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySeconds)))
                .signWith(key)
                .compact();
    }
}
