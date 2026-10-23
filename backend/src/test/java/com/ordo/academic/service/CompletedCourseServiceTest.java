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
import com.ordo.academic.dto.CompletedCourseRequest;
import com.ordo.academic.dto.CompletedCourseResponse;
import com.ordo.academic.dto.CompletedCourseUpdateRequest;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.CourseRepository;
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
class CompletedCourseServiceTest {

    @Mock
    CompletedCourseRepository completedCourseRepository;
    @Mock
    CourseRepository courseRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    CompletedCourseService completedCourseService;

    final User user = computerScienceStudent();

    @Test
    void myMajorCourseIsClassifiedAutomatically() {
        givenUser();
        givenMasterCourse("CSE204", "자료구조", Classification.MAJOR_REQUIRED);
        givenSaveReturnsArgument();

        CompletedCourseResponse response = completedCourseService.create(1L,
                request("CSE204", null, Classification.GEN_FREE, null));  // 요청의 이수구분은 무시

        assertThat(response.classification()).isEqualTo(Classification.MAJOR_REQUIRED);
        assertThat(response.courseName()).isEqualTo("자료구조");
    }

    @Test
    void unknownCourseNeedsClassification() {
        givenUser();
        given(courseRepository.findFirstByUnitNameAndCourseCodeOrderByIdAsc("컴퓨터공학", "GEE1001"))
                .willReturn(Optional.empty());

        assertErrorCode(() -> completedCourseService.create(1L, request("GEE1001", "글쓰기", null, null)),
                ErrorCode.CLASSIFICATION_REQUIRED);
    }

    @Test
    void distributionCourseNeedsArea() {
        givenUser();
        givenSaveReturnsArgument();

        assertErrorCode(() -> completedCourseService.create(1L,
                request(null, "철학의 이해", Classification.GEN_DISTRIBUTION, null)), ErrorCode.DISTRIBUTION_AREA_REQUIRED);
        assertThat(completedCourseService.create(1L,
                request(null, "철학의 이해", Classification.GEN_DISTRIBUTION, 3)).distributionArea()).isEqualTo(3);
        assertThat(completedCourseService.create(1L,
                request(null, "봉사", Classification.GENERAL_ELECTIVE, 3)).distributionArea()).isNull();  // 배분이 아니면 버림
    }

    @Test
    void courseNameIsRequiredWhenNotAutoClassified() {
        givenUser();

        assertErrorCode(() -> completedCourseService.create(1L, request(null, " ", Classification.GEN_FREE, null)),
                ErrorCode.INVALID_INPUT);
    }

    @Test
    void bulkSavesNothingWhenOneIsInvalid() {
        givenUser();

        assertErrorCode(() -> completedCourseService.createAll(1L, List.of(
                request(null, "자유1", Classification.GEN_FREE, null),
                request(null, "분류 없음", null, null))), ErrorCode.CLASSIFICATION_REQUIRED);
        verify(completedCourseRepository, never()).saveAll(anyList());
    }

    @Test
    void coursesAreListedChronologically() {
        given(completedCourseRepository.findByUserId(1L)).willReturn(List.of(
                course("2학기", 2026, Term.SECOND), course("여름", 2026, Term.SUMMER),
                course("작년 겨울", 2025, Term.WINTER), course("1학기", 2026, Term.FIRST)));

        assertThat(completedCourseService.getCourses(1L)).extracting(CompletedCourseResponse::courseName)
                .containsExactly("작년 겨울", "1학기", "여름", "2학기");
    }

    @Test
    void updateClassifiesAgainWithMergedValues() {
        CompletedCourse course = course("자료구조", 2026, Term.FIRST);
        given(completedCourseRepository.findByIdAndUserId(7L, 1L)).willReturn(Optional.of(course));
        givenMasterCourse("CSE204", "자료구조", Classification.MAJOR_REQUIRED);

        completedCourseService.update(1L, 7L, new CompletedCourseUpdateRequest(
                "CSE204", null, null, null, null, "A+", null, null));

        assertThat(course.getClassification()).isEqualTo(Classification.MAJOR_REQUIRED);
        assertThat(course.getGrade()).isEqualTo("A+");
        assertThat(course.getCredits()).isEqualTo(3);  // 보내지 않은 값은 그대로
    }

    @Test
    void othersCourseIsNotFound() {
        given(completedCourseRepository.findByIdAndUserId(7L, 2L)).willReturn(Optional.empty());

        assertErrorCode(() -> completedCourseService.delete(2L, 7L), ErrorCode.COMPLETED_COURSE_NOT_FOUND);
        verify(completedCourseRepository, never()).delete(any());
    }

    private void givenUser() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
    }

    private void givenSaveReturnsArgument() {
        given(completedCourseRepository.save(any(CompletedCourse.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenMasterCourse(String code, String name, Classification classification) {
        Course course = mock(Course.class, withSettings().strictness(Strictness.LENIENT));
        given(course.getName()).willReturn(name);
        given(course.getClassification()).willReturn(classification);
        given(courseRepository.findFirstByUnitNameAndCourseCodeOrderByIdAsc("컴퓨터공학", code))
                .willReturn(Optional.of(course));
    }

    private void assertErrorCode(Runnable call, ErrorCode errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    private static CompletedCourseRequest request(String code, String name, Classification classification,
                                                  Integer area) {
        return new CompletedCourseRequest(code, name, 3, classification, area, "A0", 2026, Term.FIRST);
    }

    private CompletedCourse course(String name, int year, Term term) {
        return CompletedCourse.builder().user(user).courseName(name).credits(3).classification(Classification.GEN_FREE)
                .academicYear(year).term(term).build();
    }

    private static User computerScienceStudent() {
        Major major = mock(Major.class, withSettings().strictness(Strictness.LENIENT));
        given(major.getCourseUnit()).willReturn("컴퓨터공학");
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은")
                .admissionYear(2026).major(major).build();
    }
}
