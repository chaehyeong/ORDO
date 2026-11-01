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
import java.util.Set;

/**
 * 복수전공 계산 (명세 5장, DB 조회 없음). 두 전공의 교과목 마스터로 과목 소속을 판정한다.
 * ponytail: 단과대 시행세칙의 중복 인정 한도(영미어문↔영미문화 전공필수 15학점, 체육대학 전문실기)는 넣지 않았다. 명세 9장 26
 */
final class DoubleMajorCalculation {

    private static final int CATALOG_YEAR = 2026;
    private static final String COMPUTER_ENGINEERING = "컴퓨터공학부 컴퓨터공학과";
    // 다전공 과목표에서 빠지는 전공기초·전공필수 학수번호 (p374·398·433·460·584, 응용수학 시행세칙 제5조). 없는 학과는 학점만 바뀐다
    // ponytail: 2026 표만 상수로 뒀다. 학번별로 나누거나 학과가 늘면 minor_designated_courses 같은 테이블로 옮긴다
    private static final Map<String, Set<String>> NOT_DESIGNATED = Map.of(
            COMPUTER_ENGINEERING, Set.of("AMTH1001", "CSE406", "SWCON104", "SWCON201", "SWCON253"),
            "컴퓨터공학부 인공지능학과", Set.of("AI1001", "CSE103", "CSE301", "SWCON104"),
            "소프트웨어융합학과", Set.of("CSE301", "CSE305", "CSE327", "CSE438", "SWCON101", "SWCON103", "SWCON104", "SWCON302"),
            "생체의공학과", Set.of("AMTH1009", "APCH1131", "APHY1000", "APHY1001", "BIO103", "BME102"),
            "우주과학과", Set.of("AMTH1004", "SPACE101"),
            "응용수학과", Set.of("AMTH1001", "AMTH1009", "APCH1101", "APHY1002"));

    private final int admissionYear;
    private final Major major;
    private final GraduationRequirement requirement;
    private final Map<String, Course> firstCourses;
    private final Map<String, Course> secondCourses;

    /** firstCourses·secondCourses 는 각 전공 교과목 마스터의 전공기초·필수·선택. major·requirement 는 제2전공 */
    DoubleMajorCalculation(int admissionYear, Major major, GraduationRequirement requirement, List<Course> firstCourses, List<Course> secondCourses) {
        this.admissionYear = admissionYear;
        this.major = major;
        this.requirement = requirement;
        this.firstCourses = index(firstCourses);
        this.secondCourses = index(secondCourses);
    }

    /**
     * 다전공일 때 그 학과에서의 이수구분 (명세 5장 12번). 과목표에서 빠진 전공기초·전공필수는 일반선택이 되고,
     * 컴공 전공필수만 전공선택이 된다 (p398 표2). 과목표가 2026 교육과정이라 다른 학번은 그대로다
     */
    static Classification classificationOf(int admissionYear, Course course, Classification classification) {
        boolean leftOut = admissionYear == CATALOG_YEAR && course.getCourseCode() != null
                && (classification == Classification.MAJOR_BASIC || classification == Classification.MAJOR_REQUIRED)
                && NOT_DESIGNATED.getOrDefault(course.getUnitName(), Set.of()).contains(course.getCourseCode());
        if (!leftOut) {
            return classification;
        }
        return COMPUTER_ENGINEERING.equals(course.getUnitName()) && classification == Classification.MAJOR_REQUIRED
                ? Classification.MAJOR_ELECTIVE : Classification.GENERAL_ELECTIVE;
    }

    /** 제1전공 이수구분은 성적표 값이고, 제1전공 과목표에서 빠진 과목만 옮긴다 */
    Classification firstMajorClassification(CompletedCourse course) {
        Course matched = firstCourses.get(course.retakeKey());
        return matched == null ? course.getClassification() : classificationOf(admissionYear, matched, course.getClassification());
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
            Classification classification = matched == null ? Classification.GENERAL_ELECTIVE
                    : classificationOf(admissionYear, matched, matched.getClassification());
            if (classification != Classification.GENERAL_ELECTIVE) {
                earned.merge(classification, course.getCredits(), Integer::sum);
                courses.add(new CourseItem(course.getId(), course.getCourseName(), course.getCourseCode(),
                        classification, course.getCredits(), course.getGrade()));
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
    static Map<String, Course> index(List<Course> courses) {
        Map<String, Course> index = new HashMap<>();
        for (Course course : courses) {
            index.putIfAbsent("code:" + course.getCourseCode(), course);
            index.putIfAbsent("name:" + AcademicProgressService.withoutSpaces(course.getName()), course);
        }
        return index;
    }
}
