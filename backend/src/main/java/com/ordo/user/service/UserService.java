package com.ordo.user.service;

import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.dto.UserResponse;
import com.ordo.user.dto.UserSettingsRequest;
import com.ordo.user.dto.UserUpdateRequest;
import com.ordo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final MajorRepository majorRepository;

    public UserResponse getMe(Long userId) {
        return UserResponse.from(getUser(userId));
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
