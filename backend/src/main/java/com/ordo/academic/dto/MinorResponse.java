package com.ordo.academic.dto;

import com.ordo.academic.dto.ProgressSummary.MajorInfo;
import com.ordo.catalog.domain.Classification;
import java.util.List;

/** T21: 학과 확인 전의 수강 후보. candidateCredits는 최종 인정 학점이 아니며 reviewRequired는 항상 true. */
public record MinorResponse(MajorInfo major, int admissionYear, int catalogYear, Requirements requirements,
                            boolean majorCoursesAvailable, boolean overlapCheckAvailable, int candidateCredits,
                            List<CompletedCandidate> completedCourses, List<RemainingCandidate> remainingCourses,
                            boolean reviewRequired) {

    public record Requirements(Integer requiredCredits, Integer electiveCredits, int totalCredits) {
    }

    public enum Status {
        CANDIDATE, PRIMARY_MAJOR_OVERLAP, DOUBLE_MAJOR_OVERLAP, OVERLAP_UNVERIFIED
    }

    public record CompletedCandidate(Long id, String courseName, String courseCode, Classification classification,
                                     int credits, String grade, Status status) {
    }

    public record RemainingCandidate(String courseName, String courseCode, Classification classification, int credits,
                                     String targetGrade, String openSemester, Status status) {
    }
}
