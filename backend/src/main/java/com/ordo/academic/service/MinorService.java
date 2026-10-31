package com.ordo.academic.service;

import com.ordo.academic.domain.UserMinor;
import com.ordo.academic.dto.MinorRequest;
import com.ordo.academic.dto.MinorResponse;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.academic.repository.UserMinorRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MinorService {

    private static final List<Classification> CANDIDATE_CLASSIFICATIONS =
            List.of(Classification.MAJOR_REQUIRED, Classification.MAJOR_ELECTIVE);

    private final UserRepository userRepository;
    private final UserMinorRepository userMinorRepository;
    private final MajorRepository majorRepository;
    private final GraduationRequirementRepository graduationRequirementRepository;
    private final CourseRepository courseRepository;
    private final CompletedCourseRepository completedCourseRepository;

    public MinorResponse get(Long userId) {
        return userMinorRepository.findByUserId(userId).map(this::response).orElse(null);
    }

    @Transactional
    public MinorResponse register(Long userId, MinorRequest request) {
        User user = lockedUser(userId);
        if (user.getMajor() == null || user.getAdmissionYear() == null) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE);
        }
        if (user.getMajor().getId().equals(request.majorId())
                || user.getDoubleMajor() != null && user.getDoubleMajor().getId().equals(request.majorId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Major major = majorRepository.findById(request.majorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MAJOR_NOT_FOUND));
        requirement(major.getId(), user.getAdmissionYear());
        UserMinor minor = userMinorRepository.findByUserId(userId)
                .orElseGet(() -> UserMinor.builder().user(user).major(major).build());
        minor.changeMajor(major);
        userMinorRepository.save(minor);
        return response(minor);
    }

    @Transactional
    public void remove(Long userId) {
        lockedUser(userId);
        userMinorRepository.findByUserId(userId).ifPresent(userMinorRepository::delete);
    }

    /** 주전공·학번을 바꾸는 호출자는 같은 사용자의 쓰기 잠금을 유지한다. */
    public void validateProfileChange(Long userId, Long majorId, int admissionYear) {
        userMinorRepository.findByUserId(userId).ifPresent(minor -> {
            if (minor.getMajor().getId().equals(majorId)
                    || supportedRequirement(minor.getMajor().getId(), admissionYear).isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        });
    }

    /** 복수전공과 같은 전공을 부전공으로 추적할 수 없다. 호출자는 사용자 쓰기 잠금을 유지한다. */
    public void validateDoubleMajor(Long userId, Long majorId) {
        if (userMinorRepository.findByUserId(userId).filter(minor -> minor.getMajor().getId().equals(majorId)).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private MinorResponse response(UserMinor minor) {
        User user = minor.getUser();
        List<Course> first = courses(user.getMajor(), AcademicProgressService.MAJOR_CLASSIFICATIONS);
        List<Course> second = courses(user.getDoubleMajor(), AcademicProgressService.MAJOR_CLASSIFICATIONS);
        List<Course> candidates = courses(minor.getMajor(), CANDIDATE_CLASSIFICATIONS);
        boolean overlapAvailable = !first.isEmpty() && (user.getDoubleMajor() == null || !second.isEmpty());
        return MinorCourseCalculation.calculate(user.getAdmissionYear(), minor.getMajor(),
                requirement(minor.getMajor().getId(), user.getAdmissionYear()), candidates, first, second,
                overlapAvailable, completedCourseRepository.findByUserId(user.getId()));
    }

    private List<Course> courses(Major major, List<Classification> classifications) {
        return major == null || major.getCourseUnit() == null ? List.of()
                : courseRepository.findByUnitNameAndClassificationIn(major.getCourseUnit(), classifications);
    }

    private GraduationRequirement requirement(Long majorId, int admissionYear) {
        return supportedRequirement(majorId, admissionYear)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIREMENT_NOT_FOUND));
    }

    private Optional<GraduationRequirement> supportedRequirement(Long majorId, int admissionYear) {
        return graduationRequirementRepository.findByMajorIdAndAdmissionYear(majorId, admissionYear)
                .filter(requirement -> requirement.getMinorTotalCredits() != null && requirement.getMinorTotalCredits() > 0);
    }

    private User lockedUser(Long userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
