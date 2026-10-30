package com.ordo.home.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ordo.schedule.domain.ScheduleCategory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 홈 화면 한 번에 (명세 4.7). graduation 은 학사 프로필 미완성이면 null. AI 추천은 보류 */
public record HomeResponse(String greetingName, List<Day> week, List<TimelineItem> timeline, List<Todo> todos,
                           List<Deadline> deadlines, Graduation graduation) {

    /** 주간일정 한 칸. categories 는 LECTURE·ASSIGNMENT·PERSONAL 순 */
    public record Day(LocalDate date, int dayOfWeek, boolean today, List<ScheduleCategory> categories) {
    }

    /**
     * source: TIMETABLE(시간표 수업) / SCHEDULE(시간 있는 일정) / ECAMPUS(e캠퍼스 과제 마감, startTime = 마감 시각).
     * ECAMPUS 는 id·endTime·location 이 null 이고 courseName·url 은 ECAMPUS 에만 값이 있다
     */
    public record TimelineItem(String source, Long id, String title, ScheduleCategory category,
                               @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                               @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                               String location, String courseName, String url) {
    }

    public record Todo(Long id, String title, ScheduleCategory category, boolean done) {
    }

    /**
     * D-Day 알림 한 줄 (T16). source: SCHEDULE(직접 등록한 일정) / ECAMPUS(e캠퍼스 과제 마감).
     * dday 는 기준일부터 며칠 남았는지(0 = 오늘). time 은 시간 없는 일정이면 null, 시각 없는 e캠퍼스 마감은 23:59.
     * courseName·url 은 ECAMPUS 에만 값이 있고, id 는 ECAMPUS 면 null
     */
    public record Deadline(String source, Long id, String title, ScheduleCategory category, LocalDate date,
                           @JsonFormat(pattern = "HH:mm") LocalTime time, int dday, String courseName, String url) {
    }

    public record Graduation(int earned, int required, double percent, int remaining) {
    }
}
