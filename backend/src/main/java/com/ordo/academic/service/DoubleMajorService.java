package com.ordo.academic.service;

import com.ordo.academic.dto.DoubleMajorRequest;
import com.ordo.academic.dto.DoubleMajorResponse;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 복수전공 등록·해제 (명세 4.6, T19). 오르도는 학교 승인 결과를 입력받으므로 다전공 금지 조합은 검사하지 않는다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DoubleMajorService {

    private final UserRepository userRepository;
    private final MajorRepository majorRepository;
    private final AcademicProgressService academicProgressService;
    private final MinorService minorService;

    /** 복수전공이 없으면 null */
    public DoubleMajorResponse get(Long userId) {
        return academicProgressService.findDoubleMajor(userId).orElse(null);
    }

    @Transactional
    public DoubleMajorResponse register(Long userId, DoubleMajorRequest request) {
        User user = getUser(userId);
        if (user.getMajor() == null || user.getAdmissionYear() == null) {
            throw new BusinessException(ErrorCode.PROFILE_INCOMPLETE);
        }
        if (user.getMajor().getId().equals(request.majorId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        minorService.validateDoubleMajor(userId, request.majorId());
        // findSelectable 은 융합전공을 빼므로 쓰지 않는다
        Major major = majorRepository.findById(request.majorId())
                .filter(m -> majorRepository.countGraduationRequirements(m.getId(), user.getAdmissionYear()) > 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.MAJOR_NOT_FOUND));
        user.changeDoubleMajor(major);
        return get(userId);
    }

    @Transactional
    public void remove(Long userId) {
        getUser(userId).changeDoubleMajor(null);
    }

    private User getUser(Long userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
