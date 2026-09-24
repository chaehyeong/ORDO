package com.ordo.user.dto;

import jakarta.validation.constraints.NotNull;

public record UserSettingsRequest(@NotNull Boolean notificationEnabled) {
}
