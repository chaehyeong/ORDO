package com.ordo.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes!!";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 3600, 1209600);

    @Test
    void accessTokenRoundTrip() {
        assertThat(jwtProvider.getUserId(jwtProvider.createAccessToken(7L))).isEqualTo(7L);
    }

    @Test
    void refreshTokenIsRejectedAsAccessToken() {
        String refreshToken = jwtProvider.createRefreshToken(7L);

        assertThatThrownBy(() -> jwtProvider.getUserId(refreshToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenThrowsExpiredJwtException() {
        String expired = new JwtProvider(SECRET, -1, -1).createAccessToken(7L);

        assertThatThrownBy(() -> jwtProvider.getUserId(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithOtherKeyIsRejected() {
        String forged = new JwtProvider("another-secret-key-at-least-32-bytes-long!!", 3600, 3600).createAccessToken(7L);

        assertThatThrownBy(() -> jwtProvider.getUserId(forged)).isInstanceOf(JwtException.class);
    }
}
