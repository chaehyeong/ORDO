package com.ordo.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordo.global.config.JpaConfig;
import com.ordo.notification.domain.PushSubscription;
import com.ordo.notification.repository.PushSubscriptionRepository;
import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.domain.ScheduleCategory;
import com.ordo.schedule.repository.ScheduleRepository;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/** 알림 후보 조회·선점 UPDATE 를 실제 DB(H2)로 확인 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import(JpaConfig.class)
class NotificationQueryTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 18);

    @Autowired ScheduleRepository scheduleRepository;
    @Autowired PushSubscriptionRepository pushSubscriptionRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;

    @Test
    void claimSucceedsOncePerNotifyAt() {
        Schedule schedule = scheduleRepository.save(schedule(user("a", true, true), "회의", DAY, 30));
        LocalDateTime notifyAt = LocalDateTime.of(2026, 10, 18, 8, 30);

        assertThat(scheduleRepository.claimAlarm(schedule.getId(), notifyAt)).isEqualTo(1);
        assertThat(scheduleRepository.claimAlarm(schedule.getId(), notifyAt)).isZero();          // 두 번째 실행·다른 서버
        assertThat(scheduleRepository.claimAlarm(schedule.getId(), notifyAt.plusHours(1))).isEqualTo(1);  // 시간을 바꾸면 다시
    }

    @Test
    void savingScheduleKeepsClaimedValue() {
        Schedule schedule = scheduleRepository.save(schedule(user("a", true, true), "회의", DAY, 30));
        LocalDateTime notifyAt = LocalDateTime.of(2026, 10, 18, 8, 30);
        em.flush();
        em.clear();
        Schedule loaded = scheduleRepository.findById(schedule.getId()).orElseThrow();  // 사용자가 수정하려고 읽음 (선점 전 값)

        scheduleRepository.claimAlarm(schedule.getId(), notifyAt);                       // 그 사이 스케줄러가 선점
        loaded.update("회의 (장소 변경)", null, null, null, null, "예406", null, null);
        scheduleRepository.flush();

        assertThat(scheduleRepository.claimAlarm(schedule.getId(), notifyAt)).isZero();  // 엔티티 저장이 선점 값을 덮지 않음
    }

    @Test
    void candidatesNeedAlarmUndoneNotificationOnAndDevice() {
        User ready = user("ready", true, true);
        User off = user("off", false, true);
        User noDevice = user("nodevice", true, false);
        scheduleRepository.save(schedule(ready, "대상", DAY, 30));
        scheduleRepository.save(schedule(ready, "알람 없음", DAY, null));
        Schedule done = scheduleRepository.save(schedule(ready, "완료", DAY, 30));
        done.changeDone(true);
        scheduleRepository.save(schedule(ready, "범위 밖", DAY.plusDays(2), 30));
        scheduleRepository.save(schedule(off, "알림 끔", DAY, 30));
        scheduleRepository.save(schedule(noDevice, "기기 없음", DAY, 30));
        em.flush();
        em.clear();

        assertThat(scheduleRepository.findAlarmCandidates(DAY.minusDays(1), DAY.plusDays(1)))
                .extracting(Schedule::getTitle).containsExactly("대상");
        assertThat(scheduleRepository.findAlarmCandidates(DAY.minusDays(1), DAY.plusDays(1)).get(0).getUser().getTodoAlarmTime())
                .isEqualTo(LocalTime.of(9, 0));  // 사용자를 함께 읽어 트랜잭션 밖에서도 쓸 수 있음
    }

    private User user(String tag, boolean notificationEnabled, boolean withDevice) {
        User user = User.builder().email(tag + "@khu.ac.kr").password("encoded").name(tag).build();
        user.changeSettings(notificationEnabled, null);
        userRepository.save(user);
        if (withDevice) {
            pushSubscriptionRepository.save(PushSubscription.builder().user(user)
                    .endpoint("https://fcm.googleapis.com/fcm/send/" + tag).p256dh("p").auth("a").build());
        }
        return user;
    }

    private static Schedule schedule(User user, String title, LocalDate date, Integer alarm) {
        return Schedule.builder().user(user).title(title).category(ScheduleCategory.PERSONAL).scheduleDate(date)
                .startTime(LocalTime.of(9, 0)).alarmMinutesBefore(alarm).build();
    }
}
