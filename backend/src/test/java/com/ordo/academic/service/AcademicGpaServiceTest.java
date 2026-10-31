package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.CompletedCourseRequest;
import com.ordo.academic.dto.GpaSummary;
import com.ordo.academic.dto.GpaSummary.TermGpa;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.Term;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 명세 5장 평점 평균: 등급표 / 학점 가중 / 성적표 샘플 대조 / P·NP·미정 제외 / 재수강 / 학기별 / 전공 / 반올림 / 빈 이수내역 */
@ExtendWith(MockitoExtension.class)
class AcademicGpaServiceTest {

    @Mock
    CompletedCourseRepository completedCourseRepository;
    @InjectMocks
    AcademicGpaService academicGpaService;

    @Test
    void gradePointsFollowTheSchoolTable() {
        String[][] table = {{"A+", "4.3"}, {"A0", "4.0"}, {"A-", "3.7"}, {"B+", "3.3"}, {"B0", "3.0"}, {"B-", "2.7"},
                {"C+", "2.3"}, {"C0", "2.0"}, {"C-", "1.7"}, {"D+", "1.3"}, {"D0", "1.0"}, {"D-", "0.7"}, {"F", "0"}};

        assertThat(AcademicGpaService.GRADE_POINTS).hasSize(table.length);
        for (String[] row : table) {
            assertThat(AcademicGpaService.GRADE_POINTS.get(row[0])).isEqualByComparingTo(row[1]);
        }
    }

    @Test
    void everyGradeCanBeEnteredAndOthersAreRejected() {
        assertThat(AcademicGpaService.GRADE_POINTS.keySet()).allMatch(grade -> grade.matches(CompletedCourseRequest.GRADE_PATTERN));
        assertThat("P".matches(CompletedCourseRequest.GRADE_PATTERN)).isTrue();
        assertThat("NP".matches(CompletedCourseRequest.GRADE_PATTERN)).isTrue();
        assertThat("F-".matches(CompletedCourseRequest.GRADE_PATTERN)).isFalse();
        assertThat("E0".matches(CompletedCourseRequest.GRADE_PATTERN)).isFalse();
    }

    @Test
    void minusGradesCountInAverageAndEarnedCredits() {
        GpaSummary summary = calculate(
                course("X1", 3, Classification.GEN_FREE, "A-", 2026, Term.FIRST),
                course("X2", 3, Classification.GEN_FREE, "B-", 2026, Term.FIRST),
                course("X3", 3, Classification.GEN_FREE, "C-", 2026, Term.FIRST),
                course("X4", 3, Classification.GEN_FREE, "D-", 2026, Term.FIRST));

        assertThat(summary.gpa()).isEqualTo(2.2);
        assertThat(summary.gradedCredits()).isEqualTo(12);
        assertThat(summary.earnedCredits()).isEqualTo(12);
    }

    @Test
    void averageIsWeightedByCredits() {
        assertThat(calculate(
                course("X1", 3, Classification.GEN_FREE, "A+", 2026, Term.FIRST),
                course("X2", 3, Classification.GEN_FREE, "B0", 2026, Term.FIRST)).gpa()).isEqualTo(3.65);
        assertThat(calculate(
                course("X1", 3, Classification.GEN_FREE, "A0", 2026, Term.FIRST),
                course("X2", 1, Classification.GEN_FREE, "C0", 2026, Term.FIRST)).gpa()).isEqualTo(3.5);
    }

    /** T12 익명 성적표 샘플(8과목)과 성적표 요약 칸 대조: 평점평균 3.417(61.5/18), 전공 3.3(12학점), 취득 20 */
    @Test
    void matchesTranscriptSample() {
        GpaSummary summary = calculate(
                course("SWCON10400", 3, Classification.MAJOR_REQUIRED, "A+", 2026, Term.FIRST),
                course("CSE20101", 3, Classification.MAJOR_ELECTIVE, "B+", 2026, Term.FIRST),
                course("CSE000100", 1, Classification.GENERAL_ELECTIVE, "P", 2026, Term.FIRST),
                course("AMTH100931", 3, Classification.MAJOR_BASIC, "C+", 2026, Term.FIRST),
                course("AMTH100409", 3, Classification.MAJOR_BASIC, "B+", 2026, Term.FIRST),
                course("GEC1102G19", 3, Classification.GEN_REQUIRED, "B0", 2026, Term.FIRST),
                course("GED1991G00", 3, Classification.GEN_DISTRIBUTION, "A+", 2026, Term.FIRST),
                course("GEE1952G03", 1, Classification.GEN_FREE, "P", 2026, Term.FIRST));

        assertThat(summary.gpa()).isEqualTo(3.417);
        assertThat(summary.majorGpa()).isEqualTo(3.3);
        assertThat(summary.gradedCredits()).isEqualTo(18);
        assertThat(summary.earnedCredits()).isEqualTo(20);
        assertThat(summary.terms()).containsExactly(new TermGpa(2026, Term.FIRST, 3.417, 18, 20));
    }

    @Test
    void passNonPassAndUngradedAreExcludedFromAverage() {
        GpaSummary summary = calculate(
                course("X1", 3, Classification.GEN_FREE, "A0", 2026, Term.FIRST),
                course("X2", 1, Classification.GEN_FREE, "P", 2026, Term.FIRST),
                course("X3", 1, Classification.GEN_FREE, "NP", 2026, Term.FIRST),
                course("X4", 3, Classification.GEN_FREE, null, 2026, Term.FIRST));

        assertThat(summary.gpa()).isEqualTo(4.0);
        assertThat(summary.gradedCredits()).isEqualTo(3);
        assertThat(summary.earnedCredits()).isEqualTo(4);  // A0 3 + P 1 (NP·미정 제외)
    }

    @Test
    void onlyPassCoursesGiveNullGpa() {
        GpaSummary summary = calculate(course("X1", 1, Classification.GEN_FREE, "P", 2026, Term.FIRST));

        assertThat(summary.gpa()).isNull();
        assertThat(summary.majorGpa()).isNull();
        assertThat(summary.earnedCredits()).isEqualTo(1);
        assertThat(summary.terms()).containsExactly(new TermGpa(2026, Term.FIRST, null, 0, 1));
    }

    @Test
    void failCountsAsZeroAndIsNotEarned() {
        GpaSummary summary = calculate(
                course("X1", 3, Classification.GEN_FREE, "A+", 2026, Term.FIRST),
                course("X2", 3, Classification.GEN_FREE, "F", 2026, Term.FIRST));

        assertThat(summary.gpa()).isEqualTo(2.15);
        assertThat(summary.gradedCredits()).isEqualTo(6);
        assertThat(summary.earnedCredits()).isEqualTo(3);
    }

    @Test
    void retakeUsesLatestRecordEvenWhenItIsF() {
        GpaSummary improved = calculate(
                course("CSE1", 3, Classification.MAJOR_REQUIRED, "C0", 2025, Term.FIRST),
                course("CSE1", 3, Classification.MAJOR_REQUIRED, "A+", 2026, Term.FIRST));
        assertThat(improved.gpa()).isEqualTo(4.3);
        assertThat(improved.gradedCredits()).isEqualTo(3);
        assertThat(improved.earnedCredits()).isEqualTo(3);

        GpaSummary failed = calculate(
                course("CSE1", 3, Classification.MAJOR_REQUIRED, "A+", 2026, Term.FIRST),
                course("CSE1", 3, Classification.MAJOR_REQUIRED, "F", 2025, Term.FIRST));
        assertThat(failed.gpa()).isEqualTo(4.3);  // 입력 순서가 아니라 학기 순으로 최근 것

        GpaSummary latestFailed = calculate(
                course("CSE1", 3, Classification.MAJOR_REQUIRED, "B0", 2025, Term.FIRST),
                course("CSE1", 3, Classification.MAJOR_REQUIRED, "F", 2026, Term.FIRST));
        assertThat(latestFailed.gpa()).isEqualTo(0.0);
        assertThat(latestFailed.earnedCredits()).isZero();
    }

    @Test
    void retakeWithoutCodeMatchesNameIgnoringSpaces() {
        GpaSummary summary = calculate(
                named("대학 영어", 2, "C0", 2025, Term.FIRST),
                named("대학영어", 2, "A0", 2025, Term.SECOND));

        assertThat(summary.gpa()).isEqualTo(4.0);
        assertThat(summary.gradedCredits()).isEqualTo(2);
    }

    @Test
    void termValuesKeepReplacedCoursesAndAreChronological() {
        GpaSummary summary = calculate(
                course("CSE1", 3, Classification.MAJOR_REQUIRED, "A+", 2026, Term.FIRST),
                course("X1", 1, Classification.GEN_FREE, "A0", 2025, Term.WINTER),
                course("CSE1", 3, Classification.MAJOR_REQUIRED, "C0", 2025, Term.SECOND),
                course("X2", 2, Classification.GEN_FREE, "B0", 2025, Term.SUMMER));

        assertThat(summary.terms()).containsExactly(
                new TermGpa(2025, Term.SUMMER, 3.0, 2, 2),
                new TermGpa(2025, Term.SECOND, 2.0, 3, 3),  // 재수강으로 대체됐어도 그 학기 값에는 남는다
                new TermGpa(2025, Term.WINTER, 4.0, 1, 1),
                new TermGpa(2026, Term.FIRST, 4.3, 3, 3));
        assertThat(summary.gradedCredits()).isEqualTo(6);  // 전체는 CSE1 최근 것만
    }

    @Test
    void majorGpaUsesMajorBasicRequiredAndElectiveOnly() {
        GpaSummary summary = calculate(
                course("M1", 3, Classification.MAJOR_BASIC, "B0", 2026, Term.FIRST),
                course("M2", 3, Classification.MAJOR_REQUIRED, "A0", 2026, Term.FIRST),
                course("M3", 3, Classification.MAJOR_ELECTIVE, "A+", 2026, Term.FIRST),
                course("G1", 3, Classification.GEN_REQUIRED, "F", 2026, Term.FIRST),
                course("T1", 3, Classification.TEACHING_MAJOR, "F", 2026, Term.FIRST));

        assertThat(summary.majorGpa()).isEqualTo(3.767);  // 33.9 / 9 = 3.7666…
        assertThat(calculate(course("G1", 3, Classification.GEN_REQUIRED, "A0", 2026, Term.FIRST)).majorGpa()).isNull();
    }

    @Test
    void roundsHalfUpAtThirdDecimal() {
        // 66.9 / 18 = 3.71666… → 버림이면 3.716
        assertThat(calculate(
                course("X1", 15, Classification.GEN_FREE, "A0", 2026, Term.FIRST),
                course("X2", 3, Classification.GEN_FREE, "C+", 2026, Term.FIRST)).gpa()).isEqualTo(3.717);
        // 딱 반: D+ 1학점 + F 7학점 = 1.3 / 8 = 0.1625 → 0.163 (HALF_EVEN 이면 0.162)
        assertThat(calculate(
                course("X1", 1, Classification.GEN_FREE, "D+", 2026, Term.FIRST),
                course("X2", 7, Classification.GEN_FREE, "F", 2026, Term.FIRST)).gpa()).isEqualTo(0.163);
    }

    @Test
    void emptyCoursesGiveNullGpaAndNoTerms() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of());

        assertThat(academicGpaService.getGpa(1L)).isEqualTo(new GpaSummary(null, null, 0, 0, List.of()));
    }

    private static GpaSummary calculate(CompletedCourse... courses) {
        return AcademicGpaService.calculate(List.of(courses));
    }

    private static CompletedCourse course(String code, int credits, Classification classification, String grade,
                                          int year, Term term) {
        return CompletedCourse.builder().courseCode(code).courseName(code).credits(credits)
                .classification(classification).grade(grade).academicYear(year).term(term).build();
    }

    private static CompletedCourse named(String name, int credits, String grade, int year, Term term) {
        return CompletedCourse.builder().courseName(name).credits(credits)
                .classification(Classification.GEN_REQUIRED).grade(grade).academicYear(year).term(term).build();
    }
}
