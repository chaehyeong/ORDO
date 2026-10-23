package com.ordo.importer.dto;

import com.ordo.academic.dto.CompletedCourseBulkRequest.TermRef;
import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.Term;
import com.ordo.timetable.dto.TimetableEntryBulkRequest;
import java.util.List;

/**
 * 파일 가져오기 미리보기 (저장 안 함). type 에 따라 timetable 또는 grades 하나만 채운다.
 * 학번·이름은 넣지 않는다.
 */
public record ImportPreviewResponse(String type, String format, TimetableDraft timetable, GradesDraft grades,
                                    List<ImportWarning> warnings) {

    /** 수강신청확인서 → entries 는 POST /api/timetables/entries/bulk 의 entries 와 같은 모양 */
    public record TimetableDraft(int year, Term term, int courseCount, int totalCredits, List<EnrolledCourse> courses,
                                 List<TimetableEntryBulkRequest.Entry> entries) {
    }

    /** 학점·이수구분·재수강은 시간표 테이블에 칸이 없어 미리보기에만 보여준다 */
    public record EnrolledCourse(String courseCode, String section, String courseName, int credits,
                                 String classificationText, Classification classification, boolean retake,
                                 int sessionCount) {
    }

    /** 전체 성적 보기 → courses 는 completed-courses/bulk 의 courses, terms 는 replaceTerms 로 쓰면 된다 */
    public record GradesDraft(List<TermRef> terms, int courseCount, int totalCredits, List<GradeCourse> courses) {
    }

    /** needs: 사용자가 골라야 하는 값 (CLASSIFICATION / DISTRIBUTION_AREA / GRADE) */
    public record GradeCourse(int year, Term term, String courseCode, String section, String courseName, int credits,
                              String grade, String classificationText, Classification classification,
                              Integer distributionArea, List<String> needs) {
    }

    /** code: NO_TIME / OVERLAP / UNKNOWN_CLASSIFICATION / DISTRIBUTION_AREA_REQUIRED / UNKNOWN_GRADE / DISCARDED / UNPARSED_ROW / PDF_ACCURACY */
    public record ImportWarning(String code, String message) {
    }
}
