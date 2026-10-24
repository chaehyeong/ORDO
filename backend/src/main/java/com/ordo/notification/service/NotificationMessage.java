package com.ordo.notification.service;

import com.ordo.schedule.domain.Schedule;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 기기의 서비스워커가 받는 알림 내용(암호화되어 전달). 제목·시간·장소만 넣고 메모는 넣지 않는다.
 * tag 가 같으면 같은 일정의 이전 알림을 덮어쓰고, 누르면 url(오르도 홈)이 열린다
 */
public record NotificationMessage(String title, String body, String tag, String url) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** 예: "10월 18일 (일) 09:00 · 예405", 할 일이면 "10월 18일 (일) 할 일" */
    public static NotificationMessage of(Schedule schedule) {
        LocalDate date = schedule.getScheduleDate();
        String day = date.getMonthValue() + "월 " + date.getDayOfMonth() + "일 ("
                + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
        String when = schedule.getStartTime() != null ? day + " " + TIME.format(schedule.getStartTime()) : day + " 할 일";
        String location = schedule.getLocation();
        String body = location != null && !location.isBlank() ? when + " · " + location : when;
        return new NotificationMessage(schedule.getTitle(), body, "schedule-" + schedule.getId(), "/");
    }

    public static NotificationMessage test() {
        return new NotificationMessage("오르도 알림 테스트", "이 기기에서 일정 알림을 받을 수 있어요.", "test", "/");
    }
}
