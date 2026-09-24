package com.ordo.auth.controller;

import com.ordo.auth.dto.LoginRequest;
import com.ordo.auth.dto.RefreshTokenRequest;
import com.ordo.auth.dto.SignupRequest;
import com.ordo.auth.dto.TokenResponse;
import com.ordo.auth.service.AuthService;
import com.ordo.global.common.ApiResponse;
import com.ordo.global.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authService.signup(request)));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@LoginUser Long userId, @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(userId, request.refreshToken());
        return ApiResponse.ok(null);
    }
}
