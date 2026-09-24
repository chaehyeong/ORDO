package com.ordo.auth.dto;

import com.ordo.user.domain.User;

/** signup / login / refresh 공통 응답 */
public record TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn, UserSummary user) {

    public record UserSummary(Long id, String name, String nickname) {
    }

    public static TokenResponse of(String accessToken, String refreshToken, long accessTokenExpiresIn, User user) {
        return new TokenResponse(accessToken, refreshToken, accessTokenExpiresIn,
                new UserSummary(user.getId(), user.getName(), user.getNickname()));
    }
}
