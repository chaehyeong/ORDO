package com.ordo.notification.service;

import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.repository.ScheduleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1분마다 알림 보낼 일정을 찾아 발송한다 (명세 4.10).
 * 발송 시각 = 날짜 + (시작시간 ?? 사용자의 할 일 알림 시각) − alarm_minutes_before, 한국 시간 기준.
 * 10분보다 늦은 알림은 버리고(서버가 꺼졌다 켜졌을 때 몰아서 가지 않게, 9장 14), 선점 UPDATE 로 한 번만 보낸다
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    static final Duration LATE_LIMIT = Duration.ofMinutes(10);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ScheduleRepository scheduleRepository;
    private final PushSubscriptionService pushSubscriptionService;
    private final WebPushClient webPushClient;
    Clock clock = Clock.systemUTC();  // 테스트에서 시간을 옮기려고 패키지 범위로 둠

    // 푸시 서비스에 HTTP 요청하는 동안 DB 연결을 잡지 않도록 트랜잭션 밖에서 실행 (조회·선점은 각자 짧은 트랜잭션)
    @Scheduled(fixedDelay = 60_000)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendDue() {
        if (webPushClient.isConfigured()) {
            sendDue(LocalDateTime.ofInstant(clock.instant(), SEOUL).truncatedTo(ChronoUnit.MINUTES));  // JVM·시계 시간대와 무관
        }
    }

    /** now − 10분 < 발송 시각 ≤ now 인 알림을 보내고, 이번에 선점한 수를 돌려준다 */
    int sendDue(LocalDateTime now) {
        LocalDateTime earliest = now.minus(LATE_LIMIT);
        LocalDate today = now.toLocalDate();
        int claimed = 0;
        // 발송 시각은 일정 날짜의 전날 00:00 ~ 그날 23:59 사이라 어제~내일 일정만 보면 된다
        for (Schedule schedule : scheduleRepository.findAlarmCandidates(today.minusDays(1), today.plusDays(1))) {
            LocalDateTime notifyAt = notifyAt(schedule);
            if (!notifyAt.isAfter(earliest) || notifyAt.isAfter(now)
                    || scheduleRepository.claimAlarm(schedule.getId(), notifyAt) == 0) {
                continue;
            }
            claimed++;
            try {
                pushSubscriptionService.sendToUser(schedule.getUser().getId(), NotificationMessage.of(schedule));
            } catch (RuntimeException e) {
                log.warn("일정 알림 발송 오류 scheduleId={} ({})", schedule.getId(), e.getClass().getSimpleName());
            }
        }
        return claimed;
    }

    static LocalDateTime notifyAt(Schedule schedule) {
        LocalTime base = schedule.getStartTime() != null ? schedule.getStartTime() : schedule.getUser().getTodoAlarmTime();
        return LocalDateTime.of(schedule.getScheduleDate(), base).minusMinutes(schedule.getAlarmMinutesBefore());
    }
}
