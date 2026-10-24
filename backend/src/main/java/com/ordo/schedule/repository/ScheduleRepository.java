package com.ordo.schedule.repository;

import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.domain.ScheduleCategory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByIdAndUserId(Long id, Long userId);

    /** 마이페이지 '남은 과제 수': from 이후 완료 안 한 그 카테고리 일정 수 */
    long countByUserIdAndCategoryAndDoneFalseAndScheduleDateGreaterThanEqual(
            Long userId, ScheduleCategory category, LocalDate from);

    /** category 가 null 이면 전체. 화면 정렬은 서비스에서 한다 */
    @Query("""
            SELECT s FROM Schedule s
            WHERE s.user.id = :userId AND s.scheduleDate BETWEEN :from AND :to
              AND (:category IS NULL OR s.category = :category)
            ORDER BY s.id
            """)
    List<Schedule> findInPeriod(Long userId, LocalDate from, LocalDate to, ScheduleCategory category);

    /** 알림 후보 (T11): 알람 있음 · 미완료 · 알림을 켠 사용자 · 등록한 기기 1개 이상, 날짜 범위. 사용자도 함께 읽는다 */
    @Query("""
            SELECT s FROM Schedule s JOIN FETCH s.user u
            WHERE s.alarmMinutesBefore IS NOT NULL AND s.done = false
              AND s.scheduleDate BETWEEN :from AND :to
              AND u.notificationEnabled = true
              AND EXISTS (SELECT p.id FROM PushSubscription p WHERE p.user = u)
            """)
    List<Schedule> findAlarmCandidates(LocalDate from, LocalDate to);

    /** 발송 선점 (T11): 1 이면 내가 보낼 차례, 0 이면 이미 처리됨. 서버가 여러 대여도 같은 알림은 한 번만 나간다 */
    @Transactional
    @Modifying
    @Query("""
            UPDATE Schedule s SET s.alarmSentFor = :notifyAt
            WHERE s.id = :id AND (s.alarmSentFor IS NULL OR s.alarmSentFor <> :notifyAt)
            """)
    int claimAlarm(Long id, LocalDateTime notifyAt);
}
