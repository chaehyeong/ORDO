package com.ordo.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 브라우저의 subscription.toJSON() 그대로 (expirationTime 은 무시) */
public record PushSubscribeRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys) {

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }
}
