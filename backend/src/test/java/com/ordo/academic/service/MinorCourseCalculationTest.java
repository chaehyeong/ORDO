package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.MinorResponse;
import com.ordo.academic.dto.MinorResponse.CompletionStatus;
import com.ordo.academic.dto.MinorResponse.Status;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;
import com.ordo.global.common.Term;
import java.util.List;
import java.util.Set;
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

    @Test
    void withoutDesignatedCoursesRequiredAreaUsesMajorRequiredAndTotalUsesAllCandidates() {
        List<Course> minors = List.of(required("R1", 3), required("R2", 3), course("E1", "선택1"), course("E2", "선택2"), course("E3", "선택3"));
        var partial = judge(2026, 6, 12, minors, Set.of(), taken("R1", "R1", "A0", 3, 2026), taken("E1", "선택1", "A0", 3, 2026),
                taken("E2", "선택2", "A0", 3, 2026), taken("E3", "선택3", "A0", 3, 2026));
        assertThat(partial.completion().status()).isEqualTo(CompletionStatus.IN_PROGRESS);
        assertThat(partial.completion().requiredEarned()).isEqualTo(3);
        assertThat(partial.completion().totalEarned()).isEqualTo(12);
        assertThat(partial.reviewRequired()).isFalse();

        var met = judge(2026, 6, 12, minors, Set.of(), taken("R1", "R1", "A0", 3, 2026), taken("R2", "R2", "A0", 3, 2026),
                taken("E1", "선택1", "A0", 3, 2026), taken("E2", "선택2", "A0", 3, 2026));
        assertThat(met.completion().status()).isEqualTo(CompletionStatus.MET);
        assertThat(met.completion().missingCourses()).isEmpty();
    }

    @Test
    void designatedCreditsEqualToRequirementMustAllBeTaken() {
        List<Course> minors = List.of(course("D1", "지정1"), course("D2", "지정2"), course("E1", "선택1"), required("R1", 3));
        var result = judge(2026, 6, 9, minors, Set.of("D1", "D2"), taken("D1", "지정1", "A0", 3, 2026), taken("E1", "선택1", "A0", 3, 2026),
                taken("R1", "R1", "A0", 3, 2026));

        // 전공필수 분류인 R1 은 지정 과목이 아니라 필수 학점에 들어가지 않는다
        assertThat(result.completion().requiredEarned()).isEqualTo(3);
        assertThat(result.completion().totalEarned()).isEqualTo(9);
        assertThat(result.completion().missingCourses()).containsExactly("지정2");
        assertThat(result.completion().status()).isEqualTo(CompletionStatus.IN_PROGRESS);
    }

    @Test
    void designatedPoolLargerThanRequirementIsChosenWithoutMissingList() {
        List<Course> minors = List.of(course("P1", "풀1"), course("P2", "풀2"), course("P3", "풀3"));
        var result = judge(2026, 6, 6, minors, Set.of("P1", "P2", "P3"), taken("P1", "풀1", "A0", 3, 2026), taken("P3", "풀3", "A0", 3, 2026));

        assertThat(result.completion().missingCourses()).isEmpty();
        assertThat(result.completion().status()).isEqualTo(CompletionStatus.MET);
    }

    @Test
    void designatedCreditsBelowRequirementNeedAllOfThemAndMajorRequiredCredits() {
        List<Course> minors = List.of(required("D1", 3), required("R1", 3), required("R2", 3));
        var withoutDesignated = judge(2026, 6, 6, minors, Set.of("D1"), taken("R1", "R1", "A0", 3, 2026), taken("R2", "R2", "A0", 3, 2026));
        assertThat(withoutDesignated.completion().requiredEarned()).isEqualTo(6);
        assertThat(withoutDesignated.completion().missingCourses()).containsExactly("D1");
        assertThat(withoutDesignated.completion().status()).isEqualTo(CompletionStatus.IN_PROGRESS);

        var met = judge(2026, 6, 6, minors, Set.of("D1"), taken("D1", "D1", "A0", 3, 2026), taken("R1", "R1", "A0", 3, 2026));
        assertThat(met.completion().status()).isEqualTo(CompletionStatus.MET);
    }

    @Test
    void overlappingAndFailedCoursesDoNotCountTowardCompletion() {
        Major major = mock(Major.class);
        List<Course> minors = List.of(course("M1", "겹침"), course("M2", "낙제"));
        var result = MinorCourseCalculation.calculate(2026, major, requirement(0, 3), minors, List.of(course("M1", "주전공")), List.of(), true,
                List.of(taken("M1", "겹침", "A0", 3, 2026), taken("M2", "낙제", "F", 3, 2026)), Set.of());

        assertThat(result.completion().totalEarned()).isZero();
        assertThat(result.completion().status()).isEqualTo(CompletionStatus.IN_PROGRESS);
    }

    @Test
    void missingCatalogOrOverlapDataNeedsReviewAndOlderYearsStayReviewRequired() {
        var noCatalog = judge(2026, 0, 3, List.of(), Set.of());
        assertThat(noCatalog.completion().status()).isEqualTo(CompletionStatus.NEEDS_REVIEW);
        assertThat(noCatalog.reviewRequired()).isTrue();

        var noOverlap = calculate(List.of(course("M1", "후보")), List.of(), List.of(), false, List.of(taken("M1", "후보", "A0", 21, 2026)));
        assertThat(noOverlap.completion().status()).isEqualTo(CompletionStatus.NEEDS_REVIEW);

        var older = judge(2025, 0, 3, List.of(course("M1", "후보")), Set.of(), taken("M1", "후보", "A0", 3, 2025));
        assertThat(older.completion().status()).isEqualTo(CompletionStatus.MET);
        assertThat(older.reviewRequired()).isTrue();
    }

    @Test
    void candidatesFollowDesignatedRows() {
        Course basic = course("B1", "기초");
        ReflectionTestUtils.setField(basic, "classification", Classification.MAJOR_BASIC);
        Course markedBasic = course("B2", "표시 기초");
        ReflectionTestUtils.setField(markedBasic, "classification", Classification.MAJOR_BASIC);
        List<Course> unit = List.of(basic, markedBasic, required("R1", 3), course("E1", "선택1"), course("E2", "선택2"));

        assertThat(MinorCourseCalculation.candidates(unit, Set.of(), Set.of())).extracting(Course::getCourseCode).containsExactly("R1", "E1", "E2");
        assertThat(MinorCourseCalculation.candidates(unit, Set.of("B2"), Set.of())).extracting(Course::getCourseCode).containsExactly("B2", "R1", "E1", "E2");
        assertThat(MinorCourseCalculation.candidates(unit, Set.of(), Set.of("B2", "E1"))).extracting(Course::getCourseCode).containsExactly("B2", "E1");
        assertThat(MinorCourseCalculation.candidates(unit, Set.of("R1"), Set.of("E2"))).extracting(Course::getCourseCode).containsExactly("R1", "E2");
    }

    private MinorResponse judge(int admissionYear, int requiredCredits, int totalCredits, List<Course> minors, Set<String> requiredCodes,
                                CompletedCourse... completed) {
        return MinorCourseCalculation.calculate(admissionYear, mock(Major.class), requirement(requiredCredits, totalCredits), minors,
                List.of(), List.of(), true, List.of(completed), requiredCodes);
    }

    private static GraduationRequirement requirement(int requiredCredits, int totalCredits) {
        GraduationRequirement requirement = BeanUtils.instantiateClass(GraduationRequirement.class);
        ReflectionTestUtils.setField(requirement, "minorRequiredCredits", requiredCredits);
        ReflectionTestUtils.setField(requirement, "minorTotalCredits", totalCredits);
        return requirement;
    }

    private static Course required(String code, int credits) {
        Course result = course(code, code);
        ReflectionTestUtils.setField(result, "classification", Classification.MAJOR_REQUIRED);
        ReflectionTestUtils.setField(result, "credits", credits);
        return result;
    }

    private MinorResponse calculate(List<Course> minors, List<Course> first, List<Course> second, boolean available,
                                     List<CompletedCourse> completed) {
        Major major = mock(Major.class);
        when(major.getId()).thenReturn(14L);
        when(major.getDisplayName()).thenReturn("부전공");
        GraduationRequirement requirement = BeanUtils.instantiateClass(GraduationRequirement.class);
        ReflectionTestUtils.setField(requirement, "minorTotalCredits", 21);
        ReflectionTestUtils.setField(requirement, "minorElectiveCredits", 21);
        return MinorCourseCalculation.calculate(2025, major, requirement, minors, first, second, available, completed, Set.of());
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
