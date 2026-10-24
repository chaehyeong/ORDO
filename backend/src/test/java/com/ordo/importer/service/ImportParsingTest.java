package com.ordo.importer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordo.academic.dto.CompletedCourseBulkRequest.TermRef;
import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.Term;
import com.ordo.importer.dto.ImportPreviewResponse.GradeCourse;
import com.ordo.importer.dto.ImportPreviewResponse.GradesDraft;
import com.ordo.importer.dto.ImportPreviewResponse.ImportWarning;
import com.ordo.importer.dto.ImportPreviewResponse.TimetableDraft;
import com.ordo.importer.service.FileFormatDetector.Detected;
import com.ordo.timetable.dto.TimetableEntryBulkRequest.Entry;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 익명 처리한 실제 샘플(src/test/resources/import, 이름=홍길동·학번=2026000000·교수명=가명)로 해석 결과를 확인한다 */
class ImportParsingTest {

    @ParameterizedTest
    @ValueSource(strings = {"enrollment.xlsx", "enrollment.csv", "enrollment.txt"})
    void enrollmentGivesSixCoursesSevenEntriesAndOneNoTimeWarning(String file) throws IOException {
        EnrollmentInterpreter.Result result = EnrollmentInterpreter.interpret(read(file));
        TimetableDraft draft = result.draft();

        assertThat(result.studentNumber()).isEqualTo("2026000000");
        assertThat(draft.year()).isEqualTo(2026);
        assertThat(draft.term()).isEqualTo(Term.SECOND);
        assertThat(draft.courseCount()).isEqualTo(6);
        assertThat(draft.totalCredits()).isEqualTo(18);
        assertThat(draft.entries()).hasSize(7);
        assertThat(draft.entries().get(0)).isEqualTo(new Entry("객체지향프로그래밍", "CSE103", 2,
                LocalTime.of(15, 0), LocalTime.of(16, 50), "전205", "김교수", null));
        assertThat(draft.courses().get(2).courseCode()).isEqualTo("GEC0103");
        assertThat(draft.courses().get(2).section()).isEqualTo("G09");
        assertThat(draft.courses().get(0).classification()).isEqualTo(Classification.MAJOR_REQUIRED);
        assertThat(result.warnings()).extracting(ImportWarning::code).containsExactly("NO_TIME");
        assertThat(result.warnings().get(0).message()).contains("기초 미분적분학");
    }

    @ParameterizedTest
    @ValueSource(strings = {"transcript.xlsx", "transcript.csv", "transcript.txt"})
    void transcriptGivesEightCoursesTwentyCreditsInFirstTerm(String file) throws IOException {
        TranscriptInterpreter.Result result = TranscriptInterpreter.interpret(read(file));
        GradesDraft draft = result.draft();

        assertThat(result.studentNumber()).isEqualTo("2026000000");
        assertThat(draft.terms()).containsExactly(new TermRef(2026, Term.FIRST));
        assertThat(draft.courseCount()).isEqualTo(8);
        assertThat(draft.totalCredits()).isEqualTo(20);
        GradeCourse discrete = course(draft, "이산구조");
        assertThat(discrete.courseCode()).isEqualTo("CSE201");
        assertThat(discrete.section()).isEqualTo("01");
        assertThat(discrete.grade()).isEqualTo("B+");
        assertThat(discrete.classification()).isEqualTo(Classification.MAJOR_ELECTIVE);
        GradeCourse required = course(draft, "인간의가치탐색");
        assertThat(required.courseCode()).isEqualTo("GEC1102");
        assertThat(required.section()).isEqualTo("G19");
        assertThat(required.classification()).isEqualTo(Classification.GEN_REQUIRED);  // 중핵교과 (명세 9장 16)
        assertThat(course(draft, "소프트웨어가치탐구").grade()).isEqualTo("P");
        GradeCourse distribution = course(draft, "코딩하는아티스트");
        assertThat(distribution.classification()).isEqualTo(Classification.GEN_DISTRIBUTION);
        assertThat(distribution.needs()).containsExactly("DISTRIBUTION_AREA");
        assertThat(result.warnings()).extracting(ImportWarning::code).containsExactly("DISTRIBUTION_AREA_REQUIRED");
    }

    @Test
    void sameContentGivesSameResultInEveryFormat() throws IOException {
        EnrollmentInterpreter.Result xlsx = EnrollmentInterpreter.interpret(read("enrollment.xlsx"));
        assertThat(EnrollmentInterpreter.interpret(read("enrollment.csv"))).isEqualTo(xlsx);
        assertThat(EnrollmentInterpreter.interpret(read("enrollment.txt"))).isEqualTo(xlsx);

        TranscriptInterpreter.Result transcript = TranscriptInterpreter.interpret(read("transcript.xlsx"));
        assertThat(TranscriptInterpreter.interpret(read("transcript.csv"))).isEqualTo(transcript);
        assertThat(TranscriptInterpreter.interpret(read("transcript.txt"))).isEqualTo(transcript);
    }

    @Test
    void transcriptForwardFillsTermSkipsDiscardedAndRepeatedHeaders() {
        List<String> header = List.of("학년도/학기", "교과목", "이수구분", "평가방법", "학점", "점수", "평점/등급", "교강사명",
                "폐기사유", "취득구분", "취득대학");
        RawDocument doc = new RawDocument(List.of(
                List.of("전체 성적 보기"),
                List.of("성명", "홍길동", "학번", "2026000000"),
                header,
                row("2025", "2학기", "CSE10100", "컴퓨터개론", "05", "전공선택", "C0", ""),
                row(null, null, "CSE10200", "자료구조", "04", "전공필수", "F", "재수강"),   // 폐기
                List.of("1/2"),
                List.of("성명", "홍길동", "학번", "2026000000"),  // 쪽이 넘어가며 반복된 머리글
                header,
                row("2026", "1학기", "CSE10200", "자료구조", "04", "전공필수", "A0", ""),
                row(null, null, "XYZ99901", "새로운과목", "99", "특수교과", "A+", ""),
                List.of("원 성적 및 4.5 변환 성적"),
                List.of("2026", "1학기", "1", "20")));

        TranscriptInterpreter.Result result = TranscriptInterpreter.interpret(doc);

        assertThat(result.draft().courses()).extracting(GradeCourse::courseName, GradeCourse::year, GradeCourse::term)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("컴퓨터개론", 2025, Term.SECOND),
                        org.assertj.core.groups.Tuple.tuple("자료구조", 2026, Term.FIRST),
                        org.assertj.core.groups.Tuple.tuple("새로운과목", 2026, Term.FIRST));
        assertThat(result.draft().courses().get(2).classification()).isNull();  // 모르는 이수구분은 추측하지 않음
        assertThat(result.draft().courses().get(2).needs()).containsExactly("CLASSIFICATION");
        assertThat(result.warnings()).extracting(ImportWarning::code)
                .containsExactlyInAnyOrder("UNKNOWN_CLASSIFICATION", "DISCARDED");
    }

    @Test
    void delimitedReaderHandlesQuotesNewlinesAndEscapedQuotes() {
        RawDocument doc = DelimitedTextReader.read("a,\"b, c\",\"재수강\n여부\",\"말 \"\"인용\"\"\"\r\n\r\nx,y", ',');

        assertThat(doc.rows()).containsExactly(
                List.of("a", "b, c", "재수강\n여부", "말 \"인용\""), List.of(), List.of("x", "y"));
    }

    // 성적표 과목 행: (학년도, 학기), 학수번호, 과목명, 이수구분코드, 이수구분, 평가방법, 학점, 점수, 평점, 등급, 교강사, 폐기사유, 취득구분, 취득대학
    private static List<String> row(String year, String term, String code, String name, String classCode,
                                    String classText, String grade, String discard) {
        List<String> tail = List.of(code, name, classCode, classText, "등급", "3", "90", "4.0", grade, "가교수", discard, "", "");
        return year == null ? tail : java.util.stream.Stream.concat(java.util.stream.Stream.of(year, term), tail.stream()).toList();
    }

    private static GradeCourse course(GradesDraft draft, String name) {
        return draft.courses().stream().filter(c -> c.courseName().equals(name)).findFirst().orElseThrow();
    }

    static RawDocument read(String file) throws IOException {
        byte[] bytes;
        try (InputStream in = ImportParsingTest.class.getResourceAsStream("/import/" + file)) {
            bytes = in.readAllBytes();
        }
        Detected detected = FileFormatDetector.detect(bytes);
        return switch (detected.format()) {
            case XLSX -> XlsxReader.read(bytes);
            case CSV -> DelimitedTextReader.read(detected.text(), ',');
            case TXT -> DelimitedTextReader.read(detected.text(), '\t');
        };
    }
}
