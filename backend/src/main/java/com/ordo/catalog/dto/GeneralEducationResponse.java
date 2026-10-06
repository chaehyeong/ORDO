package com.ordo.catalog.dto;

import com.ordo.catalog.domain.GeneralEducationRequirement;
import com.ordo.catalog.domain.GeneralEducationRequiredCourse;
import java.util.List;

public record GeneralEducationResponse(
        int admissionYear, int basisYear, boolean approximate,
        int requiredCredits, int distributionCredits, int distributionMinAreas,
        int freeCredits, int totalCredits, List<RequiredCourseResponse> requiredCourses) {

    public static GeneralEducationResponse from(int requestedYear, GeneralEducationRequirement requirement,
                                                List<GeneralEducationRequiredCourse> courses) {
        return new GeneralEducationResponse(requestedYear, requirement.getAdmissionYear(),
                requestedYear != requirement.getAdmissionYear(), requirement.getRequiredCredits(),
                requirement.getDistributionCredits(), requirement.getDistributionMinAreas(),
                requirement.getFreeCredits(), requirement.getTotalCredits(),
                courses.stream().map(RequiredCourseResponse::from).toList());
    }

    public record RequiredCourseResponse(Long id, String groupName, String courseName, int credits,
                                         String recommendedGrade, String note) {
        public static RequiredCourseResponse from(GeneralEducationRequiredCourse course) {
            return new RequiredCourseResponse(course.getId(), course.getGroupName(), course.getCourseName(),
                    course.getCredits(), course.getRecommendedGrade(), course.getNote());
        }
    }
}
