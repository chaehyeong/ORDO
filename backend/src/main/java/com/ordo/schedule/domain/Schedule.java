package com.ordo.schedule.domain;

import com.ordo.global.common.BaseTimeEntity;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 일정과 할 일을 함께 저장한다. start_time 이 있으면 일정, 없으면 할 일 (명세 3.3) */
@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseTimeEntity {

    private static final Set<Integer> ALARM_MINUTES = Set.of(10, 30, 60, 1440);
    private static final int ALARM_OFF = 0;  // PATCH 에서 알람 끄기 (null 은 '그대로'라서 따로 둔다). 저장은 null
    private static final int MIN_YEAR = 2000;  // 시간표·이수내역과 같은 범위. 아주 옛날 날짜는 DB 에 다른 날짜로 저장된다
    private static final int MAX_YEAR = 2100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String title;

    @Enumerated(EnumType.STRING)
    private ScheduleCategory category;

    private LocalDate scheduleDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private String memo;
    private boolean done;
    private Integer alarmMinutesBefore;  // null = 알림 없음. T11 웹푸시로 발송

    // 알림을 처리한 발송 예정 시각 (T11 중복 방지). ScheduleRepository.claimAlarm 만 쓰고, 엔티티 저장 때는 건드리지 않는다
    @Column(insertable = false, updatable = false)
    private LocalDateTime alarmSentFor;

    @Builder
    private Schedule(User user, String title, ScheduleCategory category, LocalDate scheduleDate, LocalTime startTime,
                     LocalTime endTime, String location, String memo, Integer alarmMinutesBefore) {
        this.user = user;
        this.title = title;
        this.category = category;
        this.scheduleDate = scheduleDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.memo = memo;
        this.alarmMinutesBefore = offToNull(alarmMinutesBefore);
        validate();
    }

    /** PATCH: null 인 값은 그대로 둔다. 바꾼 뒤의 값으로 다시 검증 */
    public void update(String title, ScheduleCategory category, LocalDate scheduleDate, LocalTime startTime,
                       LocalTime endTime, String location, String memo, Integer alarmMinutesBefore) {
        if (title != null) this.title = title;
        if (category != null) this.category = category;
        if (scheduleDate != null) this.scheduleDate = scheduleDate;
        if (startTime != null) this.startTime = startTime;
        if (endTime != null) this.endTime = endTime;
        if (location != null) this.location = location;
        if (memo != null) this.memo = memo;
        if (alarmMinutesBefore != null) this.alarmMinutesBefore = offToNull(alarmMinutesBefore);
        validate();
    }

    public void changeDone(boolean done) {
        this.done = done;
    }

    private static Integer offToNull(Integer alarmMinutesBefore) {
        return alarmMinutesBefore != null && alarmMinutesBefore == ALARM_OFF ? null : alarmMinutesBefore;
    }

    // 종료 시간이 있으면 시작 시간 필수 + 종료 > 시작. 알람은 null/10/30/60/1440 (명세 3.3)
    private void validate() {
        if (scheduleDate != null && (scheduleDate.getYear() < MIN_YEAR || scheduleDate.getYear() > MAX_YEAR)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (endTime != null && (startTime == null || !endTime.isAfter(startTime))) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        if (alarmMinutesBefore != null && !ALARM_MINUTES.contains(alarmMinutesBefore)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
