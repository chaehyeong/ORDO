package com.ordo.user.controller;

import com.ordo.global.common.ApiResponse;
import com.ordo.global.security.LoginUser;
import com.ordo.user.dto.UserResponse;
import com.ordo.user.dto.UserSettingsRequest;
import com.ordo.user.dto.UserSummaryResponse;
import com.ordo.user.dto.UserUpdateRequest;
import com.ordo.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<UserResponse> getMe(@LoginUser Long userId) {
        return ApiResponse.ok(userService.getMe(userId));
    }

    @GetMapping("/summary")
    public ApiResponse<UserSummaryResponse> getSummary(@LoginUser Long userId) {
        return ApiResponse.ok(userService.getSummary(userId));
    }

    @PatchMapping
    public ApiResponse<UserResponse> updateMe(@LoginUser Long userId, @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.updateMe(userId, request));
    }

    @PatchMapping("/settings")
    public ApiResponse<UserResponse> updateSettings(@LoginUser Long userId,
                                                    @Valid @RequestBody UserSettingsRequest request) {
        return ApiResponse.ok(userService.updateSettings(userId, request));
    }
}
