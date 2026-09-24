package com.ordo.user.dto;

import com.ordo.catalog.domain.Major;
import com.ordo.user.domain.User;

public record UserResponse(Long id, String email, String name, String nickname, String studentNumber, String phone,
                           String profileImageUrl, Integer admissionYear, Integer currentSemester, Integer grade,
                           MajorInfo major, boolean notificationEnabled) {

    public record MajorInfo(Long id, String displayName, String collegeName) {
    }

    public static UserResponse from(User user) {
        Major major = user.getMajor();
        Integer semester = user.getCurrentSemester();
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getNickname(),
                user.getStudentNumber(), user.getPhone(), user.getProfileImageUrl(), user.getAdmissionYear(),
                semester,
                semester == null ? null : (semester + 1) / 2,  // 학년 (명세 3.3)
                major == null ? null : new MajorInfo(major.getId(), major.getDisplayName(), major.getCollege().getName()),
                user.isNotificationEnabled());
    }
}
