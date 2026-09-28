package com.ordo.schedule.dto;

import com.ordo.schedule.domain.ScheduleCategory;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/** 보낸 필드만 변경 (null = 그대로, 값을 비우는 기능은 없음) */
public record ScheduleUpdateRequest(
        @Size(min = 1, max = 100) String title,
        ScheduleCategory category,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 100) String location,
        @Size(max = 1000) String memo,
        Integer alarmMinutesBefore) {
}
