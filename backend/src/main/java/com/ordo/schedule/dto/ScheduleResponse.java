package com.ordo.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.domain.ScheduleCategory;
import java.time.LocalDate;
import java.time.LocalTime;

/** type: startTime 있으면 EVENT(일정), 없으면 TODO(할 일) */
public record ScheduleResponse(Long id, String title, ScheduleCategory category, LocalDate date,
                               @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                               @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                               String location, String memo, boolean done, Integer alarmMinutesBefore,
                               String type) {

    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getTitle(), schedule.getCategory(),
                schedule.getScheduleDate(), schedule.getStartTime(), schedule.getEndTime(), schedule.getLocation(),
                schedule.getMemo(), schedule.isDone(), schedule.getAlarmMinutesBefore(),
                schedule.getStartTime() == null ? "TODO" : "EVENT");
    }
}
