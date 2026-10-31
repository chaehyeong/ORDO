package com.ordo.academic.service;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.MinorResponse;
import com.ordo.academic.dto.MinorResponse.CompletedCandidate;
import com.ordo.academic.dto.MinorResponse.Completion;
import com.ordo.academic.dto.MinorResponse.CompletionStatus;
import com.ordo.academic.dto.MinorResponse.RemainingCandidate;
import com.ordo.academic.dto.MinorResponse.Requirements;
import com.ordo.academic.dto.MinorResponse.Status;
import com.ordo.academic.dto.ProgressSummary.MajorInfo;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** DB 조회 없는 T21 후보 계산과 요건 판정 (명세 5장 14번). 최종 인정은 학교 이수 심사다. */
final class MinorCourseCalculation {

    private static final int CATALOG_YEAR = 2026;
    private static final Set<Classification> CANDIDATE_CLASSIFICATIONS =
            Set.of(Classification.MAJOR_REQUIRED, Classification.MAJOR_ELECTIVE);

    private MinorCourseCalculation() {
    }

    /** 인정 과목을 지정한 전공은 지정 과목만, 아니면 전공필수·전공선택과 지정 필수 과목이 후보다 */
    static List<Course> candidates(List<Course> unitCourses, Set<String> requiredCodes, Set<String> electiveCodes) {
        return unitCourses.stream()
                .filter(course -> requiredCodes.contains(course.getCourseCode()) || electiveCodes.contains(course.getCourseCode())
                        || electiveCodes.isEmpty() && CANDIDATE_CLASSIFICATIONS.contains(course.getClassification()))
                .toList();
    }

    static MinorResponse calculate(int admissionYear, Major minor, GraduationRequirement requirement,
                                   List<Course> minorCourses, List<Course> firstCourses, List<Course> secondCourses,
                                   boolean overlapCheckAvailable, List<CompletedCourse> completed, Set<String> requiredCodes) {
        Map<String, Course> minorIndex = index(minorCourses);
        Map<String, Course> firstIndex = index(firstCourses);
        Map<String, Course> secondIndex = index(secondCourses);
        Collection<CompletedCourse> recognized = AcademicProgressService.recognize(completed);
        Set<String> taken = new HashSet<>();
        Map<String, CompletedCandidate> matchedByCourse = new LinkedHashMap<>();
        for (CompletedCourse course : recognized.stream().sorted(CompletedCourse.CHRONOLOGICAL).toList()) {
            taken.add(course.retakeKey());
            Course candidate = minorIndex.get(course.retakeKey());
            if (candidate != null) {
                matchedByCourse.put(key(candidate), new CompletedCandidate(course.getId(), course.getCourseName(), course.getCourseCode(),
                        candidate.getClassification(), course.getCredits(), course.getGrade(),
                        status(course.retakeKey(), firstIndex, secondIndex, overlapCheckAvailable)));
            }
        }
        List<CompletedCandidate> matched = new ArrayList<>(matchedByCourse.values());
        matched.sort(Comparator.comparing(CompletedCandidate::courseName));
        Set<String> seen = new HashSet<>();
        List<RemainingCandidate> remaining = minorCourses.stream()
                .sorted(Comparator.comparing(Course::getName))
                .filter(course -> seen.add(key(course)))
                .filter(course -> !taken.contains(key(course)) && !taken.contains(nameKey(course.getName())))
                .map(course -> new RemainingCandidate(course.getName(), course.getCourseCode(), course.getClassification(),
                        course.getCredits(), course.getTargetGrade(), course.getOpenSemester(),
                        status(key(course), firstIndex, secondIndex, overlapCheckAvailable)))
                .toList();
        int candidateCredits = matched.stream().filter(course -> course.status() == Status.CANDIDATE)
                .mapToInt(CompletedCandidate::credits).sum();
        Completion completion = completion(requirement, minorCourses, requiredCodes, matchedByCourse,
                !minorCourses.isEmpty() && overlapCheckAvailable);
        return new MinorResponse(new MajorInfo(minor.getId(), minor.getDisplayName()), admissionYear, CATALOG_YEAR,
                new Requirements(requirement.getMinorRequiredCredits(), requirement.getMinorElectiveCredits(),
                        requirement.getMinorTotalCredits()),
                !minorCourses.isEmpty(), overlapCheckAvailable, candidateCredits, matched, remaining,
                admissionYear != CATALOG_YEAR || completion.status() == CompletionStatus.NEEDS_REVIEW, completion);
    }

    // ponytail: 컴퓨터공학과의 대체 인정(겹침 시 최대 2과목, 객체지향프로그래밍및실습 등, p398)은 판정하지 않아 미충족으로 나올 수 있다. 명세 5장 14번
    private static Completion completion(GraduationRequirement requirement, List<Course> minorCourses, Set<String> requiredCodes,
                                         Map<String, CompletedCandidate> matched, boolean judgeable) {
        int requiredCredits = requirement.getMinorRequiredCredits() == null ? 0 : requirement.getMinorRequiredCredits();
        int totalCredits = requirement.getMinorTotalCredits();
        Map<String, Course> designated = new LinkedHashMap<>();
        minorCourses.stream().filter(course -> requiredCodes.contains(course.getCourseCode()))
                .forEach(course -> designated.putIfAbsent(key(course), course));
        int designatedCredits = designated.values().stream().mapToInt(Course::getCredits).sum();
        Map<String, CompletedCandidate> counted = new LinkedHashMap<>();
        matched.forEach((key, course) -> {
            if (course.status() == Status.CANDIDATE) {
                counted.put(key, course);
            }
        });
        // 지정 학점이 요구 학점 이상이면 지정 과목으로 세고, 모자라면(응용수학 p517) 지정 과목을 모두 듣고 전공필수로 센다
        boolean designatedCovers = !designated.isEmpty() && designatedCredits >= requiredCredits;
        int requiredEarned = counted.entrySet().stream()
                .filter(entry -> designatedCovers ? designated.containsKey(entry.getKey())
                        : entry.getValue().classification() == Classification.MAJOR_REQUIRED)
                .mapToInt(entry -> entry.getValue().credits()).sum();
        int totalEarned = counted.values().stream().mapToInt(CompletedCandidate::credits).sum();
        // 지정 학점이 요구 학점보다 많으면 골라 듣는 과목이라(우주과학 9과목 중 4과목) 빠진 과목을 정할 수 없다
        List<String> missing = designatedCredits > requiredCredits ? List.of() : designated.entrySet().stream()
                .filter(entry -> !counted.containsKey(entry.getKey()))
                .map(entry -> entry.getValue().getName()).sorted().toList();
        CompletionStatus status = !judgeable ? CompletionStatus.NEEDS_REVIEW
                : requiredEarned >= requiredCredits && missing.isEmpty() && totalEarned >= totalCredits
                ? CompletionStatus.MET : CompletionStatus.IN_PROGRESS;
        return new Completion(status, requiredEarned, requiredCredits, totalEarned, totalCredits, missing);
    }

    private static Status status(String key, Map<String, Course> first, Map<String, Course> second, boolean available) {
        if (first.containsKey(key)) {
            return Status.PRIMARY_MAJOR_OVERLAP;
        }
        if (second.containsKey(key)) {
            return Status.DOUBLE_MAJOR_OVERLAP;
        }
        return available ? Status.CANDIDATE : Status.OVERLAP_UNVERIFIED;
    }

    private static Map<String, Course> index(List<Course> courses) {
        Map<String, Course> result = new LinkedHashMap<>();
        for (Course course : courses) {
            result.putIfAbsent(key(course), course);
            result.putIfAbsent(nameKey(course.getName()), course);
        }
        return result;
    }

    private static String key(Course course) {
        return course.getCourseCode() == null ? nameKey(course.getName()) : "code:" + course.getCourseCode();
    }

    private static String nameKey(String name) {
        return "name:" + AcademicProgressService.withoutSpaces(name);
    }
}
