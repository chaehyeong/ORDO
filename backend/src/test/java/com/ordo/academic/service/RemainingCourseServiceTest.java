package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.dto.ProgressSummary.RequiredCourse;
import com.ordo.academic.dto.ProgressSummary.Total;
import com.ordo.academic.dto.RemainingCoursesResponse;
import com.ordo.academic.dto.RemainingCoursesResponse.RemainingCourse;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.domain.GeneralEducationRequiredCourse;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.CourseRepository;
import com.ordo.catalog.repository.GeneralEducationRequiredCourseRepository;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class RemainingCourseServiceTest {

    static final String UNIT = "시각디자인학과";
    static final List<Classification> MAJOR_CLASSIFICATIONS =
            List.of(Classification.MAJOR_BASIC, Classification.MAJOR_REQUIRED, Classification.MAJOR_ELECTIVE);

    @Mock
    AcademicProgressService academicProgressService;
    @Mock
    UserRepository userRepository;
    @Mock
    CompletedCourseRepository completedCourseRepository;
    @Mock
    CourseRepository courseRepository;
    @Mock
    GeneralEducationRequiredCourseRepository generalEducationRequiredCourseRepository;
    @InjectMocks
    RemainingCourseService remainingCourseService;

    @Test
    void majorCoursesTakenByCodeOrByNameOfCodelessRecordAreRemoved() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of(
                completed("FD101", "드로잉", "A+"),
                completed(null, "디지털 디자인", "B0")));
        givenProfile(UNIT, List.of(), List.of(), List.of(
                major("FD101", "드로잉", Classification.MAJOR_BASIC),
                major("FD105", "디지털디자인", Classification.MAJOR_BASIC),
                major("FD103", "평면디자인", Classification.MAJOR_BASIC)));

        assertThat(names(remainingCourseService.getRemainingCourses(1L))).containsExactly("평면디자인");
    }

    @Test
    void sameNameWithDifferentCodeIsAnotherCourse() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of(completed("OLD100", "디자인특강", "A0")));
        givenProfile(UNIT, List.of(), List.of(), List.of(major("VID3001", "디자인특강", Classification.MAJOR_ELECTIVE)));

        assertThat(names(remainingCourseService.getRemainingCourses(1L))).containsExactly("디자인특강");
    }

    @Test
    void failedCoursesRemainButPassedRetakesAndUngradedOnesDoNot() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of(
                completed("FD101", "드로잉", "F"),
                completed("FD103", "평면디자인", "NP"),
                completed("FD105", "디지털디자인", "F"),
                completed("FD105", "디지털디자인", "B+"),
                completed("FD109", "현대미술사", null)));
        givenProfile(UNIT, List.of(), List.of(), List.of(
                major("FD101", "드로잉", Classification.MAJOR_BASIC),
                major("FD103", "평면디자인", Classification.MAJOR_BASIC),
                major("FD105", "디지털디자인", Classification.MAJOR_BASIC),
                major("FD109", "현대미술사", Classification.MAJOR_BASIC)));

        assertThat(names(remainingCourseService.getRemainingCourses(1L))).containsExactly("드로잉", "평면디자인");
    }

    @Test
    void generalCoursesFollowTheProgressChecklistWithRecommendedGrade() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of());
        givenProfile(UNIT,
                List.of(new RequiredCourse("인간의가치탐색", 3, true), new RequiredCourse("주제연구", 3, false)),
                List.of(general("인간의가치탐색", 3, "1"), general("주제연구", 3, "2")),
                List.of());

        assertThat(remainingCourseService.getRemainingCourses(1L).courses()).containsExactly(
                new RemainingCourse("주제연구", null, Classification.GEN_REQUIRED, 3, "2", null, null));
    }

    @Test
    void approximateProfileReadsFallbackYearOfGeneralCourses() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of());
        User user = user(UNIT);
        List<GeneralEducationRequiredCourse> general = List.of(general("주제연구", 3, "2"));
        given(academicProgressService.getSummary(1L)).willReturn(summary(2024, true, 40, List.of(new RequiredCourse("주제연구", 3, false))));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(generalEducationRequiredCourseRepository.findAllByAdmissionYearOrderByIdAsc(2026)).willReturn(general);
        given(courseRepository.findByUnitNameAndClassificationIn(UNIT, MAJOR_CLASSIFICATIONS)).willReturn(List.of());

        assertThat(names(remainingCourseService.getRemainingCourses(1L))).containsExactly("주제연구");
    }

    @Test
    void sortsByClassificationThenGradeThenNameWithMissingGradeLast() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of());
        givenProfile(UNIT,
                List.of(new RequiredCourse("빅뱅에서문명까지", 3, false), new RequiredCourse("대학영어", 2, false)),
                List.of(general("빅뱅에서문명까지", 3, "전학년"), general("대학영어", 2, "1")),
                List.of(major("V9", "등급 없음", Classification.MAJOR_ELECTIVE, null),
                        major("V2", "사진", Classification.MAJOR_ELECTIVE, "2"),
                        major("V1", "광고", Classification.MAJOR_ELECTIVE, "2"),
                        major("V3", "졸업논문", Classification.MAJOR_REQUIRED, "4"),
                        major("V4", "드로잉", Classification.MAJOR_BASIC, "1")));

        assertThat(names(remainingCourseService.getRemainingCourses(1L))).containsExactly(
                "대학영어", "빅뱅에서문명까지", "드로잉", "졸업논문", "광고", "사진", "등급 없음");
    }

    @Test
    void majorWithoutCourseUnitGivesOnlyGeneralCourses() {
        givenProfile(null, List.of(new RequiredCourse("주제연구", 3, false)), List.of(general("주제연구", 3, "2")), null);

        RemainingCoursesResponse response = remainingCourseService.getRemainingCourses(1L);

        assertThat(response.majorCoursesAvailable()).isFalse();
        assertThat(names(response)).containsExactly("주제연구");
        verify(courseRepository, never()).findByUnitNameAndClassificationIn(any(), any());
    }

    @Test
    void remainingCreditsComeFromProgressAndZeroCreditRequiredCourseStays() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of());
        givenProfile(UNIT, List.of(), List.of(),
                List.of(major("VID4009", "졸업논문(시각디자인전공)", Classification.MAJOR_REQUIRED, "4", 0)));

        RemainingCoursesResponse response = remainingCourseService.getRemainingCourses(1L);

        assertThat(response.remainingCredits()).isEqualTo(28);
        assertThat(response.majorCoursesAvailable()).isTrue();
        assertThat(response.courses()).containsExactly(new RemainingCourse("졸업논문(시각디자인전공)", "VID4009",
                Classification.MAJOR_REQUIRED, 0, "4", "1,2", UNIT));
    }

    @Test
    void doubleMajorAddsSecondMajorCoursesOnceAndMarksTheirUnit() {
        String second = "산업디자인학과";
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of(completed("ID101", "디자인사", "A0")));
        User user = givenProfile(UNIT, List.of(), List.of(), List.of(major("FD101", "드로잉", Classification.MAJOR_BASIC)));
        user.changeDoubleMajor(majorOf(second));
        List<Course> secondCourses = List.of(
                majorOf(second, "FD101", "드로잉"),
                majorOf(second, "FD112", "산업디자인리서치"),
                majorOf(second, "ID101", "디자인사"));
        given(courseRepository.findByUnitNameAndClassificationIn(second, MAJOR_CLASSIFICATIONS)).willReturn(secondCourses);

        RemainingCoursesResponse response = remainingCourseService.getRemainingCourses(1L);

        assertThat(response.majorCoursesAvailable()).isTrue();
        assertThat(response.courses()).extracting(RemainingCourse::courseCode, RemainingCourse::unitName)
                .containsExactly(tuple("FD101", UNIT), tuple("FD112", second));
    }

    @Test
    void doubleMajorWithoutCourseUnitIsReportedAndFirstMajorCoursesStay() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of());
        User user = givenProfile(UNIT, List.of(), List.of(), List.of(major("FD101", "드로잉", Classification.MAJOR_BASIC)));
        user.changeDoubleMajor(majorOf(null));

        RemainingCoursesResponse response = remainingCourseService.getRemainingCourses(1L);

        assertThat(response.majorCoursesAvailable()).isFalse();
        assertThat(names(response)).containsExactly("드로잉");
    }

    @Test
    void incompleteProfileIsPassedOn() {
        given(academicProgressService.getSummary(1L)).willThrow(new BusinessException(ErrorCode.PROFILE_INCOMPLETE));

        assertThatThrownBy(() -> remainingCourseService.getRemainingCourses(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PROFILE_INCOMPLETE);
    }

    private User givenProfile(String courseUnit, List<RequiredCourse> checklist, List<GeneralEducationRequiredCourse> general,
                              List<Course> majorCourses) {
        User user = user(courseUnit);
        given(academicProgressService.getSummary(1L)).willReturn(summary(2026, false, 28, checklist));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(generalEducationRequiredCourseRepository.findAllByAdmissionYearOrderByIdAsc(2026)).willReturn(general);
        if (courseUnit != null) {
            given(courseRepository.findByUnitNameAndClassificationIn(courseUnit, MAJOR_CLASSIFICATIONS)).willReturn(majorCourses);
        }
        return user;
    }

    private static ProgressSummary summary(int admissionYear, boolean approximate, int remaining, List<RequiredCourse> checklist) {
        return new ProgressSummary(admissionYear, new ProgressSummary.MajorInfo(1L, UNIT), new Total(102, 130, remaining, 78.4),
                List.of(), List.of(), checklist, List.of(), false, approximate);
    }

    private static List<String> names(RemainingCoursesResponse response) {
        return response.courses().stream().map(RemainingCourse::courseName).toList();
    }

    private static User user(String courseUnit) {
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은").major(majorOf(courseUnit)).build();
    }

    private static Major majorOf(String courseUnit) {
        Major major = lenientMock(Major.class);
        given(major.getCourseUnit()).willReturn(courseUnit);
        return major;
    }

    private static CompletedCourse completed(String code, String name, String grade) {
        return CompletedCourse.builder().courseCode(code).courseName(name).credits(3)
                .classification(Classification.MAJOR_BASIC).grade(grade).academicYear(2026).term(Term.FIRST).build();
    }

    private static Course major(String code, String name, Classification classification) {
        return major(code, name, classification, "1");
    }

    private static Course major(String code, String name, Classification classification, String targetGrade) {
        return major(code, name, classification, targetGrade, 3);
    }

    private static Course major(String code, String name, Classification classification, String targetGrade, int credits) {
        return major(code, name, classification, targetGrade, credits, UNIT);
    }

    private static Course major(String code, String name, Classification classification, String targetGrade, int credits,
                                String unitName) {
        Course course = lenientMock(Course.class);
        given(course.getUnitName()).willReturn(unitName);
        given(course.getCourseCode()).willReturn(code);
        given(course.getName()).willReturn(name);
        given(course.getClassification()).willReturn(classification);
        given(course.getCredits()).willReturn(credits);
        given(course.getTargetGrade()).willReturn(targetGrade);
        given(course.getOpenSemester()).willReturn("1,2");
        return course;
    }

    private static Course majorOf(String unitName, String code, String name) {
        return major(code, name, Classification.MAJOR_BASIC, "1", 3, unitName);
    }

    private static GeneralEducationRequiredCourse general(String name, int credits, String recommendedGrade) {
        GeneralEducationRequiredCourse course = lenientMock(GeneralEducationRequiredCourse.class);
        given(course.getCourseName()).willReturn(name);
        given(course.getCredits()).willReturn(credits);
        given(course.getRecommendedGrade()).willReturn(recommendedGrade);
        return course;
    }

    // 기준정보 엔티티는 생성자가 막혀 있어 Mock 으로 만든다 (쓰지 않는 값도 있어 lenient)
    private static <T> T lenientMock(Class<T> type) {
        return mock(type, withSettings().strictness(Strictness.LENIENT));
    }
}
