package com.ordo.schedule.dto;

import com.ordo.schedule.domain.ScheduleCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull ScheduleCategory category,
        @NotNull LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 100) String location,
        @Size(max = 1000) String memo,
        Integer alarmMinutesBefore) {
}
