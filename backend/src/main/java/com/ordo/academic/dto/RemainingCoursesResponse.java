package com.ordo.academic.dto;

import com.ordo.catalog.domain.Classification;
import java.util.List;

/**
 * 남은 수강 과목 (명세 4.6, T17). remainingCredits 는 이수현황의 졸업까지 남은 학점이고 courses 학점의 합이 아니다.
 * majorCoursesAvailable 이 false 면 전공 교과목 자료가 없는 전공이 있어 그 전공 과목이 courses 에 없다
 */
public record RemainingCoursesResponse(int remainingCredits, boolean majorCoursesAvailable, List<RemainingCourse> courses) {

    /**
     * targetGrade: 권장 학년("1", "3-4", 교양은 "전학년"도 있음), openSemester: "1" / "2" / "1,2". 자료에 없으면 null.
     * unitName: 어느 전공의 과목인지(복수전공이면 두 전공이 섞인다). 필수교양은 null
     */
    public record RemainingCourse(String courseName, String courseCode, Classification classification, int credits,
                                  String targetGrade, String openSemester, String unitName) {
    }
}
