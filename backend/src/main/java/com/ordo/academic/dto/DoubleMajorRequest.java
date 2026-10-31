package com.ordo.academic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 복수전공 등록·변경 (명세 4.6, T19). 융합전공도 등록할 수 있다 */
public record DoubleMajorRequest(@NotNull @Positive Long majorId) {
}
