package com.ordo.academic.dto;

import com.ordo.academic.domain.PlannedCourse;
import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.AcademicTerm;
import com.ordo.global.common.Term;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/** 학기별 배치 (T18). 과목이 있는 학기만 시간 순으로 온다. 빈 학기 칸은 프론트가 만든다 */
public record PlannedCoursesResponse(int totalCredits, List<PlannedTerm> terms) {

    public record PlannedTerm(int year, Term term, String label, int credits, List<Course> courses) {
    }

    public record Course(Long id, String courseName, String courseCode, Classification classification, int credits) {
    }

    public static PlannedCoursesResponse from(List<PlannedCourse> courses) {
        List<PlannedTerm> terms = courses.stream()
                .sorted(Comparator.comparingInt(PlannedCourse::getAcademicYear).thenComparing(PlannedCourse::getTerm))
                .collect(Collectors.groupingBy(course -> new AcademicTerm(course.getAcademicYear(), course.getTerm()),
                        LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new PlannedTerm(entry.getKey().year(), entry.getKey().term(), entry.getKey().label(),
                        entry.getValue().stream().mapToInt(PlannedCourse::getCredits).sum(),
                        entry.getValue().stream().map(course -> new Course(course.getId(), course.getCourseName(),
                                course.getCourseCode(), course.getClassification(), course.getCredits())).toList()))
                .toList();
        return new PlannedCoursesResponse(courses.stream().mapToInt(PlannedCourse::getCredits).sum(), terms);
    }
}
