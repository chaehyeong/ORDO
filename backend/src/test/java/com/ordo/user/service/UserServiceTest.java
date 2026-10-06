package com.ordo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.dto.ProgressSummary.Total;
import com.ordo.academic.service.AcademicProgressService;
import com.ordo.catalog.domain.College;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.global.common.AcademicTerm;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.schedule.domain.ScheduleCategory;
import com.ordo.schedule.repository.ScheduleRepository;
import com.ordo.timetable.domain.TimetableEntry;
import com.ordo.timetable.repository.TimetableEntryRepository;
import com.ordo.user.domain.User;
import com.ordo.user.dto.UserResponse;
import com.ordo.user.dto.UserSettingsRequest;
import com.ordo.user.dto.UserSummaryResponse;
import com.ordo.user.dto.UserUpdateRequest;
import com.ordo.user.repository.UserRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    MajorRepository majorRepository;
    @Mock
    TimetableEntryRepository timetableEntryRepository;
    @Mock
    ScheduleRepository scheduleRepository;
    @Mock
    AcademicProgressService academicProgressService;
    @InjectMocks
    UserService userService;

    @Test
    void summaryCountsDistinctCoursesPendingAssignmentsAndCredits() {
        AcademicTerm term = AcademicTerm.now();
        given(timetableEntryRepository.findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(
                1L, term.year(), term.term())).willReturn(List.of(entry("자료구조", 1), entry("자료구조", 3), entry("색채학", 2)));
        given(scheduleRepository.countByUserIdAndCategoryAndDoneFalseAndScheduleDateGreaterThanEqual(
                eq(1L), eq(ScheduleCategory.ASSIGNMENT), any())).willReturn(3L);
        given(academicProgressService.findSummary(1L)).willReturn(Optional.of(new ProgressSummary(2026, null,
                new Total(18, 130, 112, 13.8), List.of(), List.of(), List.of(), List.of(), false, false)));

        UserSummaryResponse summary = userService.getSummary(1L);

        assertThat(summary.term()).isEqualTo(new UserSummaryResponse.TermInfo(term.year(), term.term(), term.label()));
        assertThat(summary.courseCount()).isEqualTo(2);  // 같은 과목 여러 칸은 1개
        assertThat(summary.pendingAssignmentCount()).isEqualTo(3);
        assertThat(summary.credits()).isEqualTo(new UserSummaryResponse.Credits(18, 130, 13.8));
    }

    @Test
    void summaryCreditsAreNullWithoutAcademicProfile() {
        given(academicProgressService.findSummary(1L)).willReturn(Optional.empty());

        assertThat(userService.getSummary(1L).credits()).isNull();
    }

    private static TimetableEntry entry(String courseName, int dayOfWeek) {
        return TimetableEntry.builder().academicYear(2026).term(Term.SECOND).courseName(courseName).dayOfWeek(dayOfWeek)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 30)).build();
    }

    @Test
    void getMeCalculatesGradeAndMajorInfo() {
        Major major = major(35L, "시각디자인학과", "예술·디자인대학");
        given(userRepository.findById(1L)).willReturn(Optional.of(user(major, 3)));

        UserResponse response = userService.getMe(1L);

        assertThat(response.grade()).isEqualTo(2);  // (3 + 1) / 2
        assertThat(response.major().displayName()).isEqualTo("시각디자인학과");
        assertThat(response.major().collegeName()).isEqualTo("예술·디자인대학");
        assertThat(response.notificationEnabled()).isTrue();
    }

    @Test
    void getMeWithoutSemesterHasNullGrade() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(null, null)));

        assertThat(userService.getMe(1L).grade()).isNull();
    }

    @Test
    void updateMeChangesOnlySentFields() {
        User user = user(null, 1);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.updateMe(1L, new UserUpdateRequest(null, "하은", null, null, null, null, null, null));

        assertThat(user.getNickname()).isEqualTo("하은");
        assertThat(user.getName()).isEqualTo("이하은");
        assertThat(user.getPhone()).isEqualTo("010-1234-5678");
        assertThat(user.getCurrentSemester()).isEqualTo(1);
        verify(majorRepository, never()).findSelectable(anyLong(), anyInt());
    }

    @Test
    void updateMeWithMajorNotOfferedForAdmissionYearFails() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(null, 1)));
        given(majorRepository.findSelectable(99L, 2026)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMe(1L,
                new UserUpdateRequest(null, null, null, null, null, 99L, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MAJOR_NOT_FOUND);
    }

    @Test
    void updateSettingsTurnsNotificationOff() {
        User user = user(null, 1);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userService.updateSettings(1L, new UserSettingsRequest(false));

        assertThat(user.isNotificationEnabled()).isFalse();
        assertThat(response.notificationEnabled()).isFalse();
    }

    @Test
    void unknownUserIsNotFound() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    private User user(Major major, Integer currentSemester) {
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은")
                .phone("010-1234-5678").studentNumber("2026105632").admissionYear(2026)
                .major(major).currentSemester(currentSemester).build();
    }

    private Major major(Long id, String displayName, String collegeName) {
        College college = mock(College.class);
        given(college.getName()).willReturn(collegeName);
        Major major = mock(Major.class);
        given(major.getId()).willReturn(id);
        given(major.getDisplayName()).willReturn(displayName);
        given(major.getCollege()).willReturn(college);
        return major;
    }
}
