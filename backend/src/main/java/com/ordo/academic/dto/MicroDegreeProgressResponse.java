package com.ordo.academic.dto;

import java.util.List;

/**
 * 내 마이크로디그리 진행 상황 (명세 4.6, T20). earned·earnedCourses 는 required·requiredCourses 를 넘지 않는다.
 * unmet: 아직 못 채운 조건(REQUIRED_COURSE, COURSE_COUNT, GROUP_MINIMUM, DEPARTMENT_COUNT). 비어 있으면 completed 다.
 * period: 센 과목의 첫 학기~마지막 학기, 없으면 null
 */
public record MicroDegreeProgressResponse(Long id, String name, String hostDepartment, boolean selective, String guideUrl,
                                          int earned, int required, int earnedCourses, int requiredCourses,
                                          boolean completed, List<String> unmet, String period, List<CourseItem> courses) {

    /** counted 가 false 면 그룹 인정 한도를 넘었거나 먼저 등록한 MD 에 이미 배정된 과목이다 */
    public record CourseItem(Long id, String courseName, String courseCode, int credits, String grade, boolean counted) {
    }
}
