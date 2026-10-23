package com.ordo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.domain.ScheduleCategory;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationMessageTest {

    @Test
    void containsTitleTimeAndLocationButNeverMemo() {
        Schedule schedule = Schedule.builder().title("디자인씽킹 과제 제출").category(ScheduleCategory.ASSIGNMENT)
                .scheduleDate(LocalDate.of(2026, 10, 18)).startTime(LocalTime.of(9, 0)).location("예405")
                .memo("비밀 메모: 조원 연락처 010-1234-5678").alarmMinutesBefore(30).build();
        ReflectionTestUtils.setField(schedule, "id", 42L);

        NotificationMessage message = NotificationMessage.of(schedule);

        assertThat(message).isEqualTo(new NotificationMessage("디자인씽킹 과제 제출", "10월 18일 (일) 09:00 · 예405", "schedule-42", "/"));
        assertThat(message.toString()).doesNotContain("메모").doesNotContain("010-1234-5678");
    }

    @Test
    void todoWithoutLocation() {
        Schedule todo = Schedule.builder().title("보고서").category(ScheduleCategory.ASSIGNMENT)
                .scheduleDate(LocalDate.of(2026, 10, 19)).location(" ").alarmMinutesBefore(1440).build();

        assertThat(NotificationMessage.of(todo).body()).isEqualTo("10월 19일 (월) 할 일");
    }
}
