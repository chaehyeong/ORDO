package com.ordo.catalog.dto;

import com.ordo.catalog.domain.MicroDegree;
import com.ordo.catalog.domain.MicroDegreeCourse;
import java.util.List;

/** 마이크로디그리 목록 (명세 4.3, T20). 판정은 requiredCourseCount 기준이고 requiredCredits 는 표시용이다 */
public record MicroDegreeResponse(Long id, String name, String scope, String collegeName, String hostDepartment,
                                  int requiredCourseCount, int requiredCredits, int minDepartments, boolean selective,
                                  String requirementText, String guideUrl, List<CourseItem> courses) {

    /** credits 가 null 이면 자료에서 확인 못한 학점. groupKey 가 같은 과목끼리 groupMin~groupMax 과목을 인정한다(groupMax null = 제한 없음) */
    public record CourseItem(String courseCode, String courseName, String departmentName, Integer credits, boolean mandatory,
                             String groupKey, Integer groupMin, Integer groupMax) {
    }

    public static MicroDegreeResponse from(MicroDegree degree, List<MicroDegreeCourse> courses) {
        return new MicroDegreeResponse(degree.getId(), degree.getName(), degree.getScope(), degree.getCollegeName(),
                degree.getHostDepartment(), degree.getRequiredCourseCount(), degree.getRequiredCredits(),
                degree.getMinDepartments(), degree.isSelective(), degree.getRequirementText(), degree.getGuideUrl(),
                courses.stream().map(c -> new CourseItem(c.getCourseCode(), c.getCourseName(), c.getDepartmentName(),
                        c.getCredits(), c.isMandatory(), c.getGroupKey(), c.getGroupMin(), c.getGroupMax())).toList());
    }
}
