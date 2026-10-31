package com.ordo.academic.service;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.DoubleMajorResponse;
import com.ordo.academic.dto.DoubleMajorResponse.CourseItem;
import com.ordo.academic.dto.DoubleMajorResponse.Total;
import com.ordo.academic.dto.ProgressSummary.Group;
import com.ordo.academic.dto.ProgressSummary.MajorInfo;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 복수전공 계산 (명세 5장, DB 조회 없음). 두 전공의 교과목 마스터로 과목 소속을 판정한다.
 * ponytail: 단과대 시행세칙의 중복 인정 한도(영미어문↔영미문화 전공필수 15학점, 체육대학 전문실기)는 넣지 않았다. 명세 9장 26
 */
final class DoubleMajorCalculation {

    private final Major major;
    private final GraduationRequirement requirement;
    private final Map<String, Course> firstCourses;
    private final Map<String, Course> secondCourses;

    /** firstCourses·secondCourses 는 각 전공 교과목 마스터의 전공기초·필수·선택. major·requirement 는 제2전공 */
    DoubleMajorCalculation(Major major, GraduationRequirement requirement, List<Course> firstCourses, List<Course> secondCourses) {
        this.major = major;
        this.requirement = requirement;
        this.firstCourses = index(firstCourses);
        this.secondCourses = index(secondCourses);
    }

    /** 제2전공에만 있는 전공 과목: 제1전공 영역에서 빼고 기타로 센다. 양쪽에 있으면 양쪽 다 인정 */
    boolean onlyInSecondMajor(CompletedCourse course) {
        return AcademicProgressService.MAJOR_CLASSIFICATIONS.contains(course.getClassification())
                && secondCourses.containsKey(course.retakeKey())
                && !firstCourses.containsKey(course.retakeKey());
    }

    boolean met(Collection<CompletedCourse> recognized) {
        DoubleMajorResponse progress = progress(recognized);
        return progress.total().earned() >= progress.total().required()
                && progress.groups().stream().allMatch(group -> group.earned() >= group.required());
    }

    /** recognized: 이수현황과 같은 인정 과목. 이수구분은 성적표가 아니라 제2전공 마스터 기준, 학점은 내 이수 기록 기준 */
    DoubleMajorResponse progress(Collection<CompletedCourse> recognized) {
        Map<Classification, Integer> earned = new EnumMap<>(Classification.class);
        List<CourseItem> courses = new ArrayList<>();
        for (CompletedCourse course : recognized) {
            Course matched = secondCourses.get(course.retakeKey());
            if (matched != null) {
                earned.merge(matched.getClassification(), course.getCredits(), Integer::sum);
                courses.add(new CourseItem(course.getId(), course.getCourseName(), course.getCourseCode(),
                        matched.getClassification(), course.getCredits(), course.getGrade()));
            }
        }
        List<Group> groups = List.of(
                group("MAJOR_BASIC", "전공기초", earned, Classification.MAJOR_BASIC, requirement.getDoubleBasicCredits()),
                group("MAJOR_REQUIRED", "전공필수", earned, Classification.MAJOR_REQUIRED, requirement.getDoubleRequiredCredits()),
                group("MAJOR_ELECTIVE", "전공선택", earned, Classification.MAJOR_ELECTIVE, requirement.getDoubleElectiveCredits()));
        int totalEarned = groups.stream().mapToInt(Group::earned).sum();
        return new DoubleMajorResponse(new MajorInfo(major.getId(), major.getDisplayName()), major.getCourseUnit() != null,
                new Total(totalEarned, AcademicProgressService.zeroIfNull(requirement.getDoubleTotalCredits())), groups, courses);
    }

    private static Group group(String key, String label, Map<Classification, Integer> earned,
                               Classification classification, Integer required) {
        return new Group(key, label, earned.getOrDefault(classification, 0), AcademicProgressService.zeroIfNull(required));
    }

    // 키는 CompletedCourse.retakeKey 와 같은 모양: 학수번호가 있으면 학수번호로만, 없는 수동 입력만 공백 뺀 과목명으로 찾는다
    private static Map<String, Course> index(List<Course> courses) {
        Map<String, Course> index = new HashMap<>();
        for (Course course : courses) {
            index.putIfAbsent("code:" + course.getCourseCode(), course);
            index.putIfAbsent("name:" + AcademicProgressService.withoutSpaces(course.getName()), course);
        }
        return index;
    }
}
