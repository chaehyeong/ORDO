package com.ordo.academic.dto;

import com.ordo.academic.dto.ProgressSummary.MajorInfo;
import com.ordo.catalog.domain.Classification;
import java.util.List;

/** T21: 부전공 수강 후보와 요건 판정. 계산값이며 최종 인정은 학교 이수 심사다(교육과정 p118). 명세 5장 14번 */
public record MinorResponse(MajorInfo major, int admissionYear, int catalogYear, Requirements requirements,
                            boolean majorCoursesAvailable, boolean overlapCheckAvailable, int candidateCredits,
                            List<CompletedCandidate> completedCourses, List<RemainingCandidate> remainingCourses,
                            boolean reviewRequired, Completion completion) {

    public record Requirements(Integer requiredCredits, Integer electiveCredits, int totalCredits) {
    }

    public enum Status {
        CANDIDATE, PRIMARY_MAJOR_OVERLAP, DOUBLE_MAJOR_OVERLAP, OVERLAP_UNVERIFIED
    }

    public enum CompletionStatus {
        MET, IN_PROGRESS, NEEDS_REVIEW
    }

    public record Completion(CompletionStatus status, int requiredEarned, int requiredCredits, int totalEarned,
                             int totalCredits, List<String> missingCourses) {
    }

    public record CompletedCandidate(Long id, String courseName, String courseCode, Classification classification,
                                     int credits, String grade, Status status) {
    }

    public record RemainingCandidate(String courseName, String courseCode, Classification classification, int credits,
                                     String targetGrade, String openSemester, Status status) {
    }
}
