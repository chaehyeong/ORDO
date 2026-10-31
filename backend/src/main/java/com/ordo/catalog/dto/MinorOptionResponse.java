package com.ordo.catalog.dto;

import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;

/** 총학점이 확인된 부전공 후보. 영역별 NULL은 미기재를 뜻하므로 그대로 전달한다. */
public record MinorOptionResponse(Long id, String displayName, Long collegeId, String collegeName,
                                  Integer requiredCredits, Integer electiveCredits, int totalCredits,
                                  boolean majorCoursesAvailable) {
    public static MinorOptionResponse from(GraduationRequirement requirement, boolean majorCoursesAvailable) {
        Major major = requirement.getMajor();
        return new MinorOptionResponse(major.getId(), major.getDisplayName(), major.getCollege().getId(),
                major.getCollege().getName(), requirement.getMinorRequiredCredits(), requirement.getMinorElectiveCredits(),
                requirement.getMinorTotalCredits(), majorCoursesAvailable);
    }
}
