package com.ordo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.domain.ScheduleCategory;
import com.ordo.schedule.repository.ScheduleRepository;
import com.ordo.user.domain.User;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 18);  // 일요일
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 8, 30);

    @Mock
    ScheduleRepository scheduleRepository;
    @Mock
    PushSubscriptionService pushSubscriptionService;
    @Mock
    WebPushClient webPushClient;
    @InjectMocks
    NotificationService notificationService;

    @Test
    void notifyAtUsesStartTimeOrUsersTodoAlarmTime() {
        User morning = user(1L);
        User night = user(2L);
        night.changeSettings(null, LocalTime.of(21, 0));

        assertThat(NotificationService.notifyAt(schedule(1L, morning, DAY, LocalTime.of(9, 0), 30)))
                .isEqualTo(LocalDateTime.of(2026, 10, 18, 8, 30));
        assertThat(NotificationService.notifyAt(schedule(2L, morning, DAY, null, 1440)))  // 할 일 하루 전 = 전날 09:00
                .isEqualTo(LocalDateTime.of(2026, 10, 17, 9, 0));
        assertThat(NotificationService.notifyAt(schedule(3L, morning, DAY, LocalTime.of(0, 10), 30)))
                .isEqualTo(LocalDateTime.of(2026, 10, 17, 23, 40));
        assertThat(NotificationService.notifyAt(schedule(4L, night, DAY, null, 60)))     // 사용자 설정 21:00
                .isEqualTo(LocalDateTime.of(2026, 10, 18, 20, 0));
        assertThat(NotificationService.notifyAt(schedule(5L, night, DAY, LocalTime.of(14, 0), 10)))  // 시간 있으면 설정 무관
                .isEqualTo(LocalDateTime.of(2026, 10, 18, 13, 50));
    }

    @Test
    void sendsOnlyAlarmsDueWithinLastTenMinutes() {
        User user = user(1L);
        Schedule due = schedule(1L, user, DAY, LocalTime.of(9, 0), 30);             // 08:30 = 지금
        Schedule slightlyLate = schedule(2L, user, DAY, LocalTime.of(8, 31), 10);    // 08:21 (9분 늦음)
        Schedule tooLate = schedule(3L, user, DAY, LocalTime.of(8, 30), 10);         // 08:20 (10분 늦음 → 버림)
        Schedule future = schedule(4L, user, DAY, LocalTime.of(9, 1), 30);           // 08:31
        given(scheduleRepository.findAlarmCandidates(DAY.minusDays(1), DAY.plusDays(1)))
                .willReturn(List.of(due, slightlyLate, tooLate, future));
        given(scheduleRepository.claimAlarm(anyLong(), any())).willReturn(1);

        assertThat(notificationService.sendDue(NOW)).isEqualTo(2);

        then(scheduleRepository).should().claimAlarm(1L, LocalDateTime.of(2026, 10, 18, 8, 30));
        then(scheduleRepository).should().claimAlarm(2L, LocalDateTime.of(2026, 10, 18, 8, 21));
        then(scheduleRepository).should(never()).claimAlarm(eq(3L), any());
        then(scheduleRepository).should(never()).claimAlarm(eq(4L), any());
        then(pushSubscriptionService).should().sendToUser(1L, NotificationMessage.of(due));
        then(pushSubscriptionService).should().sendToUser(1L, NotificationMessage.of(slightlyLate));
    }

    @Test
    void alreadyClaimedAlarmIsNotSentAgain() {
        given(scheduleRepository.findAlarmCandidates(any(), any()))
                .willReturn(List.of(schedule(1L, user(1L), DAY, LocalTime.of(9, 0), 30)));
        given(scheduleRepository.claimAlarm(anyLong(), any())).willReturn(0);  // 다른 서버·이전 실행이 처리함

        assertThat(notificationService.sendDue(NOW)).isZero();

        then(pushSubscriptionService).should(never()).sendToUser(anyLong(), any());
    }

    @Test
    void oneFailureDoesNotStopOthers() {
        User first = user(1L);
        User second = user(2L);
        given(scheduleRepository.findAlarmCandidates(any(), any())).willReturn(List.of(
                schedule(1L, first, DAY, LocalTime.of(9, 0), 30), schedule(2L, second, DAY, LocalTime.of(9, 0), 30)));
        given(scheduleRepository.claimAlarm(anyLong(), any())).willReturn(1);
        given(pushSubscriptionService.sendToUser(eq(1L), any())).willThrow(new IllegalStateException("boom"));

        assertThat(notificationService.sendDue(NOW)).isEqualTo(2);

        then(pushSubscriptionService).should().sendToUser(eq(2L), any());
    }

    @Test
    void scheduledRunUsesSeoulTimeAndSkipsWithoutKeys() {
        notificationService.clock = Clock.fixed(ZonedDateTime.of(NOW.plusSeconds(42), ZoneId.of("Asia/Seoul")).toInstant(),
                ZoneId.of("UTC"));
        given(webPushClient.isConfigured()).willReturn(true);
        given(scheduleRepository.findAlarmCandidates(any(), any())).willReturn(List.of());

        notificationService.sendDue();  // 시계는 UTC 지만 NotificationService 는 한국 시간으로 계산해야 함

        then(scheduleRepository).should().findAlarmCandidates(DAY.minusDays(1), DAY.plusDays(1));

        given(webPushClient.isConfigured()).willReturn(false);
        notificationService.sendDue();
        then(scheduleRepository).shouldHaveNoMoreInteractions();
    }

    private static User user(Long id) {
        User user = User.builder().email("u" + id + "@khu.ac.kr").password("encoded").name("사용자" + id).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Schedule schedule(Long id, User user, LocalDate date, LocalTime start, Integer alarm) {
        Schedule schedule = Schedule.builder().user(user).title("일정" + id).category(ScheduleCategory.PERSONAL)
                .scheduleDate(date).startTime(start).alarmMinutesBefore(alarm).build();
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }
}
