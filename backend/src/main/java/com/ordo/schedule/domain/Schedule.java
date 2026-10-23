package com.ordo.schedule.domain;

import com.ordo.global.common.BaseTimeEntity;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
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
    private Integer alarmMinutesBefore;  // null = 알림 없음. MVP는 저장만 한다

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
        this.alarmMinutesBefore = alarmMinutesBefore;
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
        if (alarmMinutesBefore != null) this.alarmMinutesBefore = alarmMinutesBefore;
        validate();
    }

    public void changeDone(boolean done) {
        this.done = done;
    }

    // 종료 시간이 있으면 시작 시간 필수 + 종료 > 시작. 알람은 null/10/30/60/1440 (명세 3.3)
    private void validate() {
        if (endTime != null && (startTime == null || !endTime.isAfter(startTime))) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        if (alarmMinutesBefore != null && !ALARM_MINUTES.contains(alarmMinutesBefore)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
