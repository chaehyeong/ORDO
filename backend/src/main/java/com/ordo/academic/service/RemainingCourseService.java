package com.ordo.academic.service;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.dto.RemainingCoursesResponse;
import com.ordo.academic.dto.RemainingCoursesResponse.RemainingCourse;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.repository.CourseRepository;
import com.ordo.catalog.repository.GeneralEducationRequiredCourseRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 졸업 로드맵의 남은 수강 과목 (명세 4.6, T17). 필수교양은 이수현황의 체크리스트, 전공은 교과목 마스터가 원천이다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RemainingCourseService {

    private static final List<Classification> MAJOR_CLASSIFICATIONS =
            List.of(Classification.MAJOR_BASIC, Classification.MAJOR_REQUIRED, Classification.MAJOR_ELECTIVE);
    private static final List<Classification> CLASSIFICATION_ORDER = Stream
            .concat(Stream.of(Classification.GEN_REQUIRED), MAJOR_CLASSIFICATIONS.stream()).toList();
    private static final Comparator<RemainingCourse> DISPLAY_ORDER = Comparator
            .comparingInt((RemainingCourse c) -> CLASSIFICATION_ORDER.indexOf(c.classification()))
            .thenComparing(RemainingCourse::targetGrade, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(RemainingCourse::courseName);

    private final AcademicProgressService academicProgressService;
    private final UserRepository userRepository;
    private final CompletedCourseRepository completedCourseRepository;
    private final CourseRepository courseRepository;
    private final GeneralEducationRequiredCourseRepository generalEducationRequiredCourseRepository;

    /** 입학년도·전공이 없으면 PROFILE_INCOMPLETE */
    public RemainingCoursesResponse getRemainingCourses(Long userId) {
        ProgressSummary summary = academicProgressService.getSummary(userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String courseUnit = user.getMajor().getCourseUnit();
        List<RemainingCourse> courses = Stream.concat(generalCourses(summary), majorCourses(userId, courseUnit))
                .sorted(DISPLAY_ORDER)
                .toList();
        return new RemainingCoursesResponse(summary.total().remaining(), courseUnit != null, courses);
    }

    // 이수현황 체크리스트에서 아직 done 이 아닌 과목. 권장 학년은 그 체크리스트에 없어 같은 기준 연도의 행에서 읽는다
    private Stream<RemainingCourse> generalCourses(ProgressSummary summary) {
        Set<String> notDone = summary.requiredGeneralCourses().stream()
                .filter(course -> !course.done())
                .map(ProgressSummary.RequiredCourse::name)
                .collect(Collectors.toSet());
        int year = summary.approximate() ? AcademicProgressService.GENERAL_EDUCATION_FALLBACK_YEAR : summary.admissionYear();
        return generalEducationRequiredCourseRepository.findAllByAdmissionYearOrderByIdAsc(year).stream()
                .filter(course -> notDone.contains(course.getCourseName()))
                .map(course -> new RemainingCourse(course.getCourseName(), null, Classification.GEN_REQUIRED,
                        course.getCredits(), course.getRecommendedGrade(), null));
    }

    private Stream<RemainingCourse> majorCourses(Long userId, String courseUnit) {
        if (courseUnit == null) {
            return Stream.empty();
        }
        List<CompletedCourse> taken = completedCourseRepository.findByUserId(userId).stream()
                .filter(course -> course.getGrade() == null
                        || !AcademicProgressService.NOT_RECOGNIZED_GRADES.contains(course.getGrade()))
                .toList();
        Set<String> takenCodes = taken.stream().map(CompletedCourse::getCourseCode)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> namesWithoutCode = taken.stream().filter(course -> course.getCourseCode() == null)
                .map(course -> AcademicProgressService.withoutSpaces(course.getCourseName())).collect(Collectors.toSet());
        return courseRepository.findByUnitNameAndClassificationIn(courseUnit, MAJOR_CLASSIFICATIONS).stream()
                .filter(course -> !takenCodes.contains(course.getCourseCode())
                        && !namesWithoutCode.contains(AcademicProgressService.withoutSpaces(course.getName())))
                .map(course -> new RemainingCourse(course.getName(), course.getCourseCode(), course.getClassification(),
                        course.getCredits(), course.getTargetGrade(), course.getOpenSemester()));
    }
}
