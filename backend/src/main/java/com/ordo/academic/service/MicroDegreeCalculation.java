package com.ordo.academic.service;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.MicroDegreeProgressResponse;
import com.ordo.academic.dto.MicroDegreeProgressResponse.CourseItem;
import com.ordo.catalog.domain.MicroDegree;
import com.ordo.catalog.domain.MicroDegreeCourse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 마이크로디그리 판정 (명세 5장, DB 조회 없음). 이수구분은 보지 않고 학수번호로 찾으며, 전공·교양 학점 계산에는 영향이 없다.
 * ponytail: 같은 과목은 먼저 등록한 MD 에 배정한다(운영지침 제5조⑤). 뒤 MD 에 더 필요해도 넘겨주지 않으니, 사용자 지정이 필요해지면 그때 추가
 */
final class MicroDegreeCalculation {

    private final Map<String, CompletedCourse> byKey = new HashMap<>();
    private final Map<String, CompletedCourse> byName = new HashMap<>();
    private final Set<CompletedCourse> assigned = new HashSet<>();

    private MicroDegreeCalculation(Collection<CompletedCourse> recognized) {
        for (CompletedCourse course : recognized) {
            byKey.put(course.retakeKey(), course);
            byName.put(AcademicProgressService.withoutSpaces(course.getCourseName()), course);
        }
    }

    /** degrees: 등록 순서, recognized: 이수현황과 같은 인정 과목 */
    static List<MicroDegreeProgressResponse> calculate(List<MicroDegree> degrees, Map<Long, List<MicroDegreeCourse>> coursesByDegree,
                                                       Collection<CompletedCourse> recognized) {
        MicroDegreeCalculation calculation = new MicroDegreeCalculation(recognized);
        return degrees.stream()
                .map(degree -> calculation.progress(degree, coursesByDegree.getOrDefault(degree.getId(), List.of())))
                .toList();
    }

    private MicroDegreeProgressResponse progress(MicroDegree degree, List<MicroDegreeCourse> rows) {
        List<CourseItem> items = new ArrayList<>();
        List<CompletedCourse> counted = new ArrayList<>();
        Map<String, Integer> groupCounts = new HashMap<>();
        Set<String> departments = new HashSet<>();
        boolean mandatoryMet = true;
        for (MicroDegreeCourse row : rows) {
            CompletedCourse taken = find(row);
            boolean counts = taken != null && !assigned.contains(taken) && withinGroupLimit(row, groupCounts);
            if (counts) {
                counted.add(taken);
                departments.add(row.getDepartmentName());
                if (row.getGroupKey() != null) {
                    groupCounts.merge(row.getGroupKey(), 1, Integer::sum);
                }
            }
            if (row.isMandatory() && !counts) {
                mandatoryMet = false;
            }
            if (taken != null) {
                items.add(new CourseItem(taken.getId(), taken.getCourseName(), taken.getCourseCode(), taken.getCredits(),
                        taken.getGrade(), counts));
            }
        }
        assigned.addAll(counted);

        List<String> unmet = new ArrayList<>();
        if (!mandatoryMet) {
            unmet.add("REQUIRED_COURSE");
        }
        if (counted.size() < degree.getRequiredCourseCount()) {
            unmet.add("COURSE_COUNT");
        }
        if (rows.stream().anyMatch(row -> row.getGroupMin() != null
                && groupCounts.getOrDefault(row.getGroupKey(), 0) < row.getGroupMin())) {
            unmet.add("GROUP_MINIMUM");
        }
        if (departments.size() < degree.getMinDepartments()) {
            unmet.add("DEPARTMENT_COUNT");
        }
        int credits = counted.stream().mapToInt(CompletedCourse::getCredits).sum();
        return new MicroDegreeProgressResponse(degree.getId(), degree.getName(), degree.getHostDepartment(), degree.isSelective(),
                degree.getGuideUrl(), Math.min(credits, degree.getRequiredCredits()), degree.getRequiredCredits(),
                Math.min(counted.size(), degree.getRequiredCourseCount()), degree.getRequiredCourseCount(),
                unmet.isEmpty(), unmet, period(counted), items);
    }

    // 학수번호(별칭 포함)로 찾는다. 학수번호 없는 수동 입력은 과목명으로 찾고, MD 에 학수번호가 없는 과목(타대학 등)은 어떤 기록이든 과목명으로 찾는다
    private CompletedCourse find(MicroDegreeCourse row) {
        String name = AcademicProgressService.withoutSpaces(row.getCourseName());
        if (row.getCourseCode() == null) {
            return byName.get(name);
        }
        CompletedCourse found = byKey.get("code:" + row.getCourseCode());
        if (found == null && row.getAliasCode() != null) {
            found = byKey.get("code:" + row.getAliasCode());
        }
        return found != null ? found : byKey.get("name:" + name);
    }

    // 한도를 넘은 과목은 세지 않을 뿐 미충족 사유가 아니다
    private static boolean withinGroupLimit(MicroDegreeCourse row, Map<String, Integer> groupCounts) {
        return row.getGroupKey() == null || row.getGroupMax() == null
                || groupCounts.getOrDefault(row.getGroupKey(), 0) < row.getGroupMax();
    }

    private static String period(List<CompletedCourse> counted) {
        if (counted.isEmpty()) {
            return null;
        }
        String first = label(Collections.min(counted, CompletedCourse.CHRONOLOGICAL));
        String last = label(Collections.max(counted, CompletedCourse.CHRONOLOGICAL));
        return first.equals(last) ? first : first + " ~ " + last;
    }

    private static String label(CompletedCourse course) {
        return course.getAcademicYear() + ". " + course.getTerm().getLabel();
    }
}
