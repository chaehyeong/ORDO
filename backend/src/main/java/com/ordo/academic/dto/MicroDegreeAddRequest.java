package com.ordo.academic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 마이크로디그리 등록 (명세 4.6, T20) */
public record MicroDegreeAddRequest(@NotNull @Positive Long microDegreeId) {
}
