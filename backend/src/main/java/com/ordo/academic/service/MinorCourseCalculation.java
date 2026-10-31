package com.ordo.academic.service;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.MinorResponse;
import com.ordo.academic.dto.MinorResponse.CompletedCandidate;
import com.ordo.academic.dto.MinorResponse.RemainingCandidate;
import com.ordo.academic.dto.MinorResponse.Requirements;
import com.ordo.academic.dto.MinorResponse.Status;
import com.ordo.academic.dto.ProgressSummary.MajorInfo;
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

/** DB 조회 없는 T21 후보 계산. 학과의 부전공 지정 교과목 자료가 없어 최종 이수 판정은 하지 않는다. */
final class MinorCourseCalculation {

    private MinorCourseCalculation() {
    }

    static MinorResponse calculate(int admissionYear, Major minor, GraduationRequirement requirement,
                                   List<Course> minorCourses, List<Course> firstCourses, List<Course> secondCourses,
                                   boolean overlapCheckAvailable, List<CompletedCourse> completed) {
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
        return new MinorResponse(new MajorInfo(minor.getId(), minor.getDisplayName()), admissionYear, 2026,
                new Requirements(requirement.getMinorRequiredCredits(), requirement.getMinorElectiveCredits(),
                        requirement.getMinorTotalCredits()),
                !minorCourses.isEmpty(), overlapCheckAvailable, candidateCredits, matched, remaining, true);
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
