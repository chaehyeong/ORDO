package com.ordo.academic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MinorRequest(@NotNull @Positive Long majorId) {
}
