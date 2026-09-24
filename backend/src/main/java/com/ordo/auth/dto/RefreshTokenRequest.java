package com.ordo.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** refresh, logout 공통 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
