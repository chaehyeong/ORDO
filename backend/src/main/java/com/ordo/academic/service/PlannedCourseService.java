package com.ordo.academic.service;

import com.ordo.academic.domain.PlannedCourse;
import com.ordo.academic.dto.PlannedCoursesRequest;
import com.ordo.academic.dto.PlannedCoursesResponse;
import com.ordo.academic.repository.PlannedCourseRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 졸업 로드맵의 학기별 배치 (명세 4.6, T18). 끌어 놓은 결과를 통째로 저장한다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlannedCourseService {

    private final PlannedCourseRepository plannedCourseRepository;
    private final UserRepository userRepository;

    public PlannedCoursesResponse getPlans(Long userId) {
        return PlannedCoursesResponse.from(plannedCourseRepository.findByUserIdOrderByIdAsc(userId));
    }

    /** 내 배치를 이 목록으로 바꾼다. 같은 학기에 같은 과목이 두 번 있으면 아무것도 바꾸지 않고 INVALID_INPUT */
    @Transactional
    public PlannedCoursesResponse replaceAll(Long userId, PlannedCoursesRequest request) {
        userRepository.findByIdForUpdate(userId);  // 끌어 놓기가 빠르게 이어져도 같은 사용자의 교체를 차례로 처리한다
        User user = userRepository.getReferenceById(userId);
        List<PlannedCourse> courses = request.courses().stream()
                .map(course -> PlannedCourse.builder().user(user).academicYear(course.year()).term(course.term())
                        .courseName(course.courseName().strip())
                        .courseCode(course.courseCode() == null || course.courseCode().isBlank() ? null : course.courseCode().strip())
                        .classification(course.classification()).credits(course.credits()).build())
                .toList();
        if (courses.stream().map(PlannedCourseService::sameCourseKey).distinct().count() != courses.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        plannedCourseRepository.deleteAll(plannedCourseRepository.findByUserIdOrderByIdAsc(userId));
        plannedCourseRepository.saveAll(courses);
        return PlannedCoursesResponse.from(courses);
    }

    // 같은 학기 + 같은 학수번호(없으면 공백 뺀 과목명)면 같은 과목. 다른 학기에는 다시 넣을 수 있다(재수강 계획)
    private static String sameCourseKey(PlannedCourse course) {
        String courseKey = course.getCourseCode() != null
                ? "code:" + course.getCourseCode()
                : "name:" + AcademicProgressService.withoutSpaces(course.getCourseName());
        return course.getAcademicYear() + "-" + course.getTerm() + "-" + courseKey;
    }
}
