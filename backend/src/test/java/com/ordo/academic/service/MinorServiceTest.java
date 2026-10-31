package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ordo.academic.domain.UserMinor;
import com.ordo.academic.dto.MinorRequest;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.academic.repository.UserMinorRepository;
import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.CourseRepository;
import com.ordo.catalog.repository.GraduationRequirementRepository;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MinorServiceTest {

    @Mock UserRepository users;
    @Mock UserMinorRepository minors;
    @Mock MajorRepository majors;
    @Mock GraduationRequirementRepository requirements;
    @Mock CourseRepository courses;
    @Mock CompletedCourseRepository completed;
    @InjectMocks MinorService service;

    @Test
    void unregisteredUserGetsNull() {
        assertThat(service.get(1L)).isNull();
        verify(minors).findByUserId(1L);
    }

    @Test
    void registerStoresOneMinorAndReturnsProvisionalRequirements() {
        User user = user();
        Major minor = major(14L, "minor");
        prepareRegistration(user, minor);
        prepareResponse(user, minor);

        var response = service.register(1L, new MinorRequest(14L));

        assertThat(response.major().id()).isEqualTo(14L);
        assertThat(response.requirements().totalCredits()).isEqualTo(21);
        assertThat(response.reviewRequired()).isTrue();
        assertThat(response.remainingCourses()).hasSize(1);
        verify(minors).save(any(UserMinor.class));
    }

    @Test
    void changingMinorUpdatesExistingRegistration() {
        User user = user();
        Major next = major(14L, "minor");
        UserMinor existing = UserMinor.builder().user(user).major(major(34L, "old")).build();
        given(minors.findByUserId(1L)).willReturn(Optional.of(existing));
        prepareRegistration(user, next);
        prepareResponse(user, next);

        service.register(1L, new MinorRequest(14L));

        assertThat(existing.getMajor()).isSameAs(next);
        verify(minors).save(existing);
    }

    @Test
    void sameAsPrimaryFailsWithoutSaving() {
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user()));
        assertRegisterFails(35L, ErrorCode.INVALID_INPUT);
    }

    @Test
    void sameAsDoubleMajorFailsWithoutSaving() {
        User user = user();
        user.changeDoubleMajor(major(14L, "double"));
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        assertRegisterFails(14L, ErrorCode.INVALID_INPUT);
    }

    @Test
    void missingProfileIsRejected() {
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(User.builder().name("미완성").build()));
        assertRegisterFails(14L, ErrorCode.PROFILE_INCOMPLETE);
    }

    @Test
    void unknownUserIsNotFound() {
        assertRegisterFails(14L, ErrorCode.NOT_FOUND);
    }

    @Test
    void unknownMajorIsNotFound() {
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user()));
        assertRegisterFails(14L, ErrorCode.MAJOR_NOT_FOUND);
    }

    @Test
    void convergenceMajorWithExplicitMinorRequirementsCanBeTracked() {
        User user = user();
        Major convergence = major(14L, "minor");
        ReflectionTestUtils.setField(convergence, "convergence", true);
        prepareRegistration(user, convergence);
        prepareResponse(user, convergence);
        assertThat(service.register(1L, new MinorRequest(14L)).major().id()).isEqualTo(14L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void missingOrInvalidTotalNeverMeansZeroCreditCompletion(Integer total) {
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user()));
        given(majors.findById(14L)).willReturn(Optional.of(major(14L, "minor")));
        given(requirements.findByMajorIdAndAdmissionYear(14L, 2026)).willReturn(Optional.of(requirement(total)));
        assertRegisterFails(14L, ErrorCode.REQUIREMENT_NOT_FOUND);
    }

    @Test
    void admissionYearWithoutRequirementIsRejected() {
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user()));
        given(majors.findById(14L)).willReturn(Optional.of(major(14L, "minor")));
        assertRegisterFails(14L, ErrorCode.REQUIREMENT_NOT_FOUND);
    }

    @Test
    void removeIsIdempotentAndOnlyLooksUpCurrentUser() {
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user()));
        service.remove(1L);
        verify(minors).findByUserId(1L);
        verify(minors, never()).delete(any());
    }

    @Test
    void profileChangeCannotBecomeTheMinor() {
        given(minors.findByUserId(1L)).willReturn(Optional.of(UserMinor.builder().user(user()).major(major(14L, "minor")).build()));
        assertThatThrownBy(() -> service.validateProfileChange(1L, 14L, 2026))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void profileChangeCannotLoseMinorRequirement() {
        given(minors.findByUserId(1L)).willReturn(Optional.of(UserMinor.builder().user(user()).major(major(14L, "minor")).build()));
        assertThatThrownBy(() -> service.validateProfileChange(1L, 35L, 2010))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void doubleMajorCannotBecomeTheMinor() {
        given(minors.findByUserId(1L)).willReturn(Optional.of(UserMinor.builder().user(user()).major(major(14L, "minor")).build()));
        assertThatThrownBy(() -> service.validateDoubleMajor(1L, 14L))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private void assertRegisterFails(long majorId, ErrorCode code) {
        assertThatThrownBy(() -> service.register(1L, new MinorRequest(majorId)))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
        verify(minors, never()).save(any());
    }

    private void prepareRegistration(User user, Major major) {
        given(users.findByIdForUpdate(1L)).willReturn(Optional.of(user));
        given(majors.findById(major.getId())).willReturn(Optional.of(major));
        given(requirements.findByMajorIdAndAdmissionYear(major.getId(), 2026)).willReturn(Optional.of(requirement(21)));
    }

    private void prepareResponse(User user, Major minor) {
        given(courses.findByUnitNameAndClassificationIn(org.mockito.ArgumentMatchers.eq("primary"), any()))
                .willReturn(List.of(MinorCourseCalculationTest.course("FIRST", "주전공")));
        given(courses.findByUnitNameAndClassificationIn(org.mockito.ArgumentMatchers.eq(minor.getCourseUnit()), any()))
                .willReturn(List.of(MinorCourseCalculationTest.course("MINOR", "후보")));
        given(completed.findByUserId(user.getId())).willReturn(List.of());
    }

    static User user() {
        User result = User.builder().name("검증").major(major(35L, "primary")).admissionYear(2026).build();
        ReflectionTestUtils.setField(result, "id", 1L);
        return result;
    }

    static Major major(Long id, String unit) {
        Major result = BeanUtils.instantiateClass(Major.class);
        ReflectionTestUtils.setField(result, "id", id);
        ReflectionTestUtils.setField(result, "displayName", unit);
        ReflectionTestUtils.setField(result, "courseUnit", unit);
        return result;
    }

    static GraduationRequirement requirement(Integer total) {
        GraduationRequirement result = BeanUtils.instantiateClass(GraduationRequirement.class);
        ReflectionTestUtils.setField(result, "minorTotalCredits", total);
        return result;
    }
}
