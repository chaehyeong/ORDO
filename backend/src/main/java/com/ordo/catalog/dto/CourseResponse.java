package com.ordo.catalog.dto;

import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;

public record CourseResponse(Long id, String collegeName, String unitName, String courseCode,
                             String name, Classification classification, int credits, boolean variableCredits,
                             String targetGrade, String openSemester, String track) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(course.getId(), course.getCollegeName(), course.getUnitName(),
                course.getCourseCode(), course.getName(), course.getClassification(), course.getCredits(),
                course.isVariableCredits(), course.getTargetGrade(), course.getOpenSemester(), course.getTrack());
    }
}
