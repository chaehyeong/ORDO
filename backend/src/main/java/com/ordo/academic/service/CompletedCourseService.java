package com.ordo.academic.service;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.CompletedCourseBulkRequest;
import com.ordo.academic.dto.CompletedCourseRequest;
import com.ordo.academic.dto.CompletedCourseResponse;
import com.ordo.academic.dto.CompletedCourseUpdateRequest;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.CourseRepository;
import com.ordo.global.common.AcademicTerm;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompletedCourseService {

    private final CompletedCourseRepository completedCourseRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    /** 판별 전후의 과목 값 묶음 */
    private record Fields(String courseCode, String courseName, int credits, Classification classification,
                          Integer distributionArea, String grade, int year, Term term) {
    }

    public List<CompletedCourseResponse> getCourses(Long userId) {
        return completedCourseRepository.findByUserId(userId).stream()
                .sorted(CompletedCourse.CHRONOLOGICAL)
                .map(CompletedCourseResponse::from)
                .toList();
    }

    @Transactional
    public CompletedCourseResponse create(Long userId, CompletedCourseRequest request) {
        return CompletedCourseResponse.from(completedCourseRepository.save(newCourse(getUser(userId), request, false)));
    }

    /** 하나라도 판별에 실패하면 예외로 전체 롤백. replaceTerms 학기의 기존 이수내역은 지우고 넣는다 */
    @Transactional
    public List<CompletedCourseResponse> createAll(Long userId, CompletedCourseBulkRequest request) {
        User user = getUser(userId);
        boolean keepClassification = Boolean.TRUE.equals(request.keepClassification());
        List<CompletedCourse> courses = request.courses().stream()
                .map(r -> newCourse(user, r, keepClassification))
                .toList();
        if (request.replaceTerms() != null && !request.replaceTerms().isEmpty()) {
            Set<AcademicTerm> terms = request.replaceTerms().stream()
                    .map(t -> new AcademicTerm(t.year(), t.term()))
                    .collect(Collectors.toSet());
            completedCourseRepository.deleteAll(completedCourseRepository.findByUserId(userId).stream()
                    .filter(c -> terms.contains(new AcademicTerm(c.getAcademicYear(), c.getTerm())))
                    .toList());
        }
        return completedCourseRepository.saveAll(courses).stream().map(CompletedCourseResponse::from).toList();
    }

    @Transactional
    public CompletedCourseResponse update(Long userId, Long courseId, CompletedCourseUpdateRequest request) {
        CompletedCourse course = getOwnCourse(userId, courseId);
        String courseCode = blankToNull(request.courseCode());
        Fields merged = new Fields(
                courseCode != null ? courseCode : course.getCourseCode(),
                request.courseName() != null ? request.courseName() : course.getCourseName(),
                request.credits() != null ? request.credits() : course.getCredits(),
                request.classification() != null ? request.classification() : course.getClassification(),
                request.distributionArea() != null ? request.distributionArea() : course.getDistributionArea(),
                request.grade() != null ? request.grade() : course.getGrade(),
                request.year() != null ? request.year() : course.getAcademicYear(),
                request.term() != null ? request.term() : course.getTerm());
        Fields f = resolve(course.getUser(), merged, false);
        course.change(f.courseCode(), f.courseName(), f.credits(), f.classification(), f.distributionArea(), f.grade(),
                f.year(), f.term());
        return CompletedCourseResponse.from(course);
    }

    @Transactional
    public void delete(Long userId, Long courseId) {
        completedCourseRepository.delete(getOwnCourse(userId, courseId));
    }

    private CompletedCourse newCourse(User user, CompletedCourseRequest request, boolean keepClassification) {
        Fields f = resolve(user, new Fields(blankToNull(request.courseCode()), request.courseName(), request.credits(),
                request.classification(), request.distributionArea(), request.grade(), request.year(), request.term()),
                keepClassification);
        return CompletedCourse.builder().user(user).courseCode(f.courseCode()).courseName(f.courseName())
                .credits(f.credits()).classification(f.classification()).distributionArea(f.distributionArea())
                .grade(f.grade()).academicYear(f.year()).term(f.term()).build();
    }

    // 명세 4.6 이수구분 자동 판별
    // 1. 내 전공 편성(course_unit)에 그 학수번호가 있으면 그 이수구분을 쓰고(요청값 무시), 과목명이 비었으면 채운다
    // 2. 아니면 요청의 이수구분 필수  3. 배분이수면 영역 1~5 필수 (다른 이수구분이면 영역은 버린다)
    // keepClassification(성적표 가져오기): 요청에 이수구분이 있으면 1번을 건너뛰고 요청값을 쓴다
    private Fields resolve(User user, Fields f, boolean keepClassification) {
        Optional<Course> master = findMasterCourse(user, f.courseCode());
        Classification classification = keepClassification && f.classification() != null
                ? f.classification()
                : master.map(Course::getClassification).orElse(f.classification());
        String courseName = blankToNull(f.courseName());
        if (courseName == null) {
            courseName = master.map(Course::getName).orElse(null);
        }
        if (classification == null) {
            throw new BusinessException(ErrorCode.CLASSIFICATION_REQUIRED);
        }
        if (courseName == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);  // 자동 판별도 안 되고 과목명도 없음
        }
        boolean distribution = classification == Classification.GEN_DISTRIBUTION;
        if (distribution && f.distributionArea() == null) {
            throw new BusinessException(ErrorCode.DISTRIBUTION_AREA_REQUIRED);
        }
        return new Fields(f.courseCode(), courseName, f.credits(), classification,
                distribution ? f.distributionArea() : null, f.grade(), f.year(), f.term());
    }

    private Optional<Course> findMasterCourse(User user, String courseCode) {
        Major major = user.getMajor();
        if (courseCode == null || major == null || major.getCourseUnit() == null) {
            return Optional.empty();
        }
        return courseRepository.findFirstByUnitNameAndCourseCodeOrderByIdAsc(major.getCourseUnit(), courseCode);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    // 남의 이수내역도 "없음"으로 응답해 존재 여부를 노출하지 않는다
    private CompletedCourse getOwnCourse(Long userId, Long courseId) {
        return completedCourseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPLETED_COURSE_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
