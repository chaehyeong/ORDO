package com.ordo.schedule.dto;

import jakarta.validation.constraints.NotNull;

public record ScheduleDoneRequest(@NotNull Boolean done) {
}
