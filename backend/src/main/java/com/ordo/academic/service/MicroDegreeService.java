package com.ordo.academic.service;

import com.ordo.academic.domain.UserMicroDegree;
import com.ordo.academic.dto.MicroDegreeAddRequest;
import com.ordo.academic.dto.MicroDegreeProgressResponse;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.academic.repository.UserMicroDegreeRepository;
import com.ordo.catalog.domain.MicroDegree;
import com.ordo.catalog.domain.MicroDegreeCourse;
import com.ordo.catalog.repository.MicroDegreeCourseRepository;
import com.ordo.catalog.repository.MicroDegreeRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 마이크로디그리 등록·해제·진행 상황 (명세 4.6, T20). 신청은 학교 포털에서 하고 오르도는 진행 상황만 계산한다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MicroDegreeService {

    static final int MAX_MICRO_DEGREES = 3;  // 운영지침 제4조②

    private final UserRepository userRepository;
    private final UserMicroDegreeRepository userMicroDegreeRepository;
    private final MicroDegreeRepository microDegreeRepository;
    private final MicroDegreeCourseRepository microDegreeCourseRepository;
    private final CompletedCourseRepository completedCourseRepository;

    public List<MicroDegreeProgressResponse> getMine(Long userId) {
        List<MicroDegree> degrees = userMicroDegreeRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(UserMicroDegree::getMicroDegree).toList();
        Map<Long, List<MicroDegreeCourse>> courses = microDegreeCourseRepository
                .findByMicroDegreeIdInOrderByIdAsc(degrees.stream().map(MicroDegree::getId).toList()).stream()
                .collect(Collectors.groupingBy(course -> course.getMicroDegree().getId()));
        return MicroDegreeCalculation.calculate(degrees, courses,
                AcademicProgressService.recognize(completedCourseRepository.findByUserId(userId)));
    }

    /** 응답은 내 목록 전체: 등록하면 앞뒤 MD 의 과목 배정이 달라질 수 있다 */
    @Transactional
    public List<MicroDegreeProgressResponse> add(Long userId, MicroDegreeAddRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<UserMicroDegree> mine = userMicroDegreeRepository.findByUserIdOrderByIdAsc(userId);
        boolean duplicate = mine.stream().anyMatch(m -> m.getMicroDegree().getId().equals(request.microDegreeId()));
        if (duplicate || mine.size() >= MAX_MICRO_DEGREES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        MicroDegree degree = microDegreeRepository.findById(request.microDegreeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        userMicroDegreeRepository.save(UserMicroDegree.builder().user(user).microDegree(degree).build());
        return getMine(userId);
    }

    @Transactional
    public void remove(Long userId, Long microDegreeId) {
        userMicroDegreeRepository.delete(userMicroDegreeRepository.findByUserIdAndMicroDegreeId(userId, microDegreeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
    }
}
