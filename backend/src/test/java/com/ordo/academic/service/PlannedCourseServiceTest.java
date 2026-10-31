package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ordo.academic.domain.PlannedCourse;
import com.ordo.academic.dto.PlannedCoursesRequest;
import com.ordo.academic.dto.PlannedCoursesResponse;
import com.ordo.academic.dto.PlannedCoursesResponse.Course;
import com.ordo.academic.dto.PlannedCoursesResponse.PlannedTerm;
import com.ordo.academic.repository.PlannedCourseRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlannedCourseServiceTest {

    @Mock
    PlannedCourseRepository plannedCourseRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    PlannedCourseService plannedCourseService;

    @Test
    void plansAreGroupedByTermInTimeOrderKeepingSavedOrderInsideATerm() {
        given(plannedCourseRepository.findByUserIdOrderByIdAsc(1L)).willReturn(List.of(
                planned(2028, Term.FIRST, "사진1", 3),
                planned(2027, Term.SECOND, "성찰과표현", 3),
                planned(2027, Term.FIRST, "UX/UI 디자인", 3),
                planned(2027, Term.SECOND, "대학영어", 2),
                planned(2027, Term.SUMMER, "계절 과목", 1)));

        PlannedCoursesResponse response = plannedCourseService.getPlans(1L);

        assertThat(response.totalCredits()).isEqualTo(12);
        assertThat(response.terms()).extracting(PlannedTerm::year, PlannedTerm::term, PlannedTerm::label, PlannedTerm::credits)
                .containsExactly(tuple(2027, Term.FIRST, "2027년 1학기", 3),
                        tuple(2027, Term.SUMMER, "2027년 여름계절학기", 1),
                        tuple(2027, Term.SECOND, "2027년 2학기", 5),
                        tuple(2028, Term.FIRST, "2028년 1학기", 3));
        assertThat(response.terms().get(2).courses()).extracting(Course::courseName).containsExactly("성찰과표현", "대학영어");
    }

    @Test
    void noPlansGiveEmptyResponse() {
        given(plannedCourseRepository.findByUserIdOrderByIdAsc(1L)).willReturn(List.of());

        assertThat(plannedCourseService.getPlans(1L)).isEqualTo(new PlannedCoursesResponse(0, List.of()));
    }

    @Test
    void replaceAllDeletesOldPlansAndSavesTheNewListInOrder() {
        List<PlannedCourse> old = List.of(planned(2027, Term.FIRST, "예전 과목", 3));
        givenExisting(old);

        PlannedCoursesResponse response = plannedCourseService.replaceAll(1L, request(
                course(2027, Term.SECOND, "성찰과표현", null, Classification.GEN_REQUIRED, 3),
                course(2027, Term.FIRST, "UX/UI 디자인", "VID2001", Classification.MAJOR_ELECTIVE, 3)));

        verify(plannedCourseRepository).deleteAll(old);
        verify(plannedCourseRepository).saveAll(argThat((List<PlannedCourse> saved) -> saved.size() == 2
                && saved.get(0).getCourseName().equals("성찰과표현") && saved.get(0).getUser() != null));
        assertThat(response.totalCredits()).isEqualTo(6);
        assertThat(response.terms()).extracting(PlannedTerm::term).containsExactly(Term.FIRST, Term.SECOND);
        assertThat(response.terms().get(0).courses()).containsExactly(
                new Course(null, "UX/UI 디자인", "VID2001", Classification.MAJOR_ELECTIVE, 3));
    }

    @Test
    void emptyListResetsThePlan() {
        List<PlannedCourse> old = List.of(planned(2027, Term.FIRST, "예전 과목", 3));
        givenExisting(old);

        PlannedCoursesResponse response = plannedCourseService.replaceAll(1L, request());

        verify(plannedCourseRepository).deleteAll(old);
        assertThat(response).isEqualTo(new PlannedCoursesResponse(0, List.of()));
    }

    @Test
    void sameCodeTwiceInATermChangesNothing() {
        given(userRepository.getReferenceById(1L)).willReturn(user());

        assertInvalidAndNothingChanged(request(
                course(2027, Term.FIRST, "타이포그래피1", "VID1004", null, 3),
                course(2027, Term.FIRST, "타이포그래피 1", "VID1004", null, 3)));
    }

    @Test
    void sameCodeInLowercaseInATermChangesNothing() {
        given(userRepository.getReferenceById(1L)).willReturn(user());

        assertInvalidAndNothingChanged(request(
                course(2027, Term.FIRST, "타이포그래피1", "VID1004", null, 3),
                course(2027, Term.FIRST, "타이포그래피1", "vid1004", null, 3)));
    }

    @Test
    void sameNameWithoutCodeTwiceInATermChangesNothing() {
        given(userRepository.getReferenceById(1L)).willReturn(user());

        assertInvalidAndNothingChanged(request(
                course(2027, Term.FIRST, "성찰과표현", null, null, 3),
                course(2027, Term.FIRST, " 성찰과 표현 ", "  ", null, 3)));
    }

    @Test
    void sameCourseInAnotherTermIsAllowed() {
        givenExisting(List.of());

        PlannedCoursesResponse response = plannedCourseService.replaceAll(1L, request(
                course(2027, Term.FIRST, "사진1", "VID1003", null, 3),
                course(2027, Term.SECOND, "사진1", "VID1003", null, 3),
                course(2028, Term.FIRST, "사진1", "VID1003", null, 3)));

        assertThat(response.terms()).hasSize(3);
    }

    @Test
    void valuesAreCleanedAndClassificationAndZeroCreditsAreAllowed() {
        givenExisting(List.of());

        PlannedCoursesResponse response = plannedCourseService.replaceAll(1L, request(
                course(2027, Term.FIRST, "  졸업논문  ", " VID4009 ", Classification.MAJOR_REQUIRED, 0),
                course(2027, Term.FIRST, "직접 추가", " ", null, 2)));

        assertThat(response.terms().get(0).courses()).containsExactly(
                new Course(null, "졸업논문", "VID4009", Classification.MAJOR_REQUIRED, 0),
                new Course(null, "직접 추가", null, null, 2));
    }

    private void assertInvalidAndNothingChanged(PlannedCoursesRequest request) {
        assertThatThrownBy(() -> plannedCourseService.replaceAll(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verify(plannedCourseRepository, never()).deleteAll(any());
        verify(plannedCourseRepository, never()).saveAll(any());
    }

    private void givenExisting(List<PlannedCourse> existing) {
        given(userRepository.getReferenceById(1L)).willReturn(user());
        given(plannedCourseRepository.findByUserIdOrderByIdAsc(1L)).willReturn(existing);
    }

    private static PlannedCoursesRequest request(PlannedCoursesRequest.Course... courses) {
        return new PlannedCoursesRequest(List.of(courses));
    }

    private static PlannedCoursesRequest.Course course(int year, Term term, String name, String code,
                                                       Classification classification, int credits) {
        return new PlannedCoursesRequest.Course(year, term, name, code, classification, credits);
    }

    private static PlannedCourse planned(int year, Term term, String name, int credits) {
        return PlannedCourse.builder().academicYear(year).term(term).courseName(name).credits(credits).build();
    }

    private static User user() {
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은").build();
    }
}
