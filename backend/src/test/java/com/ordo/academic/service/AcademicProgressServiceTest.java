package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.domain.UserMinor;
import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.dto.ProgressSummary.Area;
import com.ordo.academic.dto.ProgressSummary.Group;
import com.ordo.academic.dto.ProgressSummary.Total;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.academic.repository.UserMinorRepository;
import com.ordo.academic.dto.DoubleMajorResponse;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.domain.GeneralEducationRequiredCourse;
import com.ordo.catalog.domain.GeneralEducationRequirement;
import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.CourseRepository;
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
import java.util.Set;
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
    @Mock
    CourseRepository courseRepository;
    @Mock
    UserMinorRepository userMinorRepository;
    @InjectMocks
    AcademicProgressService academicProgressService;

    static final String COMPUTER_ENGINEERING = "컴퓨터공학부 컴퓨터공학과";

    final Major major = lenientMock(Major.class);
    final GraduationRequirement requirement = requirement(130, 12, 42, 27, 81);
    // 시각디자인(제1전공) 다전공 6/-/36 합 42 + 산업디자인(제2전공) 다전공 9/12/21 합 42
    final GraduationRequirement firstRequirement = doubleCredits(requirement(130, 6, null, 61, 67), 6, null, 36, 42);
    final GraduationRequirement secondRequirement = doubleCredits(lenientMock(GraduationRequirement.class), 9, 12, 21, 42);
    final Major secondMajor = lenientMock(Major.class);
    final List<Course> firstCourses = List.of(
            master("FD101", "드로잉", Classification.MAJOR_BASIC),
            master("FD113", "시각디자인연구", Classification.MAJOR_BASIC),
            master("VID3001", "편집디자인", Classification.MAJOR_ELECTIVE));
    final List<Course> secondCourses = List.of(
            master("FD101", "드로잉", Classification.MAJOR_BASIC),
            master("FD112", "산업디자인리서치", Classification.MAJOR_BASIC),
            master("ID201", "제품디자인", Classification.MAJOR_REQUIRED),
            master("ID301", "디자인경영", Classification.MAJOR_ELECTIVE),
            master("VID3001", "편집디자인", Classification.MAJOR_REQUIRED));
    final GeneralEducationRequirement generalEducation = generalEducation();
    final List<GeneralEducationRequiredCourse> requiredCourses =
            List.of(requiredCourse("인간의가치탐색", 3), requiredCourse("대학영어", 2));
    final List<GeneralEducationRequiredCourse> requiredWithSeminar = List.of(requiredCourse("인간의가치탐색", 3),
            requiredCourse("대학영어", 2), requiredCourse(AcademicProgressService.EXPLORATION_SEMINAR, 1));

    {
        given(major.getId()).willReturn(54L);
        given(major.getDisplayName()).willReturn("컴퓨터공학부 컴퓨터공학과");
        given(major.getCourseUnit()).willReturn("시각디자인학과");
        given(secondMajor.getId()).willReturn(12L);
        given(secondMajor.getDisplayName()).willReturn("산업디자인학과");
        given(secondMajor.getCourseUnit()).willReturn("산업디자인학과");
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
    void sameCourseRecordedWithAndWithoutCodeCountsOnce() {
        ProgressSummary summary = calculate(List.of(
                course("CSE204", "자료구조", 3, Classification.MAJOR_REQUIRED, null, "A0", 2025, Term.FIRST),
                course(null, "자료 구조", 3, Classification.MAJOR_REQUIRED, null, "A+", 2026, Term.FIRST),
                course("CSE300", "다른 과목", 3, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST)));

        assertThat(summary.total().earned()).isEqualTo(6);
        assertThat(area(summary, "MAJOR_REQUIRED").earned()).isEqualTo(6);
    }

    // 2023학번까지의 경과조치(교육과정 p45): 배분 12학점, 영역 제한 없음
    @Test
    void transitionRuleNeedsTwelveDistributionCreditsInAnyArea() {
        given(generalEducation.getDistributionCredits()).willReturn(12);
        given(generalEducation.getDistributionMinAreas()).willReturn(0);
        List<CompletedCourse> courses = graduatableCourses();  // 배분 9학점, 3영역
        assertThat(calculate(courses).graduatable()).isFalse();

        courses.removeIf(c -> c.getClassification() == Classification.GEN_DISTRIBUTION);
        courses.add(course(null, "옛 배분", 12, Classification.GEN_DISTRIBUTION, 1, "A0", 2023, Term.FIRST));

        ProgressSummary summary = calculate(courses);
        assertThat(area(summary, "GEN_DISTRIBUTION").areaCount()).isEqualTo(1);
        assertThat(summary.graduatable()).isTrue();
    }

    @Test
    void nullRequirementCreditsAreZero() {
        ProgressSummary summary = AcademicProgressService.calculate(2026, major, requirement(null, null, 42, 27, null),
                generalEducation, false, requiredCourses, List.of(), null, Set.of());

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
    void seminarNotTakenBlocksGraduationEvenWhenFreeCreditsAreMet() {
        ProgressSummary summary = calculateSeminar(major);

        assertThat(area(summary, "GEN_FREE").earned()).isEqualTo(area(summary, "GEN_FREE").required());
        assertThat(summary.total().remaining()).isZero();
        assertThat(seminarDone(summary)).isFalse();
        assertThat(summary.graduatable()).isFalse();
    }

    @Test
    void failedSeminarBlocksGraduation() {
        for (String grade : new String[]{"F", "NP"}) {
            ProgressSummary summary = calculateSeminar(major, course("GEE1952", AcademicProgressService.EXPLORATION_SEMINAR, 1,
                    Classification.GEN_FREE, null, grade, 2026, Term.FIRST));

            assertThat(seminarDone(summary)).as(grade).isFalse();
            assertThat(summary.graduatable()).as(grade).isFalse();
        }
    }

    @Test
    void passedSeminarCountsAsFreeElectiveNotRequiredGeneral() {
        ProgressSummary summary = calculateSeminar(major, course("GEE1952", AcademicProgressService.EXPLORATION_SEMINAR, 1,
                Classification.GEN_FREE, null, "P", 2026, Term.FIRST));

        assertThat(seminarDone(summary)).isTrue();
        assertThat(summary.graduatable()).isTrue();
        assertThat(area(summary, "GEN_FREE").earned()).isEqualTo(4);
        assertThat(area(summary, "GEN_REQUIRED").earned()).isEqualTo(17);
        assertThat(area(summary, "GEN_REQUIRED").required()).isEqualTo(17);
    }

    @Test
    void seminarRetakenAfterFailureIsDone() {
        ProgressSummary summary = calculateSeminar(major,
                course("GEE1952", AcademicProgressService.EXPLORATION_SEMINAR, 1, Classification.GEN_FREE, null, "NP", 2025, Term.FIRST),
                course("GEE1952", AcademicProgressService.EXPLORATION_SEMINAR, 1, Classification.GEN_FREE, null, "P", 2026, Term.FIRST));

        assertThat(seminarDone(summary)).isTrue();
        assertThat(area(summary, "GEN_FREE").earned()).isEqualTo(4);
    }

    @Test
    void seminarMatchesOtherNamesAndSubstituteInAnyClassification() {
        for (CompletedCourse taken : List.of(
                course(null, "전공탐색및 기업가정신 세미나", 1, Classification.GEN_FREE, null, "P", 2026, Term.FIRST),
                course(null, "전공탐색세미나", 1, Classification.GENERAL_ELECTIVE, null, "P", 2026, Term.FIRST),
                course(null, "전공탐색세미나및기업가정신세미나", 1, Classification.GEN_FREE, null, "P", 2026, Term.FIRST),
                course("SLS1014", "전공탐색2", 2, Classification.MAJOR_ELECTIVE, null, "P", 2026, Term.FIRST))) {
            assertThat(seminarDone(calculateSeminar(major, taken))).as(taken.getCourseName()).isTrue();
        }
        assertThat(seminarDone(calculateSeminar(major,
                course("SLS1001", "전공탐색1", 3, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST)))).isFalse();
    }

    @Test
    void startupSeminarCountsOnlyForListedEngineeringUnits() {
        CompletedCourse startup = course(null, "창업세미나:스타트업에서빅테크까지", 3, Classification.GEN_FREE, null, "A0", 2026, Term.FIRST);
        Major mechanical = lenientMock(Major.class);
        given(mechanical.getCourseUnit()).willReturn("기계공학부");

        assertThat(seminarDone(calculateSeminar(mechanical, startup))).isTrue();
        assertThat(seminarDone(calculateSeminar(major, startup))).isFalse();

        // 과목표가 없는 전공(글로벌한국학과 등)은 course_unit 이 NULL 이다
        Major noCourseUnit = lenientMock(Major.class);
        given(noCourseUnit.getCourseUnit()).willReturn(null);
        assertThat(seminarDone(calculateSeminar(noCourseUnit, startup))).isFalse();
    }

    @Test
    void otherRequiredCoursesStillNeedGenRequiredClassification() {
        ProgressSummary summary = calculate(List.of(course(null, "대학영어", 2, Classification.GEN_FREE, null, "P", 2026, Term.FIRST)));

        assertThat(summary.requiredGeneralCourses()).noneMatch(ProgressSummary.RequiredCourse::done);
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
        assertThat(summary.checks()).extracting(ProgressSummary.Check::name)
                .containsExactly("SW기초교육", "졸업논문", "다전공·부전공·마이크로디그리");
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

    @Test
    void multiProgramCheckIsOnlyFor2026AdmissionOnward() {
        ProgressSummary summary = AcademicProgressService.calculate(2025, major, requirement, generalEducation, true,
                requiredCourses, List.of(), null, Set.of());

        assertThat(summary.checks()).extracting(ProgressSummary.Check::name).containsExactly("SW기초교육", "졸업논문");
    }

    @Test
    void doubleMajorSwapsFirstMajorRequirementToDoubleCredits() {
        ProgressSummary summary = calculateDouble(List.of());

        assertThat(area(summary, "MAJOR_BASIC").required()).isEqualTo(6);
        assertThat(area(summary, "MAJOR_REQUIRED").required()).isZero();  // 시각디자인 전공필수 NULL = 요건 없음
        assertThat(area(summary, "MAJOR_ELECTIVE").required()).isEqualTo(36);
        assertThat(summary.groups()).containsExactly(
                new Group("MAJOR", "전공", 0, 42), new Group("GENERAL", "교양", 0, 29), new Group("OTHER", "기타", 0, 59));
        assertThat(summary.total().required()).isEqualTo(130);
    }

    @Test
    void courseInBothCurriculaCountsForBothMajorsButOnceTowardTotal() {
        List<CompletedCourse> courses = List.of(course("FD101", "드로잉", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST));

        ProgressSummary summary = calculateDouble(courses);
        DoubleMajorResponse second = doubleMajor().progress(courses);

        assertThat(area(summary, "MAJOR_BASIC").earned()).isEqualTo(3);
        assertThat(second.groups().get(0)).isEqualTo(new Group("MAJOR_BASIC", "전공기초", 3, 9));
        assertThat(second.total()).isEqualTo(new DoubleMajorResponse.Total(3, 42));
        assertThat(summary.total().earned()).isEqualTo(3);
    }

    @Test
    void secondMajorOnlyCourseLeavesFirstMajorAreaAndCountsAsOther() {
        List<CompletedCourse> courses = List.of(course("FD112", "산업디자인리서치", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST));

        ProgressSummary summary = calculateDouble(courses);

        assertThat(area(summary, "MAJOR_BASIC").earned()).isZero();
        assertThat(area(summary, "OTHER").earned()).isEqualTo(3);
        assertThat(summary.groups()).contains(new Group("MAJOR", "전공", 0, 42), new Group("OTHER", "기타", 3, 59));
        assertThat(summary.total().earned()).isEqualTo(3);
        assertThat(doubleMajor().progress(courses).groups().get(0).earned()).isEqualTo(3);
    }

    @Test
    void firstMajorOnlyAndUnknownCoursesStayInFirstMajor() {
        List<CompletedCourse> courses = List.of(
                course("FD113", "시각디자인연구", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST),
                course("OLD100", "옛 전공과목", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST));

        ProgressSummary summary = calculateDouble(courses);

        assertThat(area(summary, "MAJOR_BASIC").earned()).isEqualTo(3);
        assertThat(area(summary, "MAJOR_ELECTIVE").earned()).isEqualTo(3);
        assertThat(doubleMajor().progress(courses).courses()).isEmpty();
    }

    // 공통 과목: 제1전공은 성적표 이수구분, 제2전공은 그 학과 마스터의 이수구분 (교육과정 p116)
    @Test
    void eachMajorUsesItsOwnClassification() {
        List<CompletedCourse> courses = List.of(course("VID3001", "편집디자인", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST));

        assertThat(area(calculateDouble(courses), "MAJOR_ELECTIVE").earned()).isEqualTo(3);
        DoubleMajorResponse second = doubleMajor().progress(courses);
        assertThat(second.groups().get(1).earned()).isEqualTo(3);
        assertThat(second.groups().get(2).earned()).isZero();
        assertThat(second.courses().get(0).classification()).isEqualTo(Classification.MAJOR_REQUIRED);
    }

    @Test
    void generalElectiveOnTranscriptCountsForSecondMajorAndStaysOther() {
        List<CompletedCourse> courses = List.of(course("ID301", "디자인경영", 3, Classification.GENERAL_ELECTIVE, null, "A0", 2026, Term.FIRST));

        assertThat(area(calculateDouble(courses), "OTHER").earned()).isEqualTo(3);
        assertThat(doubleMajor().progress(courses).groups().get(2).earned()).isEqualTo(3);
    }

    @Test
    void generalEducationCourseInSecondCatalogKeepsItsArea() {
        List<CompletedCourse> courses = List.of(course("ID301", "디자인경영", 3, Classification.GEN_FREE, null, "P", 2026, Term.FIRST));

        ProgressSummary summary = calculateDouble(courses);

        assertThat(area(summary, "GEN_FREE").earned()).isEqualTo(3);
        assertThat(area(summary, "OTHER").earned()).isZero();
    }

    @Test
    void sameNameWithDifferentCodeIsNotRecognizedButCodelessRecordMatchesByName() {
        DoubleMajorResponse second = doubleMajor().progress(List.of(
                course("OLD100", "드로잉", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST),
                course(null, "제품 디자인", 3, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST)));

        assertThat(second.courses()).extracting(DoubleMajorResponse.CourseItem::courseName).containsExactly("제품 디자인");
    }

    @Test
    void secondMajorSeesRecognizedCoursesOnlyThroughService() {
        givenUser(2026);
        User user = userRepository.findById(1L).orElseThrow();
        user.changeDoubleMajor(secondMajor);
        given(graduationRequirementRepository.findByMajorIdAndAdmissionYear(12L, 2026)).willReturn(Optional.of(secondRequirement));
        given(courseRepository.findByUnitNameAndClassificationIn("시각디자인학과", AcademicProgressService.MAJOR_CLASSIFICATIONS)).willReturn(firstCourses);
        given(courseRepository.findByUnitNameAndClassificationIn("산업디자인학과", AcademicProgressService.MAJOR_CLASSIFICATIONS)).willReturn(secondCourses);
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of(
                course("ID201", "제품디자인", 3, Classification.MAJOR_REQUIRED, null, "F", 2026, Term.FIRST),
                course("FD112", "산업디자인리서치", 3, Classification.MAJOR_BASIC, null, "C0", 2025, Term.SECOND),
                course("FD112", "산업디자인리서치", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST)));

        DoubleMajorResponse second = academicProgressService.findDoubleMajor(1L).orElseThrow();

        assertThat(second.major()).isEqualTo(new ProgressSummary.MajorInfo(12L, "산업디자인학과"));
        assertThat(second.majorCoursesAvailable()).isTrue();
        assertThat(second.total()).isEqualTo(new DoubleMajorResponse.Total(3, 42));
        assertThat(second.courses()).singleElement().satisfies(c -> assertThat(c.grade()).isEqualTo("A0"));
    }

    @Test
    void summaryThroughServiceUsesDoubleMajor() {
        givenUser(2026);
        User user = userRepository.findById(1L).orElseThrow();
        user.changeDoubleMajor(secondMajor);
        given(graduationRequirementRepository.findByMajorIdAndAdmissionYear(54L, 2026)).willReturn(Optional.of(firstRequirement));
        given(graduationRequirementRepository.findByMajorIdAndAdmissionYear(12L, 2026)).willReturn(Optional.of(secondRequirement));
        given(generalEducationRequirementRepository.findByAdmissionYear(2026)).willReturn(Optional.of(generalEducation));
        given(generalEducationRequiredCourseRepository.findAllByAdmissionYearOrderByIdAsc(2026)).willReturn(requiredCourses);
        given(courseRepository.findByUnitNameAndClassificationIn("시각디자인학과", AcademicProgressService.MAJOR_CLASSIFICATIONS)).willReturn(firstCourses);
        given(courseRepository.findByUnitNameAndClassificationIn("산업디자인학과", AcademicProgressService.MAJOR_CLASSIFICATIONS)).willReturn(secondCourses);
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of(
                course("FD112", "산업디자인리서치", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST)));

        ProgressSummary summary = academicProgressService.getSummary(1L);

        assertThat(area(summary, "MAJOR_ELECTIVE").required()).isEqualTo(36);
        assertThat(area(summary, "MAJOR_BASIC").earned()).isZero();
        assertThat(area(summary, "OTHER").earned()).isEqualTo(3);
    }

    @Test
    void findDoubleMajorIsEmptyWithoutOne() {
        givenUser(2026);

        assertThat(academicProgressService.findDoubleMajor(1L)).isEmpty();
    }

    @Test
    void secondMajorWithoutCourseUnitShowsRequirementOnly() {
        Major noCourses = lenientMock(Major.class);
        given(noCourses.getCourseUnit()).willReturn(null);
        DoubleMajorCalculation calculation = new DoubleMajorCalculation(2026, noCourses, secondRequirement, firstCourses, List.of());
        List<CompletedCourse> courses = List.of(course("FD101", "드로잉", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST));

        DoubleMajorResponse second = calculation.progress(courses);

        assertThat(second.majorCoursesAvailable()).isFalse();
        assertThat(second.courses()).isEmpty();
        assertThat(second.total()).isEqualTo(new DoubleMajorResponse.Total(0, 42));
        assertThat(calculation.met(courses)).isFalse();
    }

    @Test
    void bothMajorsMustBeMetToGraduate() {
        ProgressSummary met = calculateDouble(doubleMajorGraduatableCourses("ID301", "디자인경영"));
        assertThat(met.total()).isEqualTo(new Total(130, 130, 0, 100.0));
        assertThat(met.graduatable()).isTrue();

        // 같은 학점이라도 제2전공 전공선택을 채우지 못하면 졸업 불가
        ProgressSummary notMet = calculateDouble(doubleMajorGraduatableCourses("OTHER100", "다른학과과목"));
        assertThat(notMet.total()).isEqualTo(new Total(130, 130, 0, 100.0));
        assertThat(notMet.graduatable()).isFalse();
    }

    @Test
    void minorOnlyCourseMovesToOtherAndKeepsRequirementAndTotal() {
        ProgressSummary summary = calculateMinor(List.of(
                course("ID301", "디자인경영", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST)));

        assertThat(area(summary, "MAJOR_ELECTIVE").earned()).isZero();
        assertThat(area(summary, "MAJOR_ELECTIVE").required()).isEqualTo(27);
        assertThat(area(summary, "OTHER").earned()).isEqualTo(3);
        assertThat(summary.groups()).contains(new Group("MAJOR", "전공", 0, 81), new Group("OTHER", "기타", 3, 20));
        assertThat(summary.total().earned()).isEqualTo(3);
    }

    @Test
    void minorOnlyCourseMovesForEveryMajorClassification() {
        ProgressSummary summary = calculateMinor(List.of(
                course("FD112", "산업디자인리서치", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST),
                course("ID201", "제품디자인", 3, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST),
                course("ID301", "디자인경영", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST)));

        assertThat(area(summary, "MAJOR_BASIC").earned()).isZero();
        assertThat(area(summary, "MAJOR_REQUIRED").earned()).isZero();
        assertThat(area(summary, "MAJOR_ELECTIVE").earned()).isZero();
        assertThat(area(summary, "OTHER").earned()).isEqualTo(9);
    }

    // 교육과정 p118: 부전공은 전공과 중복 인정하지 않는다. 겹치는 과목은 주전공에만 남는다
    @Test
    void courseAlsoInFirstMajorAndUnknownCourseStayInMajor() {
        ProgressSummary summary = calculateMinor(List.of(
                course("FD101", "드로잉", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST),
                course("OLD100", "옛 전공과목", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST)));

        assertThat(area(summary, "MAJOR_BASIC").earned()).isEqualTo(3);
        assertThat(area(summary, "MAJOR_ELECTIVE").earned()).isEqualTo(3);
        assertThat(area(summary, "OTHER").earned()).isZero();
    }

    @Test
    void minorCourseNotRecordedAsMajorKeepsItsArea() {
        ProgressSummary summary = calculateMinor(List.of(
                course("ID301", "디자인경영", 3, Classification.GEN_FREE, null, "P", 2026, Term.FIRST),
                course("ID201", "제품디자인", 3, Classification.GENERAL_ELECTIVE, null, "A0", 2026, Term.FIRST)));

        assertThat(area(summary, "GEN_FREE").earned()).isEqualTo(3);
        assertThat(area(summary, "OTHER").earned()).isEqualTo(3);
    }

    @Test
    void codelessRecordMatchesMinorByNameButDifferentCodeDoesNot() {
        ProgressSummary summary = calculateMinor(List.of(
                course(null, "제품 디자인", 3, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST),
                course("OLD100", "디자인경영", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST)));

        assertThat(area(summary, "MAJOR_REQUIRED").earned()).isZero();
        assertThat(area(summary, "OTHER").earned()).isEqualTo(3);
        assertThat(area(summary, "MAJOR_ELECTIVE").earned()).isEqualTo(3);
    }

    @Test
    void failedAndRetakenMinorCoursesFollowRecognizedCourses() {
        ProgressSummary summary = calculateMinor(List.of(
                course("ID201", "제품디자인", 3, Classification.MAJOR_REQUIRED, null, "F", 2026, Term.FIRST),
                course("ID301", "디자인경영", 3, Classification.MAJOR_ELECTIVE, null, "C0", 2025, Term.SECOND),
                course("ID301", "디자인경영", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST)));

        assertThat(area(summary, "OTHER").earned()).isEqualTo(3);
        assertThat(summary.total().earned()).isEqualTo(3);
    }

    @Test
    void firstMajorWithoutCoursesMovesNothing() {
        assertThat(AcademicProgressService.minorOnlyKeys(List.of(), secondCourses)).isEmpty();
    }

    @Test
    void minorOnlyCourseMissingFromMajorAreaCanBlockGraduation() {
        List<CompletedCourse> courses = graduatableCourses();
        courses.removeIf(c -> c.getCourseName().equals("전공선택"));
        courses.add(course("ID301", "디자인경영", 27, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST));

        assertThat(calculate(courses).graduatable()).isTrue();

        ProgressSummary summary = calculateMinor(courses);
        assertThat(summary.total().earned()).isEqualTo(130);
        assertThat(summary.graduatable()).isFalse();
    }

    @Test
    void doubleMajorAndMinorBothMoveButCourseInBothCountsOnce() {
        List<Course> minorCourses = List.of(
                master("BM101", "경영학원론", Classification.MAJOR_ELECTIVE),
                master("FD112", "산업디자인리서치", Classification.MAJOR_BASIC));

        ProgressSummary summary = AcademicProgressService.calculate(2026, major, firstRequirement, generalEducation, false, requiredCourses,
                List.of(course("BM101", "경영학원론", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST),
                        course("FD112", "산업디자인리서치", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST),
                        course("ID201", "제품디자인", 3, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST)),
                doubleMajor(), AcademicProgressService.minorOnlyKeys(firstCourses, minorCourses));

        assertThat(area(summary, "OTHER").earned()).isEqualTo(9);
        assertThat(summary.groups()).contains(new Group("MAJOR", "전공", 0, 42));
        assertThat(summary.total().earned()).isEqualTo(9);
    }

    @Test
    void summaryThroughServiceMovesMinorOnlyCourseWithoutChangingRequirement() {
        givenUser(2026);
        User user = userRepository.findById(1L).orElseThrow();
        given(userMinorRepository.findByUserId(1L)).willReturn(Optional.of(UserMinor.builder().user(user).major(secondMajor).build()));
        given(graduationRequirementRepository.findByMajorIdAndAdmissionYear(54L, 2026)).willReturn(Optional.of(requirement));
        given(generalEducationRequirementRepository.findByAdmissionYear(2026)).willReturn(Optional.of(generalEducation));
        given(generalEducationRequiredCourseRepository.findAllByAdmissionYearOrderByIdAsc(2026)).willReturn(requiredCourses);
        given(courseRepository.findByUnitNameAndClassificationIn("시각디자인학과", AcademicProgressService.MAJOR_CLASSIFICATIONS)).willReturn(firstCourses);
        given(courseRepository.findByUnitNameAndClassificationIn("산업디자인학과", AcademicProgressService.MAJOR_CLASSIFICATIONS)).willReturn(secondCourses);
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of(
                course("ID301", "디자인경영", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST)));

        ProgressSummary summary = academicProgressService.getSummary(1L);

        assertThat(area(summary, "MAJOR_ELECTIVE")).isEqualTo(new Area("MAJOR_ELECTIVE", "전공선택", 0, 27, null, null));
        assertThat(area(summary, "OTHER").earned()).isEqualTo(3);
    }

    @Test
    void firstMajorCoursesLeftOutOfDoubleMajorTableMove() {
        ProgressSummary summary = calculateComputerEngineeringFirst(2026);

        assertThat(area(summary, "MAJOR_BASIC").earned()).isEqualTo(3);
        assertThat(area(summary, "MAJOR_REQUIRED").earned()).isEqualTo(3);
        assertThat(area(summary, "MAJOR_ELECTIVE").earned()).isEqualTo(3);
        assertThat(area(summary, "OTHER").earned()).isEqualTo(3);
        assertThat(summary.total().earned()).isEqualTo(12);
    }

    // 표는 2026 교육과정이라 다른 학번은 성적표 이수구분 그대로
    @Test
    void doubleMajorTableAppliesOnlyToCatalogYear() {
        ProgressSummary summary = calculateComputerEngineeringFirst(2025);

        assertThat(area(summary, "MAJOR_BASIC").earned()).isEqualTo(6);
        assertThat(area(summary, "MAJOR_REQUIRED").earned()).isEqualTo(6);
        assertThat(area(summary, "OTHER").earned()).isZero();
    }

    @Test
    void transcriptElectiveStaysEvenWhenLeftOutOfTable() {
        Course left = master(COMPUTER_ENGINEERING, "SWCON253", "기계학습", Classification.MAJOR_REQUIRED);
        DoubleMajorCalculation calculation = new DoubleMajorCalculation(2026, secondMajor, secondRequirement, List.of(left), List.of());

        assertThat(calculation.firstMajorClassification(course("SWCON253", "기계학습", 3, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST)))
                .isEqualTo(Classification.MAJOR_ELECTIVE);
    }

    @Test
    void singleMajorIsNotChangedByDoubleMajorTable() {
        given(major.getCourseUnit()).willReturn(COMPUTER_ENGINEERING);

        ProgressSummary summary = calculate(List.of(
                course("AMTH1001", "미분방정식", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST),
                course("SWCON253", "기계학습", 3, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST)));

        assertThat(area(summary, "MAJOR_BASIC").earned()).isEqualTo(3);
        assertThat(area(summary, "MAJOR_REQUIRED").earned()).isEqualTo(3);
    }

    @Test
    void secondMajorCountsOnlyDesignatedCourses() {
        given(secondMajor.getCourseUnit()).willReturn("소프트웨어융합학과");
        DoubleMajorCalculation calculation = new DoubleMajorCalculation(2026, secondMajor, secondRequirement, List.of(), List.of(
                master("소프트웨어융합학과", "CSE103", "객체지향프로그래밍", Classification.MAJOR_REQUIRED),
                master("소프트웨어융합학과", "CSE301", "운영체제", Classification.MAJOR_REQUIRED)));

        DoubleMajorResponse second = calculation.progress(List.of(
                course("CSE103", "객체지향프로그래밍", 3, Classification.GENERAL_ELECTIVE, null, "A0", 2026, Term.FIRST),
                course("CSE301", "운영체제", 3, Classification.GENERAL_ELECTIVE, null, "A0", 2026, Term.FIRST)));

        assertThat(second.courses()).extracting(DoubleMajorResponse.CourseItem::courseCode).containsExactly("CSE103");
        assertThat(second.total().earned()).isEqualTo(3);
    }

    @Test
    void computerEngineeringAsSecondMajorMovesLeftOutRequiredToElective() {
        DoubleMajorCalculation calculation = new DoubleMajorCalculation(2026, secondMajor, secondRequirement, List.of(), List.of(
                master(COMPUTER_ENGINEERING, "SWCON253", "기계학습", Classification.MAJOR_REQUIRED)));

        DoubleMajorResponse second = calculation.progress(List.of(
                course("SWCON253", "기계학습", 3, Classification.GENERAL_ELECTIVE, null, "A0", 2026, Term.FIRST)));

        assertThat(second.courses()).singleElement()
                .satisfies(c -> assertThat(c.classification()).isEqualTo(Classification.MAJOR_ELECTIVE));
    }

    @Test
    void bigBangIsDroppedForExemptUnitWithoutChangingGraduationTotal() {
        Major exempt = lenientMock(Major.class);
        given(exempt.getCourseUnit()).willReturn(COMPUTER_ENGINEERING);

        ProgressSummary summary = calculateWithBigBang(exempt);

        assertThat(summary.requiredGeneralCourses()).extracting(ProgressSummary.RequiredCourse::name).containsExactly("인간의가치탐색");
        assertThat(area(summary, "GEN_REQUIRED").required()).isEqualTo(14);
        assertThat(summary.groups()).extracting(ProgressSummary.Group::key, ProgressSummary.Group::required).containsExactly(
                tuple("MAJOR", 81), tuple("GENERAL", 26), tuple("OTHER", 23));
        assertThat(summary.total().required()).isEqualTo(130);
    }

    @Test
    void bigBangStaysForUnitsNotInTheTable() {
        for (String unit : new String[] {"시각디자인학과", "전자공학부 반도체공학과", null}) {
            Major kept = lenientMock(Major.class);
            given(kept.getCourseUnit()).willReturn(unit);

            ProgressSummary summary = calculateWithBigBang(kept);

            assertThat(summary.requiredGeneralCourses()).hasSize(2);
            assertThat(area(summary, "GEN_REQUIRED").required()).isEqualTo(17);
        }
    }

    private ProgressSummary calculateWithBigBang(Major studentMajor) {
        return AcademicProgressService.calculate(2026, studentMajor, requirement, generalEducation, false,
                List.of(requiredCourse("인간의가치탐색", 3), requiredCourse("빅뱅에서문명까지", 3)), List.of(), null, Set.of());
    }

    // 컴공(제1전공) 지정 과목 2개와 빠진 전공기초·전공필수 각 1개
    private ProgressSummary calculateComputerEngineeringFirst(int admissionYear) {
        List<Course> computerEngineering = List.of(
                master(COMPUTER_ENGINEERING, "AMTH1009", "미분적분학", Classification.MAJOR_BASIC),
                master(COMPUTER_ENGINEERING, "AMTH1001", "미분방정식", Classification.MAJOR_BASIC),
                master(COMPUTER_ENGINEERING, "CSE301", "운영체제", Classification.MAJOR_REQUIRED),
                master(COMPUTER_ENGINEERING, "SWCON253", "기계학습", Classification.MAJOR_REQUIRED));
        return AcademicProgressService.calculate(admissionYear, major, firstRequirement, generalEducation, false, requiredCourses,
                List.of(course("AMTH1009", "미분적분학", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST),
                        course("AMTH1001", "미분방정식", 3, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST),
                        course("CSE301", "운영체제", 3, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST),
                        course("SWCON253", "기계학습", 3, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST)),
                new DoubleMajorCalculation(admissionYear, secondMajor, secondRequirement, computerEngineering, secondCourses), Set.of());
    }

    private ProgressSummary calculate(List<CompletedCourse> courses) {
        return AcademicProgressService.calculate(2026, major, requirement, generalEducation, false, requiredCourses, courses, null, Set.of());
    }

    // 세미나를 뺀 졸업 요건은 모두 채운 학생에게 taken 을 더한다
    private ProgressSummary calculateSeminar(Major studentMajor, CompletedCourse... taken) {
        List<CompletedCourse> courses = graduatableCourses();
        courses.addAll(List.of(taken));
        return AcademicProgressService.calculate(2026, studentMajor, requirement, generalEducation, false, requiredWithSeminar, courses, null, Set.of());
    }

    private static boolean seminarDone(ProgressSummary summary) {
        return summary.requiredGeneralCourses().stream()
                .filter(c -> c.name().equals(AcademicProgressService.EXPLORATION_SEMINAR))
                .findFirst().orElseThrow().done();
    }

    private ProgressSummary calculateDouble(List<CompletedCourse> courses) {
        return AcademicProgressService.calculate(2026, major, firstRequirement, generalEducation, false, requiredCourses,
                courses, doubleMajor(), Set.of());
    }

    // 주전공 firstCourses, 부전공 secondCourses
    private ProgressSummary calculateMinor(List<CompletedCourse> courses) {
        return AcademicProgressService.calculate(2026, major, requirement, generalEducation, false, requiredCourses, courses, null,
                AcademicProgressService.minorOnlyKeys(firstCourses, secondCourses));
    }

    private DoubleMajorCalculation doubleMajor() {
        return new DoubleMajorCalculation(2026, secondMajor, secondRequirement, firstCourses, secondCourses);
    }

    /** 총 130 = 교양 29 + 제1전공 42(6·36) + 제2전공 42(9·12·21) + 일반선택 17 */
    private List<CompletedCourse> doubleMajorGraduatableCourses(String secondElectiveCode, String secondElectiveName) {
        List<CompletedCourse> courses = graduatableCourses();
        courses.removeIf(c -> AcademicProgressService.MAJOR_CLASSIFICATIONS.contains(c.getClassification())
                || c.getClassification() == Classification.GENERAL_ELECTIVE);
        courses.add(course("FD113", "시각디자인연구", 6, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST));
        courses.add(course(null, "시각 전공선택", 36, Classification.MAJOR_ELECTIVE, null, "A0", 2026, Term.FIRST));
        courses.add(course("FD112", "산업디자인리서치", 9, Classification.MAJOR_BASIC, null, "A0", 2026, Term.FIRST));
        courses.add(course("ID201", "제품디자인", 12, Classification.MAJOR_REQUIRED, null, "A0", 2026, Term.FIRST));
        courses.add(course(secondElectiveCode, secondElectiveName, 21, Classification.GENERAL_ELECTIVE, null, "A0", 2026, Term.FIRST));
        courses.add(course(null, "일반선택", 17, Classification.GENERAL_ELECTIVE, null, "A0", 2026, Term.FIRST));
        return courses;
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

    private static GraduationRequirement doubleCredits(GraduationRequirement r, Integer basic, Integer required,
                                                       Integer elective, Integer total) {
        given(r.getDoubleBasicCredits()).willReturn(basic);
        given(r.getDoubleRequiredCredits()).willReturn(required);
        given(r.getDoubleElectiveCredits()).willReturn(elective);
        given(r.getDoubleTotalCredits()).willReturn(total);
        return r;
    }

    private static Course master(String code, String name, Classification classification) {
        return master("시각디자인학과", code, name, classification);
    }

    private static Course master(String unit, String code, String name, Classification classification) {
        Course c = lenientMock(Course.class);
        given(c.getUnitName()).willReturn(unit);
        given(c.getCourseCode()).willReturn(code);
        given(c.getName()).willReturn(name);
        given(c.getClassification()).willReturn(classification);
        return c;
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
