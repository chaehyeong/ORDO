package com.ordo.notification.controller;

import com.ordo.global.common.ApiResponse;
import com.ordo.global.security.LoginUser;
import com.ordo.notification.dto.PushPublicKeyResponse;
import com.ordo.notification.dto.PushSendResponse;
import com.ordo.notification.dto.PushSubscribeRequest;
import com.ordo.notification.dto.PushUnsubscribeRequest;
import com.ordo.notification.service.PushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 일정 알림 웹푸시 (명세 4.10, 전부 🔒). 서버에 VAPID 키가 없으면 전부 503 PUSH_NOT_CONFIGURED */
@RestController
@RequestMapping("/api/notifications/push")
@RequiredArgsConstructor
public class PushNotificationController {

    private final PushSubscriptionService pushSubscriptionService;

    @GetMapping("/public-key")
    public ApiResponse<PushPublicKeyResponse> getPublicKey() {
        return ApiResponse.ok(new PushPublicKeyResponse(pushSubscriptionService.publicKey()));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@LoginUser Long userId, @Valid @RequestBody PushSubscribeRequest request) {
        pushSubscriptionService.subscribe(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@LoginUser Long userId, @Valid @RequestBody PushUnsubscribeRequest request) {
        pushSubscriptionService.unsubscribe(userId, request.endpoint());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ApiResponse<PushSendResponse> sendTest(@LoginUser Long userId) {
        return ApiResponse.ok(pushSubscriptionService.sendTest(userId));
    }
}
