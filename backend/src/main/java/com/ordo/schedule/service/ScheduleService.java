package com.ordo.schedule.service;

import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.domain.ScheduleCategory;
import com.ordo.schedule.dto.ScheduleCreateRequest;
import com.ordo.schedule.dto.ScheduleResponse;
import com.ordo.schedule.dto.ScheduleUpdateRequest;
import com.ordo.schedule.repository.ScheduleRepository;
import com.ordo.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private static final int MAX_PERIOD_DAYS = 62;  // 양 끝 포함 (예: 10-01 ~ 12-01)

    // 날짜 → 시작시간 순, 시간 없는 할 일은 그날 맨 뒤 (명세 4.4)
    private static final Comparator<Schedule> DISPLAY_ORDER = Comparator.comparing(Schedule::getScheduleDate)
            .thenComparing(Schedule::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    public List<ScheduleResponse> getSchedules(Long userId, LocalDate from, LocalDate to, ScheduleCategory category) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        return scheduleRepository.findInPeriod(userId, from, to, category).stream()
                .sorted(DISPLAY_ORDER)
                .map(ScheduleResponse::from)
                .toList();
    }

    public ScheduleResponse getSchedule(Long userId, Long scheduleId) {
        return ScheduleResponse.from(getOwnSchedule(userId, scheduleId));
    }

    @Transactional
    public ScheduleResponse create(Long userId, ScheduleCreateRequest request) {
        Schedule schedule = Schedule.builder()
                .user(userRepository.getReferenceById(userId))
                .title(request.title())
                .category(request.category())
                .scheduleDate(request.date())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .location(request.location())
                .memo(request.memo())
                .alarmMinutesBefore(request.alarmMinutesBefore())
                .build();
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleResponse update(Long userId, Long scheduleId, ScheduleUpdateRequest request) {
        Schedule schedule = getOwnSchedule(userId, scheduleId);
        schedule.update(request.title(), request.category(), request.date(), request.startTime(), request.endTime(),
                request.location(), request.memo(), request.alarmMinutesBefore());
        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public ScheduleResponse changeDone(Long userId, Long scheduleId, boolean done) {
        Schedule schedule = getOwnSchedule(userId, scheduleId);
        schedule.changeDone(done);
        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public void delete(Long userId, Long scheduleId) {
        scheduleRepository.delete(getOwnSchedule(userId, scheduleId));
    }

    // 남의 일정도 "없음"으로 응답해 존재 여부를 노출하지 않는다
    private Schedule getOwnSchedule(Long userId, Long scheduleId) {
        return scheduleRepository.findByIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }
}
