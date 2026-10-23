package com.ordo.user.service;

import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.service.AcademicProgressService;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.global.common.AcademicTerm;
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
import com.ordo.user.dto.UserSummaryResponse.Credits;
import com.ordo.user.dto.UserSummaryResponse.TermInfo;
import com.ordo.user.dto.UserUpdateRequest;
import com.ordo.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final MajorRepository majorRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final ScheduleRepository scheduleRepository;
    private final AcademicProgressService academicProgressService;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(getUser(userId));
    }

    /** 마이페이지 요약: 이번 학기 시간표의 서로 다른 과목 수, 오늘 이후 완료 안 한 과제 일정 수, 학점 (명세 4.2) */
    public UserSummaryResponse getSummary(Long userId) {
        AcademicTerm term = AcademicTerm.now();
        long courseCount = timetableEntryRepository
                .findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(userId, term.year(), term.term())
                .stream().map(TimetableEntry::getCourseName).distinct().count();
        long pendingAssignmentCount = scheduleRepository.countByUserIdAndCategoryAndDoneFalseAndScheduleDateGreaterThanEqual(
                userId, ScheduleCategory.ASSIGNMENT, LocalDate.now(ZoneId.of("Asia/Seoul")));
        Credits credits = academicProgressService.findSummary(userId).map(ProgressSummary::total)
                .map(t -> new Credits(t.earned(), t.required(), t.percent()))
                .orElse(null);
        return new UserSummaryResponse(new TermInfo(term.year(), term.term(), term.label()),
                (int) courseCount, (int) pendingAssignmentCount, credits);
    }

    @Transactional
    public UserResponse updateMe(Long userId, UserUpdateRequest request) {
        User user = getUser(userId);
        user.updateProfile(request.name(), request.nickname(), request.studentNumber(), request.phone(),
                request.admissionYear(), resolveMajor(user, request), request.currentSemester(), request.profileImageUrl());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateSettings(Long userId, UserSettingsRequest request) {
        User user = getUser(userId);
        user.changeNotificationEnabled(request.notificationEnabled());
        return UserResponse.from(user);
    }

    // 전공·입학년도 중 하나라도 바뀌면 그 조합을 가입 때와 같은 규칙으로 다시 확인
    // ponytail: 이메일 가입자는 전공·입학년도가 항상 있음. 카카오 로그인(값 없는 사용자) 도입 시 null 처리 추가
    private Major resolveMajor(User user, UserUpdateRequest request) {
        if (request.majorId() == null && request.admissionYear() == null) {
            return null;
        }
        Long majorId = request.majorId() != null ? request.majorId() : user.getMajor().getId();
        int admissionYear = request.admissionYear() != null ? request.admissionYear() : user.getAdmissionYear();
        return majorRepository.findSelectable(majorId, admissionYear)
                .orElseThrow(() -> new BusinessException(ErrorCode.MAJOR_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
