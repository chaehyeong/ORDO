package com.ordo.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.ordo.academic.dto.DoubleMajorRequest;
import com.ordo.academic.dto.DoubleMajorResponse;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class DoubleMajorServiceTest {

    static final long FIRST = 35L;
    static final long SECOND = 12L;

    @Mock
    UserRepository userRepository;
    @Mock
    MajorRepository majorRepository;
    @Mock
    AcademicProgressService academicProgressService;
    @InjectMocks
    DoubleMajorService doubleMajorService;

    @Test
    void getIsNullWithoutDoubleMajor() {
        given(academicProgressService.findDoubleMajor(1L)).willReturn(Optional.empty());

        assertThat(doubleMajorService.get(1L)).isNull();
    }

    @Test
    void registerStoresMajorAndReturnsProgress() {
        User user = user(major(FIRST), 2026);
        Major second = major(SECOND);
        DoubleMajorResponse progress = mock(DoubleMajorResponse.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(majorRepository.findById(SECOND)).willReturn(Optional.of(second));
        given(majorRepository.countGraduationRequirements(SECOND, 2026)).willReturn(1L);
        given(academicProgressService.findDoubleMajor(1L)).willReturn(Optional.of(progress));

        assertThat(doubleMajorService.register(1L, new DoubleMajorRequest(SECOND))).isSameAs(progress);
        assertThat(user.getDoubleMajor()).isSameAs(second);
    }

    // 주전공 목록과 달리 융합전공을 막지 않는다 (findSelectable 을 쓰지 않음)
    @Test
    void convergenceMajorCanBeRegistered() {
        User user = user(major(FIRST), 2026);
        Major convergence = major(SECOND);
        given(convergence.isConvergence()).willReturn(true);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(majorRepository.findById(SECOND)).willReturn(Optional.of(convergence));
        given(majorRepository.countGraduationRequirements(SECOND, 2026)).willReturn(1L);
        given(academicProgressService.findDoubleMajor(1L)).willReturn(Optional.empty());

        doubleMajorService.register(1L, new DoubleMajorRequest(SECOND));

        assertThat(user.getDoubleMajor()).isSameAs(convergence);
    }

    @Test
    void registerWithoutProfileFails() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user(null, null)));

        assertFails(ErrorCode.PROFILE_INCOMPLETE);
    }

    @Test
    void sameMajorAsFirstFails() {
        User user = user(major(SECOND), 2026);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertFails(ErrorCode.INVALID_INPUT);
    }

    @Test
    void unknownMajorFails() {
        User user = user(major(FIRST), 2026);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(majorRepository.findById(SECOND)).willReturn(Optional.empty());

        assertFails(ErrorCode.MAJOR_NOT_FOUND);
    }

    @Test
    void majorWithoutRequirementForAdmissionYearFails() {
        User user = user(major(FIRST), 2020);
        Major second = major(SECOND);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(majorRepository.findById(SECOND)).willReturn(Optional.of(second));
        given(majorRepository.countGraduationRequirements(SECOND, 2020)).willReturn(0L);

        assertFails(ErrorCode.MAJOR_NOT_FOUND);
    }

    @Test
    void removeClearsDoubleMajorAndIsSafeWhenNoneIsSet() {
        User user = user(major(FIRST), 2026);
        user.changeDoubleMajor(major(SECOND));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        doubleMajorService.remove(1L);
        doubleMajorService.remove(1L);

        assertThat(user.getDoubleMajor()).isNull();
    }

    private void assertFails(ErrorCode errorCode) {
        assertThatThrownBy(() -> doubleMajorService.register(1L, new DoubleMajorRequest(SECOND)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    private static User user(Major major, Integer admissionYear) {
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은")
                .major(major).admissionYear(admissionYear).build();
    }

    // 기준정보 엔티티는 생성자가 막혀 있어 Mock 으로 만든다 (쓰지 않는 값도 있어 lenient)
    private static Major major(long id) {
        Major major = mock(Major.class, withSettings().strictness(Strictness.LENIENT));
        given(major.getId()).willReturn(id);
        return major;
    }
}
