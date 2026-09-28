package com.ordo.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.domain.ScheduleCategory;
import com.ordo.schedule.dto.ScheduleCreateRequest;
import com.ordo.schedule.dto.ScheduleResponse;
import com.ordo.schedule.dto.ScheduleUpdateRequest;
import com.ordo.schedule.repository.ScheduleRepository;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 16);

    @Mock
    ScheduleRepository scheduleRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    ScheduleService scheduleService;

    @Test
    void createWithoutTimeIsTodo() {
        given(userRepository.getReferenceById(1L)).willReturn(user());
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(1L, new ScheduleCreateRequest(
                "디자인씽킹 과제 제출", ScheduleCategory.ASSIGNMENT, DAY, null, null, null, "PDF로 제출", 1440));

        assertThat(response.type()).isEqualTo("TODO");
        assertThat(response.done()).isFalse();
        assertThat(response.alarmMinutesBefore()).isEqualTo(1440);
    }

    @Test
    void createWithStartTimeIsEvent() {
        given(userRepository.getReferenceById(1L)).willReturn(user());
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(1L, createRequest(at(9), null, null));

        assertThat(response.type()).isEqualTo("EVENT");
    }

    @Test
    void endTimeMustBeAfterStartTime() {
        given(userRepository.getReferenceById(1L)).willReturn(user());

        assertErrorCode(() -> scheduleService.create(1L, createRequest(at(9), at(9), null)),
                ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void endTimeWithoutStartTimeFails() {
        given(userRepository.getReferenceById(1L)).willReturn(user());

        assertErrorCode(() -> scheduleService.create(1L, createRequest(null, at(10), null)),
                ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void unsupportedAlarmMinutesFails() {
        given(userRepository.getReferenceById(1L)).willReturn(user());

        assertErrorCode(() -> scheduleService.create(1L, createRequest(null, null, 15)), ErrorCode.INVALID_INPUT);
    }

    @Test
    void othersScheduleIsNotFound() {
        given(scheduleRepository.findByIdAndUserId(42L, 2L)).willReturn(Optional.empty());

        assertErrorCode(() -> scheduleService.getSchedule(2L, 42L), ErrorCode.SCHEDULE_NOT_FOUND);
        assertErrorCode(() -> scheduleService.delete(2L, 42L), ErrorCode.SCHEDULE_NOT_FOUND);
        verify(scheduleRepository, never()).delete(any());
    }

    @Test
    void periodIsSortedByDateThenTimeWithTodosLast() {
        given(scheduleRepository.findInPeriod(1L, DAY.minusDays(1), DAY, null)).willReturn(List.of(
                schedule("할 일", DAY, null, null),
                schedule("오후 수업", DAY, at(14), at(15)),
                schedule("오전 수업", DAY, at(9), null),
                schedule("어제 일정", DAY.minusDays(1), at(18), null)));

        List<ScheduleResponse> responses = scheduleService.getSchedules(1L, DAY.minusDays(1), DAY, null);

        assertThat(responses).extracting(ScheduleResponse::title)
                .containsExactly("어제 일정", "오전 수업", "오후 수업", "할 일");
    }

    @Test
    void periodMustBeInOrderAndAtMost62Days() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 12, 1);  // 양 끝 포함 62일
        given(scheduleRepository.findInPeriod(1L, from, to, null)).willReturn(List.of());

        assertThat(scheduleService.getSchedules(1L, from, to, null)).isEmpty();
        assertErrorCode(() -> scheduleService.getSchedules(1L, from, to.plusDays(1), null),
                ErrorCode.INVALID_TIME_RANGE);
        assertErrorCode(() -> scheduleService.getSchedules(1L, to, from, null), ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void updateChangesOnlySentFields() {
        Schedule schedule = schedule("팀플 모임", DAY, at(12), at(13));
        given(scheduleRepository.findByIdAndUserId(51L, 1L)).willReturn(Optional.of(schedule));

        scheduleService.update(1L, 51L, new ScheduleUpdateRequest(null, null, null, null, at(14), "예405", null, null));

        assertThat(schedule.getTitle()).isEqualTo("팀플 모임");
        assertThat(schedule.getStartTime()).isEqualTo(at(12));
        assertThat(schedule.getEndTime()).isEqualTo(at(14));
        assertThat(schedule.getLocation()).isEqualTo("예405");
    }

    @Test
    void updateValidatesMergedTimes() {
        Schedule schedule = schedule("팀플 모임", DAY, at(12), at(13));
        given(scheduleRepository.findByIdAndUserId(51L, 1L)).willReturn(Optional.of(schedule));

        assertErrorCode(() -> scheduleService.update(1L, 51L,
                new ScheduleUpdateRequest(null, null, null, null, at(11), null, null, null)),
                ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void changeDoneSetsSentValue() {
        Schedule schedule = schedule("자료 조사", DAY, null, null);
        given(scheduleRepository.findByIdAndUserId(60L, 1L)).willReturn(Optional.of(schedule));

        assertThat(scheduleService.changeDone(1L, 60L, true).done()).isTrue();
        assertThat(scheduleService.changeDone(1L, 60L, true).done()).isTrue();
    }

    private void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    private ScheduleCreateRequest createRequest(LocalTime startTime, LocalTime endTime, Integer alarmMinutesBefore) {
        return new ScheduleCreateRequest("세시 팀플 모임", ScheduleCategory.PERSONAL, DAY, startTime, endTime,
                null, null, alarmMinutesBefore);
    }

    private Schedule schedule(String title, LocalDate date, LocalTime startTime, LocalTime endTime) {
        return Schedule.builder().user(user()).title(title).category(ScheduleCategory.PERSONAL)
                .scheduleDate(date).startTime(startTime).endTime(endTime).build();
    }

    private User user() {
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은").build();
    }

    private static LocalTime at(int hour) {
        return LocalTime.of(hour, 0);
    }
}
