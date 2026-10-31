package com.ordo.academic.dto;

import com.ordo.academic.dto.ProgressSummary.Group;
import com.ordo.academic.dto.ProgressSummary.MajorInfo;
import com.ordo.catalog.domain.Classification;
import java.util.List;

/**
 * 복수전공 진행 상황 (명세 4.6, T19). majorCoursesAvailable 이 false 면 교과목 자료가 없는 전공이라 courses 가 비어 있고 earned 는 0 이다.
 * groups 와 courses 의 이수구분은 성적표가 아니라 복수전공 학과의 교과목 마스터 기준이다
 */
public record DoubleMajorResponse(MajorInfo major, boolean majorCoursesAvailable, Total total, List<Group> groups,
                                  List<CourseItem> courses) {

    public record Total(int earned, int required) {
    }

    public record CourseItem(Long id, String courseName, String courseCode, Classification classification, int credits,
                             String grade) {
    }
}
