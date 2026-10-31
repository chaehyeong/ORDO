package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.domain.UserMicroDegree;
import com.ordo.academic.dto.MicroDegreeAddRequest;
import com.ordo.academic.dto.MicroDegreeProgressResponse;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.academic.repository.UserMicroDegreeRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.MicroDegree;
import com.ordo.catalog.domain.MicroDegreeCourse;
import com.ordo.catalog.repository.MicroDegreeCourseRepository;
import com.ordo.catalog.repository.MicroDegreeRepository;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

/** 명세 5장 마이크로디그리 판정: 필수 / 과목 수 / 그룹 상한·하한 / 2개 학과 / 별칭·과목명 / MD 간 중복 / 학점 상한 / 기간 */
@ExtendWith(MockitoExtension.class)
class MicroDegreeServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    UserMicroDegreeRepository userMicroDegreeRepository;
    @Mock
    MicroDegreeRepository microDegreeRepository;
    @Mock
    MicroDegreeCourseRepository microDegreeCourseRepository;
    @Mock
    CompletedCourseRepository completedCourseRepository;
    @InjectMocks
    MicroDegreeService microDegreeService;

    // ---------- 판정

    @Test
    void nothingTakenGivesZeroAndNoPeriod() {
        MicroDegreeProgressResponse progress = calculate(degree(1L, 3, 9), List.of(mandatory("A1"), mandatory("A2"), mandatory("A3")), List.of());

        assertThat(progress.earned()).isZero();
        assertThat(progress.required()).isEqualTo(9);
        assertThat(progress.earnedCourses()).isZero();
        assertThat(progress.requiredCourses()).isEqualTo(3);
        assertThat(progress.completed()).isFalse();
        assertThat(progress.unmet()).containsExactly("REQUIRED_COURSE", "COURSE_COUNT");
        assertThat(progress.period()).isNull();
        assertThat(progress.courses()).isEmpty();
    }

    @Test
    void allMandatoryCoursesComplete() {
        MicroDegreeProgressResponse progress = calculate(degree(1L, 3, 9), List.of(mandatory("A1"), mandatory("A2"), mandatory("A3")),
                List.of(taken("A1"), taken("A2"), taken("A3")));

        assertThat(progress.completed()).isTrue();
        assertThat(progress.unmet()).isEmpty();
        assertThat(progress.earned()).isEqualTo(9);
        assertThat(progress.courses()).allMatch(MicroDegreeProgressResponse.CourseItem::counted);
    }

    @Test
    void missingMandatoryBlocksEvenWithEnoughCourses() {
        MicroDegreeProgressResponse progress = calculate(degree(1L, 3, 9),
                List.of(mandatory("A1"), optional("B1"), optional("B2"), optional("B3")),
                List.of(taken("B1"), taken("B2"), taken("B3")));

        assertThat(progress.earnedCourses()).isEqualTo(3);
        assertThat(progress.unmet()).containsExactly("REQUIRED_COURSE");
    }

    @Test
    void fewerCoursesThanRequiredIsUnmet() {
        MicroDegreeProgressResponse progress = calculate(degree(1L, 3, 9),
                List.of(optional("B1"), optional("B2"), optional("B3"), optional("B4")), List.of(taken("B1"), taken("B2")));

        assertThat(progress.earned()).isEqualTo(6);
        assertThat(progress.unmet()).containsExactly("COURSE_COUNT");
    }

    // 자료의 "그룹 최대 범위 안" 을 그대로 하면 한 과목 더 들은 학생이 미충족이 된다. 초과 과목은 세지 않을 뿐이다
    @Test
    void goingOverGroupMaxOnlyStopsCounting() {
        MicroDegreeProgressResponse progress = calculate(degree(1L, 3, 9),
                List.of(optional("B1"), optional("B2"), grouped("G1", "A", 0, 1), grouped("G2", "A", 0, 1)),
                List.of(taken("B1"), taken("B2"), taken("G1"), taken("G2")));

        assertThat(progress.completed()).isTrue();
        assertThat(progress.courses()).filteredOn(c -> c.courseCode().equals("G2")).singleElement()
                .satisfies(c -> assertThat(c.counted()).isFalse());
    }

    @Test
    void courseOverGroupMaxDoesNotFillTheCount() {
        MicroDegreeProgressResponse progress = calculate(degree(1L, 3, 9),
                List.of(optional("B1"), grouped("G1", "A", 0, 1), grouped("G2", "A", 0, 1)),
                List.of(taken("B1"), taken("G1"), taken("G2")));

        assertThat(progress.earnedCourses()).isEqualTo(2);
        assertThat(progress.unmet()).containsExactly("COURSE_COUNT");
    }

    @Test
    void groupMinimumIsRequiredEvenWithEnoughCourses() {
        MicroDegreeProgressResponse progress = calculate(degree(1L, 3, 9),
                List.of(optional("B1"), optional("B2"), optional("B3"), grouped("G1", "A", 1, 1)),
                List.of(taken("B1"), taken("B2"), taken("B3")));

        assertThat(progress.unmet()).containsExactly("GROUP_MINIMUM");
    }

    // 의료인공지능처럼 그룹 최대가 없으면 다 센다
    @Test
    void groupWithoutMaxCountsEverything() {
        MicroDegreeProgressResponse progress = calculate(degree(1L, 4, 12),
                List.of(grouped("G1", "A", 1, null), grouped("G2", "B", 1, null), grouped("G3", "B", 1, null), grouped("G4", "C", 1, null)),
                List.of(taken("G1"), taken("G2"), taken("G3"), taken("G4")));

        assertThat(progress.completed()).isTrue();
    }

    @Test
    void departmentRuleNeedsTwoDepartmentsEvenAtFullCredits() {
        MicroDegree degree = degree(1L, 3, 9);
        given(degree.getMinDepartments()).willReturn(2);
        List<MicroDegreeCourse> rows = List.of(optional("V1", "시각디자인학과"), optional("V2", "시각디자인학과"),
                optional("V3", "시각디자인학과"), optional("C1", "문화엔터테인먼트학과"));

        MicroDegreeProgressResponse sameDepartment = calculate(degree, rows, List.of(taken("V1"), taken("V2"), taken("V3")));
        assertThat(sameDepartment.earned()).isEqualTo(9);
        assertThat(sameDepartment.completed()).isFalse();
        assertThat(sameDepartment.unmet()).containsExactly("DEPARTMENT_COUNT");

        assertThat(calculate(degree, rows, List.of(taken("V1"), taken("V2"), taken("C1"))).completed()).isTrue();
    }

    @Test
    void oldOrMistypedCodeMatchesByAlias() {
        MicroDegreeCourse row = optional("SWCON212");
        given(row.getAliasCode()).willReturn("SWCOM212");

        assertThat(calculate(degree(1L, 1, 3), List.of(row), List.of(taken("SWCOM212"))).completed()).isTrue();
    }

    @Test
    void rowWithoutCodeMatchesByNameEvenIfRecordHasAnotherSchoolsCode() {
        MicroDegreeCourse row = lenientMock(MicroDegreeCourse.class);
        given(row.getCourseName()).willReturn("버추얼3D캐릭터모델링");
        given(row.getDepartmentName()).willReturn("계원예술대학교");

        assertThat(calculate(degree(1L, 1, 3), List.of(row), List.of(course("KW100", "버추얼 3D 캐릭터 모델링", 3, "A0", 2026, Term.FIRST)))
                .completed()).isTrue();
    }

    @Test
    void recordWithoutCodeMatchesByNameButDifferentCodeDoesNot() {
        MicroDegreeCourse row = optional("AI1002");
        given(row.getCourseName()).willReturn("인공지능프로그래밍");

        assertThat(calculate(degree(1L, 1, 3), List.of(row), List.of(course(null, "인공지능 프로그래밍", 3, "A0", 2026, Term.FIRST))).completed()).isTrue();
        assertThat(calculate(degree(1L, 1, 3), List.of(row), List.of(course("OLD100", "인공지능프로그래밍", 3, "A0", 2026, Term.FIRST))).completed()).isFalse();
    }

    // 운영지침 제5조⑤: 같은 과목은 MD 1개에만
    @Test
    void sharedCourseCountsOnlyInFirstRegistered() {
        MicroDegree first = degree(1L, 1, 3);
        MicroDegree second = degree(2L, 1, 3);
        Map<Long, List<MicroDegreeCourse>> rows = Map.of(1L, List.of(optional("S1")), 2L, List.of(optional("S1")));

        List<MicroDegreeProgressResponse> result = MicroDegreeCalculation.calculate(List.of(first, second), rows, List.of(taken("S1")));

        assertThat(result.get(0).completed()).isTrue();
        assertThat(result.get(1).completed()).isFalse();
        assertThat(result.get(1).courses()).singleElement().satisfies(c -> assertThat(c.counted()).isFalse());
    }

    @Test
    void courseNotCountedInFirstStaysAvailableForNext() {
        MicroDegree first = degree(1L, 1, 3);
        MicroDegree second = degree(2L, 1, 3);
        Map<Long, List<MicroDegreeCourse>> rows = Map.of(
                1L, List.of(grouped("G1", "A", 0, 1), grouped("G2", "A", 0, 1)),
                2L, List.of(optional("G2")));

        List<MicroDegreeProgressResponse> result = MicroDegreeCalculation.calculate(List.of(first, second), rows, List.of(taken("G1"), taken("G2")));

        assertThat(result.get(1).completed()).isTrue();
    }

    @Test
    void earnedUsesMyCreditsAndIsCappedAtRequired() {
        MicroDegree degree = degree(1L, 4, 11);
        List<MicroDegreeCourse> rows = List.of(optional("B1"), optional("B2"), optional("B3"), optional("B4"), optional("B5"));

        MicroDegreeProgressResponse withTwoCredit = calculate(degree, rows, List.of(taken("B1"), taken("B2"), taken("B3"),
                course("B4", "B4", 2, "A0", 2026, Term.FIRST)));
        assertThat(withTwoCredit.earned()).isEqualTo(11);
        assertThat(withTwoCredit.completed()).isTrue();

        MicroDegreeProgressResponse overTaken = calculate(degree, rows,
                List.of(taken("B1"), taken("B2"), taken("B3"), taken("B4"), taken("B5")));
        assertThat(overTaken.earned()).isEqualTo(11);
        assertThat(overTaken.earnedCourses()).isEqualTo(4);
    }

    @Test
    void periodSpansCountedCoursesInTimeOrder() {
        MicroDegreeProgressResponse progress = calculate(degree(1L, 3, 9), List.of(optional("B1"), optional("B2"), optional("B3")), List.of(
                course("B1", "B1", 3, "A0", 2027, Term.FIRST),
                course("B2", "B2", 3, "A0", 2026, Term.SECOND),
                course("B3", "B3", 3, "A0", 2026, Term.WINTER)));

        assertThat(progress.period()).isEqualTo("2026. 2학기 ~ 2027. 1학기");
    }

    @Test
    void periodOfOneTermIsSingle() {
        assertThat(calculate(degree(1L, 1, 3), List.of(optional("B1")), List.of(course("B1", "B1", 3, "A0", 2026, Term.SECOND))).period())
                .isEqualTo("2026. 2학기");
    }

    // ---------- 서비스

    @Test
    void getMineUsesRecognizedCoursesAndRegisteredOrder() {
        MicroDegree degree = degree(1L, 2, 6);
        MicroDegreeCourse f = optional("F1");
        MicroDegreeCourse retake = optional("R1");
        given(f.getMicroDegree()).willReturn(degree);
        given(retake.getMicroDegree()).willReturn(degree);
        UserMicroDegree mine = UserMicroDegree.builder().microDegree(degree).build();
        given(userMicroDegreeRepository.findByUserIdOrderByIdAsc(1L)).willReturn(List.of(mine));
        given(microDegreeCourseRepository.findByMicroDegreeIdInOrderByIdAsc(List.of(1L))).willReturn(List.of(f, retake));
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of(
                course("F1", "F1", 3, "F", 2026, Term.FIRST),
                course("R1", "R1", 3, "C0", 2025, Term.FIRST),
                course("R1", "R1", 3, "A0", 2026, Term.FIRST)));

        MicroDegreeProgressResponse progress = microDegreeService.getMine(1L).get(0);

        assertThat(progress.earnedCourses()).isEqualTo(1);
        assertThat(progress.courses()).singleElement().satisfies(c -> assertThat(c.grade()).isEqualTo("A0"));
    }

    @Test
    void addSavesRegistration() {
        MicroDegree degree = degree(7L, 3, 9);
        User user = user();
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(userMicroDegreeRepository.findByUserIdOrderByIdAsc(1L)).willReturn(List.of());
        given(microDegreeRepository.findById(7L)).willReturn(Optional.of(degree));
        given(microDegreeCourseRepository.findByMicroDegreeIdInOrderByIdAsc(anyList())).willReturn(List.of());
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of());

        microDegreeService.add(1L, new MicroDegreeAddRequest(7L));

        verify(userMicroDegreeRepository).save(any(UserMicroDegree.class));
    }

    @Test
    void addingFourthFails() {
        User user = user();
        List<UserMicroDegree> mine = List.of(registered(1L), registered(2L), registered(3L));
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(userMicroDegreeRepository.findByUserIdOrderByIdAsc(1L)).willReturn(mine);

        assertAddFails(4L, ErrorCode.INVALID_INPUT);
    }

    @Test
    void addingTheSameDegreeTwiceFails() {
        User user = user();
        List<UserMicroDegree> mine = List.of(registered(7L));
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(userMicroDegreeRepository.findByUserIdOrderByIdAsc(1L)).willReturn(mine);

        assertAddFails(7L, ErrorCode.INVALID_INPUT);
    }

    @Test
    void addingUnknownDegreeFails() {
        User user = user();
        given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(userMicroDegreeRepository.findByUserIdOrderByIdAsc(1L)).willReturn(List.of());
        given(microDegreeRepository.findById(99L)).willReturn(Optional.empty());

        assertAddFails(99L, ErrorCode.NOT_FOUND);
    }

    @Test
    void removeDeletesOnlyMyRegistration() {
        UserMicroDegree mine = registered(7L);
        given(userMicroDegreeRepository.findByUserIdAndMicroDegreeId(1L, 7L)).willReturn(Optional.of(mine));

        microDegreeService.remove(1L, 7L);

        verify(userMicroDegreeRepository).delete(mine);
    }

    @Test
    void removingUnregisteredDegreeFails() {
        given(userMicroDegreeRepository.findByUserIdAndMicroDegreeId(1L, 7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> microDegreeService.remove(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        verify(userMicroDegreeRepository, never()).delete(any());
    }

    private void assertAddFails(long microDegreeId, ErrorCode errorCode) {
        assertThatThrownBy(() -> microDegreeService.add(1L, new MicroDegreeAddRequest(microDegreeId)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
        verify(userMicroDegreeRepository, never()).save(any());
    }

    private static MicroDegreeProgressResponse calculate(MicroDegree degree, List<MicroDegreeCourse> rows, List<CompletedCourse> courses) {
        return MicroDegreeCalculation.calculate(List.of(degree), Map.of(degree.getId(), rows), courses).get(0);
    }

    private static MicroDegree degree(long id, int courseCount, int credits) {
        MicroDegree degree = lenientMock(MicroDegree.class);
        given(degree.getId()).willReturn(id);
        given(degree.getRequiredCourseCount()).willReturn(courseCount);
        given(degree.getRequiredCredits()).willReturn(credits);
        return degree;
    }

    private static UserMicroDegree registered(long microDegreeId) {
        MicroDegree degree = degree(microDegreeId, 3, 9);
        return UserMicroDegree.builder().microDegree(degree).build();
    }

    private static MicroDegreeCourse mandatory(String code) {
        MicroDegreeCourse row = optional(code);
        given(row.isMandatory()).willReturn(true);
        return row;
    }

    private static MicroDegreeCourse optional(String code) {
        return optional(code, "학과" + code.charAt(0));
    }

    private static MicroDegreeCourse optional(String code, String department) {
        MicroDegreeCourse row = lenientMock(MicroDegreeCourse.class);
        given(row.getCourseCode()).willReturn(code);
        given(row.getCourseName()).willReturn(code);
        given(row.getDepartmentName()).willReturn(department);
        return row;
    }

    private static MicroDegreeCourse grouped(String code, String group, Integer min, Integer max) {
        MicroDegreeCourse row = optional(code);
        given(row.getGroupKey()).willReturn(group);
        given(row.getGroupMin()).willReturn(min);
        given(row.getGroupMax()).willReturn(max);
        return row;
    }

    private static CompletedCourse taken(String code) {
        return course(code, code, 3, "A0", 2026, Term.FIRST);
    }

    private static CompletedCourse course(String code, String name, int credits, String grade, int year, Term term) {
        return CompletedCourse.builder().courseCode(code).courseName(name).credits(credits)
                .classification(Classification.GENERAL_ELECTIVE).grade(grade).academicYear(year).term(term).build();
    }

    private static User user() {
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은").build();
    }

    // 기준정보 엔티티는 생성자가 막혀 있어 Mock 으로 만든다 (쓰지 않는 값도 있어 lenient)
    private static <T> T lenientMock(Class<T> type) {
        return mock(type, withSettings().strictness(Strictness.LENIENT));
    }
}
