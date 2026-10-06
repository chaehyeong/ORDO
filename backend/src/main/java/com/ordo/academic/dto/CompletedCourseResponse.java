package com.ordo.academic.dto;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.Term;

public record CompletedCourseResponse(Long id, String courseCode, String courseName, int credits,
                                      Classification classification, Integer distributionArea, String grade,
                                      int year, Term term) {

    public static CompletedCourseResponse from(CompletedCourse course) {
        return new CompletedCourseResponse(course.getId(), course.getCourseCode(), course.getCourseName(),
                course.getCredits(), course.getClassification(), course.getDistributionArea(), course.getGrade(),
                course.getAcademicYear(), course.getTerm());
    }
}
