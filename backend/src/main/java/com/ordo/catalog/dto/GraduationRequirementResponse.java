package com.ordo.catalog.dto;

import com.ordo.catalog.domain.GraduationRequirement;

/** 원본 조회이므로 미기재 값(null)을 그대로 반환한다. 이수현황 계산은 T7에서 처리한다. */
public record GraduationRequirementResponse(
        Long majorId, int admissionYear, Integer totalCredits,
        Integer basicCredits, Integer requiredCredits, Integer electiveCredits,
        Integer majorTotalCredits, Integer otherMajorCredits,
        Integer doubleBasicCredits, Integer doubleRequiredCredits, Integer doubleElectiveCredits,
        Integer doubleTotalCredits, Integer doubleOtherMajorCredits,
        Integer minorRequiredCredits, Integer minorElectiveCredits, Integer minorTotalCredits,
        String swRequirement, String englishLectureRequirement, String thesisRequirement,
        String topikRequirement, String competencyCertification) {

    public static GraduationRequirementResponse from(GraduationRequirement requirement) {
        return new GraduationRequirementResponse(requirement.getMajor().getId(), requirement.getAdmissionYear(),
                requirement.getTotalCredits(), requirement.getBasicCredits(), requirement.getRequiredCredits(),
                requirement.getElectiveCredits(), requirement.getMajorTotalCredits(), requirement.getOtherMajorCredits(),
                requirement.getDoubleBasicCredits(), requirement.getDoubleRequiredCredits(),
                requirement.getDoubleElectiveCredits(), requirement.getDoubleTotalCredits(),
                requirement.getDoubleOtherMajorCredits(), requirement.getMinorRequiredCredits(),
                requirement.getMinorElectiveCredits(), requirement.getMinorTotalCredits(),
                requirement.getSwRequirement(), requirement.getEnglishLectureRequirement(),
                requirement.getThesisRequirement(), requirement.getTopikRequirement(),
                requirement.getCompetencyCertification());
    }
}
