package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.MinorResponse;
import com.ordo.academic.dto.MinorResponse.Status;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;
import com.ordo.global.common.Term;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class MinorCourseCalculationTest {

    @Test
    void candidatesUseTranscriptCreditsWithoutCertifyingCompletion() {
        var result = calculate(List.of(course("M1", "후보1"), course("M2", "후보2")), List.of(), List.of(), true,
                List.of(taken("M1", "입력명", "A0", 4, 2026), taken("M2", "미정", null, 2, 2026)));

        assertThat(result.candidateCredits()).isEqualTo(6);
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.completedCourses()).extracting(c -> c.status()).containsOnly(Status.CANDIDATE);
        assertThat(result.remainingCourses()).isEmpty();
        assertThat(result.requirements().requiredCredits()).isNull();
        assertThat(result.requirements().totalCredits()).isEqualTo(21);
    }

    @Test
    void primaryAndDoubleMajorOverlapsNeverContributeCandidateCredits() {
        var result = calculate(List.of(course("M1", "공통1"), course("M2", "공통2"), course("M3", "전용")),
                List.of(course("M1", "주전공")), List.of(course("M2", "복수전공")), true,
                List.of(taken("M1", "공통1", "A0", 3, 2026), taken("M2", "공통2", "A0", 3, 2026), taken("M3", "전용", "A0", 3, 2026)));

        assertThat(result.candidateCredits()).isEqualTo(3);
        assertThat(result.completedCourses()).extracting(c -> c.status())
                .containsExactlyInAnyOrder(Status.PRIMARY_MAJOR_OVERLAP, Status.DOUBLE_MAJOR_OVERLAP, Status.CANDIDATE);
    }

    @Test
    void missingOtherMajorCatalogDoesNotSilentlyApproveOverlap() {
        var result = calculate(List.of(course("M1", "후보1"), course("M2", "후보2")), List.of(), List.of(), false,
                List.of(taken("M1", "후보1", "A0", 3, 2026)));

        assertThat(result.overlapCheckAvailable()).isFalse();
        assertThat(result.candidateCredits()).isZero();
        assertThat(result.completedCourses()).extracting(c -> c.status()).containsOnly(Status.OVERLAP_UNVERIFIED);
        assertThat(result.remainingCourses()).extracting(c -> c.status()).containsOnly(Status.OVERLAP_UNVERIFIED);
    }

    @Test
    void failedAndNpCoursesStayInRemainingCandidates() {
        var result = calculate(List.of(course("M1", "실패"), course("M2", "NP")), List.of(), List.of(), true,
                List.of(taken("M1", "실패", "F", 3, 2026), taken("M2", "NP", "NP", 3, 2026)));

        assertThat(result.candidateCredits()).isZero();
        assertThat(result.completedCourses()).isEmpty();
        assertThat(result.remainingCourses()).hasSize(2);
    }

    @Test
    void retakesUseLatestRecognizedRecordOnce() {
        var result = calculate(List.of(course("M1", "후보")), List.of(), List.of(), true,
                List.of(taken("M1", "예전", "C0", 3, 2025), taken("M1", "최근", "A0", 4, 2026)));

        assertThat(result.candidateCredits()).isEqualTo(4);
        assertThat(result.completedCourses()).singleElement().satisfies(c -> assertThat(c.courseName()).isEqualTo("최근"));
    }

    @Test
    void differentCodeWithSameNameIsNotMatched() {
        var result = calculate(List.of(course("M1", "같은이름")), List.of(), List.of(), true,
                List.of(taken("OTHER", "같은이름", "A0", 3, 2026)));

        assertThat(result.completedCourses()).isEmpty();
        assertThat(result.remainingCourses()).hasSize(1);
    }

    @Test
    void nameOnlyRecordMatchesAfterRemovingSpaces() {
        var result = calculate(List.of(course("M1", "과목 이름")), List.of(), List.of(), true,
                List.of(taken(null, "과 목이름", "A0", 3, 2026)));

        assertThat(result.candidateCredits()).isEqualTo(3);
        assertThat(result.remainingCourses()).isEmpty();
    }

    @Test
    void codeAndNameOnlyEntriesForOneCatalogCourseAreNotCountedTwice() {
        var result = calculate(List.of(course("M1", "같은 과목")), List.of(), List.of(), true,
                List.of(taken("M1", "같은 과목", "C0", 3, 2025), taken(null, "같은과목", "A0", 4, 2026)));

        assertThat(result.candidateCredits()).isEqualTo(4);
        assertThat(result.completedCourses()).hasSize(1);
    }

    @Test
    void remainingOverlapsAreLabeledAndEmptyCatalogIsExplicit() {
        var overlap = calculate(List.of(course("M1", "겹침")), List.of(course("M1", "주전공")), List.of(), true, List.of());
        assertThat(overlap.remainingCourses()).singleElement().satisfies(c -> assertThat(c.status()).isEqualTo(Status.PRIMARY_MAJOR_OVERLAP));
        var empty = calculate(List.of(), List.of(), List.of(), false, List.of());
        assertThat(empty.majorCoursesAvailable()).isFalse();
        assertThat(empty.reviewRequired()).isTrue();
    }

    private MinorResponse calculate(List<Course> minors, List<Course> first, List<Course> second, boolean available,
                                     List<CompletedCourse> completed) {
        Major major = mock(Major.class);
        when(major.getId()).thenReturn(14L);
        when(major.getDisplayName()).thenReturn("부전공");
        GraduationRequirement requirement = BeanUtils.instantiateClass(GraduationRequirement.class);
        ReflectionTestUtils.setField(requirement, "minorTotalCredits", 21);
        ReflectionTestUtils.setField(requirement, "minorElectiveCredits", 21);
        return MinorCourseCalculation.calculate(2025, major, requirement, minors, first, second, available, completed);
    }

    static Course course(String code, String name) {
        Course result = BeanUtils.instantiateClass(Course.class);
        ReflectionTestUtils.setField(result, "courseCode", code);
        ReflectionTestUtils.setField(result, "name", name);
        ReflectionTestUtils.setField(result, "classification", Classification.MAJOR_ELECTIVE);
        ReflectionTestUtils.setField(result, "credits", 3);
        return result;
    }

    private CompletedCourse taken(String code, String name, String grade, int credits, int year) {
        return CompletedCourse.builder().courseCode(code).courseName(name).grade(grade).credits(credits)
                .classification(Classification.GENERAL_ELECTIVE).academicYear(year).term(Term.FIRST).build();
    }
}
