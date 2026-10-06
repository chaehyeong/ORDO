package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.dto.ProgressSummary.Area;
import com.ordo.academic.dto.ProgressSummary.Group;
import com.ordo.academic.dto.ProgressSummary.Total;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.GeneralEducationRequiredCourse;
import com.ordo.catalog.domain.GeneralEducationRequirement;
import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.GeneralEducationRequiredCourseRepository;
import com.ordo.catalog.repository.GeneralEducationRequirementRepository;
import com.ordo.catalog.repository.GraduationRequirementRepository;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

/** 명세 5장 필수 케이스: 빈 이수내역 / F 제외 / 재수강 / NULL 요구학점 / 배분 영역 부족 / 2025학번 근사 / 졸업 가능 */
@ExtendWith(MockitoExtension.class)
class AcademicProgressServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    CompletedCourseRepository completedCourseRepository;
    @Mock
    GraduationRequirementRepository graduationRequirementRepository;
    @Mock
    GeneralEducationRequirementRepository generalEducationRequirementRepository;
    @Mock
    GeneralEducationRequiredCourseRepository generalEducationRequiredCourseRepository;
    @InjectMocks
    AcademicProgressService academicProgressService;

    final Major major = lenientMock(Major.class);
    final GraduationRequirement requirement = requirement(130, 12, 42, 27, 81);
    final GeneralEducationRequirement generalEducation = generalEducation();
    final List<GeneralEducationRequiredCourse> requiredCourses =
            List.of(requiredCourse("인간의가치탐색", 3), requiredCourse("대학영어", 2));

    {
        given(major.getId()).willReturn(54L);
        given(major.getDisplayName()).willReturn("컴퓨터공학부 컴퓨터공학과");
    }

    @Test
    void emptyCoursesGiveZeroProgress() {
        ProgressSummary summary = calculate(List.of());

        assertThat(summary.total()).isEqualTo(new Total(0, 130, 130, 0.0));
        assertThat(summary.areas()).extracting(Area::earned).containsOnly(0);
        assertThat(summary.requiredGeneralCourses()).noneMatch(ProgressSummary.RequiredCourse::done);
        assertThat(summary.graduatable()).isFalse();
    }

    @Test
    void failedCoursesAreNotCounted() {
        ProgressSummary summary = calculate(List.of(
                course(null, "운영체제", 3, Classification.MAJOR_REQUIRED, null, "F", 2026, Term.FIRST),
                course(null, "봉사활동", 1, Classification.GENERAL_ELECTIVE, null, "NP", 2026, Term.FIRST),
                course(null, "자료구조", 3, Classification.MAJOR_REQUIRED, null, "A+", 2026, Term.FIRST),
                course(null, "이번 학기 과목", 3, Classification.MAJOR_ELECTIVE, null, null, 2026, Term.SECOND)));

        assertThat(summary.total().earned()).isEqualTo(6);  // 성적 미정(null)은 인정
        assertThat(area(summary, "MAJOR_REQUIRED").earned()).isEqualTo(3);
        assertThat(area(summary, "OTHER").earned()).isZero();
    }

    @Test
    void retakenCourseCountsOnlyLatest() {
        ProgressSummary summary = calculate(List.of(
                course("CSE204", "자료구조", 3, Classification.MAJOR_ELECTIVE, null, "C0", 2025, Term.SECOND),
                course("CSE204", "자료구조", 3, Classification.MAJOR_REQUIRED, null, "A+", 2026, Term.FIRST),
                // 학수번호가 없으면 공백을 뺀 과목명으로 같은 과목을 찾는다. 2학기가 여름학기보다 나중
                course(null, "글쓰기 기초", 2, Classification.GENERAL_ELECTIVE, null, "P", 2026, Term.SECOND),
                course(null, "글쓰기기초", 2, Classification.GEN_FREE, null, "P", 2026, Term.SUMMER)));

        assertThat(summary.total().earned()).isEqualTo(5);
        assertThat(area(summary, "MAJOR_REQUIRED").earned()).isEqualTo(3);
        assertThat(area(summary, "MAJOR_ELECTIVE").earned()).isZero();
        assertThat(area(summary, "OTHER").earned()).isEqualTo(2);
        assertThat(area(summary, "GEN_FREE").earned()).isZero();
    }

    @Test
    void nullRequirementCreditsAreZero() {
        ProgressSummary summary = AcademicProgressService.calculate(2026, major, requirement(null, null, 42, 27, null),
                generalEducation, false, requiredCourses, List.of());

        assertThat(area(summary, "MAJOR_BASIC").required()).isZero();
        assertThat(summary.total()).isEqualTo(new Total(0, 0, 0, 0.0));
        assertThat(summary.groups()).extracting(Group::required).containsExactly(0, 29, 0);
    }

    @Test
    void tooFewDistributionAreasBlocksGraduation() {
        List<CompletedCourse> courses = graduatableCourses();
        courses.removeIf(c -> c.getClassification() == Classification.GEN_DISTRIBUTION);
        courses.add(course(null, "배분A", 3, Classification.GEN_DISTRIBUTION, 1, "A0", 2026, Term.FIRST));
        courses.add(course(null, "배분B", 3, Classification.GEN_DISTRIBUTION, 1, "A0", 2026, Term.FIRST));
        courses.add(course(null, "배분C", 3, Classification.GEN_DISTRIBUTION, 2, "A0", 2026, Term.FIRST));

        ProgressSummary summary = calculate(courses);

        Area distribution = area(summary, "GEN_DISTRIBUTION");
        assertThat(distribution.earned()).isEqualTo(9);
        assertThat(distribution.areaCount()).isEqualTo(2);
        assertThat(distribution.requiredAreaCount()).isEqualTo(3);
        assertThat(summary.graduatable()).isFalse();
    }

    @Test
    void admissionYear2025UsesApproximate2026GeneralEducation() {
        givenUser(2025);
        given(graduationRequirementRepository.findByMajorIdAndAdmissionYear(54L, 2025)).willReturn(Optional.of(requirement));
        given(generalEducationRequirementRepository.findByAdmissionYear(2025)).willReturn(Optional.empty());
        given(generalEducationRequirementRepository.findByAdmissionYear(2026)).willReturn(Optional.of(generalEducation));
        given(generalEducationRequiredCourseRepository.findAllByAdmissionYearOrderByIdAsc(2026)).willReturn(requiredCourses);
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of());

        ProgressSummary summary = academicProgressService.getSummary(1L);

        assertThat(summary.approximate()).isTrue();
        assertThat(summary.admissionYear()).isEqualTo(2025);
        assertThat(area(summary, "GEN_REQUIRED").required()).isEqualTo(17);
        assertThat(summary.requiredGeneralCourses()).hasSize(2);
    }

    @Test
    void graduatableWhenEverythingIsMet() {
        ProgressSummary summary = calculate(graduatableCourses());

        assertThat(summary.graduatable()).isTrue();
        assertThat(summary.total()).isEqualTo(new Total(130, 130, 0, 100.0));
        assertThat(summary.groups()).containsExactly(
                new Group("MAJOR", "전공", 81, 81), new Group("GENERAL", "교양", 29, 29), new Group("OTHER", "기타", 20, 20));
        assertThat(area(summary, "OTHER").required()).isNull();
        assertThat(summary.requiredGeneralCourses()).allMatch(ProgressSummary.RequiredCourse::done);  // "인간의 가치탐색" 공백 무시
        assertThat(summary.checks()).extracting(ProgressSummary.Check::name).containsExactly("SW기초교육", "졸업논문");
        assertThat(summary.major()).isEqualTo(new ProgressSummary.MajorInfo(54L, "컴퓨터공학부 컴퓨터공학과"));
    }

    @Test
    void incompleteProfileHasNoSummary() {
        given(userRepository.findById(1L)).willReturn(Optional.of(
                User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은").build()));

        assertThat(academicProgressService.findSummary(1L)).isEmpty();
        assertThatThrownBy(() -> academicProgressService.getSummary(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROFILE_INCOMPLETE);
    }

    @Test
    void percentIsFlooredToOneDecimal() {
        assertThat(AcademicProgressService.percent(102, 130)).isEqualTo(78.4);  // 78.46…
        assertThat(AcademicProgressService.percent(18, 130)).isEqualTo(13.8);
        assertThat(AcademicProgressService.percent(1, 3)).isEqualTo(33.3);
        assertThat(AcademicProgressService.percent(5, 0)).isZero();
    }

    private ProgressSummary calculate(List<CompletedCourse> courses) {
        return AcademicProgressService.calculate(2026, major, requirement, generalEducation, false, requiredCourses, courses);
    }

    /** 총 130 = 교양 29(필수 17·배분 9·3영역·자유 3) + 전공 81(12·42·27) + 기타 20 */
    private List<CompletedCourse> graduatableCourses() {
        List<CompletedCourse> courses = new ArrayList<>(List.of(
                course(null, "인간의 가치탐색", 3, Classification.GEN_REQUIRED, null, "A0", 2026, Term.FIRST),
                course(null, "대학영어", 2, Classification.GEN_REQUIRED, null, "B+", 2026, Term.FIRST),
                course(null, "기타 필수교양", 12, Classification.GEN_REQUIRED, null, "A0", 2026, Term.FIRST),
                course(null, "배분1", 3, Classification.GEN_DISTRIBUTION, 1, "A0", 2026, Term.FIRST),
                course(null, "배분2", 3, Classification.GEN_DISTRIBUTION, 2, "A0", 2026, Term.FIRST),
                course(null, "배분3", 3, Classification.GEN_DISTRIBUTION, 3, "A0", 2026, Term.FIRST),
                course(null, "자유", 3, Classification.GEN_FREE, null, "P", 2026, Term.FIRST),
                course(null, "전공기초", 12, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST),
                course(null, "전공필수", 42, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST),
                course(null, "전공선택", 27, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST),
                course(null, "일반선택", 20, Classification.GENERAL_ELECTIVE, null, "A0", 2026, Term.FIRST)));
        return courses;
    }

    private void givenUser(int admissionYear) {
        given(userRepository.findById(1L)).willReturn(Optional.of(User.builder().email("haeun@khu.ac.kr")
                .password("encoded").name("이하은").admissionYear(admissionYear).major(major).build()));
    }

    private static Area area(ProgressSummary summary, String key) {
        return summary.areas().stream().filter(a -> a.key().equals(key)).findFirst().orElseThrow();
    }

    private static CompletedCourse course(String code, String name, int credits, Classification classification,
                                          Integer area, String grade, int year, Term term) {
        return CompletedCourse.builder().courseCode(code).courseName(name).credits(credits)
                .classification(classification).distributionArea(area).grade(grade).academicYear(year).term(term)
                .build();
    }

    private static GraduationRequirement requirement(Integer total, Integer basic, Integer required, Integer elective,
                                                     Integer majorTotal) {
        GraduationRequirement r = lenientMock(GraduationRequirement.class);
        given(r.getTotalCredits()).willReturn(total);
        given(r.getBasicCredits()).willReturn(basic);
        given(r.getRequiredCredits()).willReturn(required);
        given(r.getElectiveCredits()).willReturn(elective);
        given(r.getMajorTotalCredits()).willReturn(majorTotal);
        given(r.getSwRequirement()).willReturn("6학점");
        given(r.getThesisRequirement()).willReturn("필요");
        return r;
    }

    private static GeneralEducationRequirement generalEducation() {
        GeneralEducationRequirement g = lenientMock(GeneralEducationRequirement.class);
        given(g.getAdmissionYear()).willReturn(2026);
        given(g.getRequiredCredits()).willReturn(17);
        given(g.getDistributionCredits()).willReturn(9);
        given(g.getDistributionMinAreas()).willReturn(3);
        given(g.getFreeCredits()).willReturn(3);
        given(g.getTotalCredits()).willReturn(29);
        return g;
    }

    private static GeneralEducationRequiredCourse requiredCourse(String name, int credits) {
        GeneralEducationRequiredCourse c = lenientMock(GeneralEducationRequiredCourse.class);
        given(c.getCourseName()).willReturn(name);
        given(c.getCredits()).willReturn(credits);
        return c;
    }

    // 기준정보 엔티티는 생성자가 막혀 있어 Mock 으로 만든다 (쓰지 않는 값도 있어 lenient)
    private static <T> T lenientMock(Class<T> type) {
        return mock(type, withSettings().strictness(Strictness.LENIENT));
    }
}
