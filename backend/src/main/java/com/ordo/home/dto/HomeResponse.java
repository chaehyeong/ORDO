package com.ordo.home.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ordo.schedule.domain.ScheduleCategory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 홈 화면 한 번에 (명세 4.7). graduation 은 학사 프로필 미완성이면 null. AI 추천은 보류 */
public record HomeResponse(String greetingName, List<Day> week, List<TimelineItem> timeline, List<Todo> todos,
                           Graduation graduation) {

    /** 주간일정 한 칸. categories 는 LECTURE·ASSIGNMENT·PERSONAL 순 */
    public record Day(LocalDate date, int dayOfWeek, boolean today, List<ScheduleCategory> categories) {
    }

    /** source: TIMETABLE(시간표 수업) / SCHEDULE(시간 있는 일정) */
    public record TimelineItem(String source, Long id, String title, ScheduleCategory category,
                               @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                               @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                               String location) {
    }

    public record Todo(Long id, String title, ScheduleCategory category, boolean done) {
    }

    public record Graduation(int earned, int required, double percent, int remaining) {
    }
}
