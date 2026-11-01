package com.ordo.academic.service;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.DoubleMajorResponse;
import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.dto.ProgressSummary.Area;
import com.ordo.academic.dto.ProgressSummary.Check;
import com.ordo.academic.dto.ProgressSummary.Group;
import com.ordo.academic.dto.ProgressSummary.MajorInfo;
import com.ordo.academic.dto.ProgressSummary.RequiredCourse;
import com.ordo.academic.dto.ProgressSummary.Total;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.academic.repository.UserMinorRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import com.ordo.catalog.domain.GeneralEducationRequiredCourse;
import com.ordo.catalog.domain.GeneralEducationRequirement;
import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.repository.CourseRepository;
import com.ordo.catalog.repository.GeneralEducationRequiredCourseRepository;
import com.ordo.catalog.repository.GeneralEducationRequirementRepository;
import com.ordo.catalog.repository.GraduationRequirementRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이수현황 계산 (명세 5장). 홈·마이페이지는 findSummary 를 쓴다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicProgressService {

    static final int GENERAL_EDUCATION_FALLBACK_YEAR = 2026;  // 교양 기준은 2026만 있음 (명세 3.3)
    static final Set<String> NOT_RECOGNIZED_GRADES = Set.of("F", "NP");
    static final List<Classification> MAJOR_CLASSIFICATIONS =
            List.of(Classification.MAJOR_BASIC, Classification.MAJOR_REQUIRED, Classification.MAJOR_ELECTIVE);
    static final String EXPLORATION_SEMINAR = "전공탐색및기업가정신세미나";  // 자유이수교과지만 필수 (교육과정 p21·24, p45)
    // 세칙에 쓰인 다른 이름과 대체 과목 전공탐색2 (p22 제4조④)
    private static final Set<String> SEMINAR_NAMES = Set.of(EXPLORATION_SEMINAR, "전공탐색세미나", "전공탐색세미나및기업가정신세미나", "전공탐색2");
    // 이 공과대학 학과들은 창업세미나도 인정한다 (p132 등)
    private static final String STARTUP_SEMINAR = "창업세미나:스타트업에서빅테크까지";
    private static final Set<String> STARTUP_SEMINAR_UNITS = Set.of("기계공학부", "산업경영공학과", "원자력공학과", "화학공학과",
            "신소재공학과", "사회기반시스템공학과", "건축공학과", "건축학과");
    // 빅뱅에서문명까지를 전공기초로 대체하는 단위는 필수교양에서 뺀다 (p22 제4조①, p40 별표4). 반도체공학과는 표에 없다
    private static final String BIG_BANG = "빅뱅에서문명까지";
    private static final Set<String> BIG_BANG_EXEMPT_UNITS = Set.of("기계공학부", "산업경영공학과", "원자력공학과", "화학공학과",
            "신소재공학과", "사회기반시스템공학과", "건축공학과", "환경학전공", "환경공학전공", "건축학과",
            "응용수학과", "응용물리학과", "응용화학과", "우주과학과",
            "식물·환경신소재공학과", "식품생명공학과", "원예생명공학과", "유전생명학과", "한방생명공학과", "스마트팜과학과", "융합바이오·신소재공학과",
            "전자공학부 전자공학과", "생체의공학과",
            "컴퓨터공학부 컴퓨터공학과", "컴퓨터공학부 인공지능학과", "소프트웨어융합학과");
    private static final int MULTI_PROGRAM_REQUIRED_FROM = 2026;  // 2026학년도 입학자부터 다전공·부전공·MD 중 1개 이상 (교육과정 p115)
    private static final Set<Classification> OTHER = Set.of(
            Classification.GENERAL_ELECTIVE, Classification.TEACHING, Classification.TEACHING_MAJOR);

    private final UserRepository userRepository;
    private final CompletedCourseRepository completedCourseRepository;
    private final GraduationRequirementRepository graduationRequirementRepository;
    private final GeneralEducationRequirementRepository generalEducationRequirementRepository;
    private final GeneralEducationRequiredCourseRepository generalEducationRequiredCourseRepository;
    private final CourseRepository courseRepository;
    private final UserMinorRepository userMinorRepository;

    /** 입학년도·전공이 없으면 PROFILE_INCOMPLETE */
    public ProgressSummary getSummary(Long userId) {
        return findSummary(userId).orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_INCOMPLETE));
    }

    /** 홈·마이페이지용: 입학년도·전공이 없으면 empty */
    public Optional<ProgressSummary> findSummary(Long userId) {
        User user = getUser(userId);
        Major major = user.getMajor();
        Integer admissionYear = user.getAdmissionYear();
        if (major == null || admissionYear == null) {
            return Optional.empty();
        }
        GraduationRequirement requirement = graduationRequirementRepository
                .findByMajorIdAndAdmissionYear(major.getId(), admissionYear)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIREMENT_NOT_FOUND));
        Optional<GeneralEducationRequirement> exact = generalEducationRequirementRepository.findByAdmissionYear(admissionYear);
        GeneralEducationRequirement generalEducation = exact
                .or(() -> generalEducationRequirementRepository.findByAdmissionYear(GENERAL_EDUCATION_FALLBACK_YEAR))
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIREMENT_NOT_FOUND));
        List<GeneralEducationRequiredCourse> requiredCourses = generalEducationRequiredCourseRepository
                .findAllByAdmissionYearOrderByIdAsc(generalEducation.getAdmissionYear());
        return Optional.of(calculate(admissionYear, major, requirement, generalEducation, exact.isEmpty(),
                requiredCourses, completedCourseRepository.findByUserId(userId), doubleMajorOf(user), minorOnlyKeys(userId, major)));
    }

    /** 복수전공 진행 상황: 복수전공이 없으면 empty */
    public Optional<DoubleMajorResponse> findDoubleMajor(Long userId) {
        return Optional.ofNullable(doubleMajorOf(getUser(userId)))
                .map(doubleMajor -> doubleMajor.progress(recognize(completedCourseRepository.findByUserId(userId))));
    }

    // 요건 행은 등록·프로필 변경 때 확인하므로 항상 있다
    private DoubleMajorCalculation doubleMajorOf(User user) {
        Major second = user.getDoubleMajor();
        if (second == null) {
            return null;
        }
        GraduationRequirement secondRequirement = graduationRequirementRepository
                .findByMajorIdAndAdmissionYear(second.getId(), user.getAdmissionYear())
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIREMENT_NOT_FOUND));
        return new DoubleMajorCalculation(user.getAdmissionYear(), second, secondRequirement, majorCourses(user.getMajor()), majorCourses(second));
    }

    private Set<String> minorOnlyKeys(Long userId, Major major) {
        return userMinorRepository.findByUserId(userId)
                .map(minor -> minorOnlyKeys(majorCourses(major), majorCourses(minor.getMajor())))
                .orElse(Set.of());
    }

    /** 부전공에만 있는 과목의 키. 주전공 과목표가 없으면 겹치는지 알 수 없어 옮기지 않는다 */
    static Set<String> minorOnlyKeys(List<Course> firstCourses, List<Course> minorCourses) {
        if (firstCourses.isEmpty()) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>(DoubleMajorCalculation.index(minorCourses).keySet());
        keys.removeAll(DoubleMajorCalculation.index(firstCourses).keySet());
        return keys;
    }

    private List<Course> majorCourses(Major major) {
        return major.getCourseUnit() == null ? List.of()
                : courseRepository.findByUnitNameAndClassificationIn(major.getCourseUnit(), MAJOR_CLASSIFICATIONS);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /**
     * 순수 계산 (DB 조회 없음). 나중에 졸업 시뮬레이션은 courses 에 계획 과목을 더해 그대로 부르면 된다.
     * doubleMajor 가 null 이면 단일전공, 있으면 제1전공 요건도 다전공 기준으로 바뀐다 (교육과정 p116)
     * minorOnly 는 부전공에만 있는 과목의 키 (없으면 빈 집합). 부전공은 주전공 요건을 바꾸지 않는다 (p116·118)
     */
    static ProgressSummary calculate(int admissionYear, Major major, GraduationRequirement requirement,
                                     GeneralEducationRequirement generalEducation, boolean approximate,
                                     List<GeneralEducationRequiredCourse> requiredCourses,
                                     List<CompletedCourse> courses, DoubleMajorCalculation doubleMajor,
                                     Set<String> minorOnly) {
        Collection<CompletedCourse> recognized = recognize(courses);
        Map<Classification, Integer> earned = new EnumMap<>(Classification.class);
        recognized.forEach(c -> earned.merge(
                movesToOther(c, doubleMajor, minorOnly) ? Classification.GENERAL_ELECTIVE
                        : doubleMajor == null ? c.getClassification() : doubleMajor.firstMajorClassification(c),
                c.getCredits(), Integer::sum));
        int areaCount = (int) recognized.stream()
                .filter(c -> c.getClassification() == Classification.GEN_DISTRIBUTION && c.getDistributionArea() != null)
                .map(CompletedCourse::getDistributionArea).distinct().count();
        int otherEarned = OTHER.stream().mapToInt(c -> earned.getOrDefault(c, 0)).sum();

        int exemptCredits = requiredCourses.stream().filter(r -> bigBangExempt(major, r))
                .mapToInt(GeneralEducationRequiredCourse::getCredits).sum();

        boolean hasDoubleMajor = doubleMajor != null;
        Integer basicRequired = hasDoubleMajor ? requirement.getDoubleBasicCredits() : requirement.getBasicCredits();
        Integer requiredRequired = hasDoubleMajor ? requirement.getDoubleRequiredCredits() : requirement.getRequiredCredits();
        Integer electiveRequired = hasDoubleMajor ? requirement.getDoubleElectiveCredits() : requirement.getElectiveCredits();

        List<Area> areas = List.of(
                area("GEN_REQUIRED", "필수교양", earned, Classification.GEN_REQUIRED, generalEducation.getRequiredCredits() - exemptCredits),
                new Area("GEN_DISTRIBUTION", "배분이수", earned.getOrDefault(Classification.GEN_DISTRIBUTION, 0),
                        generalEducation.getDistributionCredits(), areaCount, generalEducation.getDistributionMinAreas()),
                area("GEN_FREE", "자유이수", earned, Classification.GEN_FREE, generalEducation.getFreeCredits()),
                area("MAJOR_BASIC", "전공기초", earned, Classification.MAJOR_BASIC, zeroIfNull(basicRequired)),
                area("MAJOR_REQUIRED", "전공필수", earned, Classification.MAJOR_REQUIRED, zeroIfNull(requiredRequired)),
                area("MAJOR_ELECTIVE", "전공선택", earned, Classification.MAJOR_ELECTIVE, zeroIfNull(electiveRequired)),
                new Area("OTHER", "기타", otherEarned, null, null, null));

        int totalRequired = zeroIfNull(requirement.getTotalCredits());
        int majorRequired = zeroIfNull(hasDoubleMajor ? requirement.getDoubleTotalCredits() : requirement.getMajorTotalCredits());
        int generalRequired = generalEducation.getTotalCredits() - exemptCredits;
        int majorEarned = sum(earned, Classification.MAJOR_BASIC, Classification.MAJOR_REQUIRED, Classification.MAJOR_ELECTIVE);
        int generalEarned = sum(earned, Classification.GEN_REQUIRED, Classification.GEN_DISTRIBUTION, Classification.GEN_FREE);
        List<Group> groups = List.of(
                new Group("MAJOR", "전공", majorEarned, majorRequired),
                new Group("GENERAL", "교양", generalEarned, generalRequired),
                new Group("OTHER", "기타", otherEarned, Math.max(0, totalRequired - majorRequired - generalRequired)));

        int totalEarned = recognized.stream().mapToInt(CompletedCourse::getCredits).sum();
        Total total = new Total(totalEarned, totalRequired, Math.max(0, totalRequired - totalEarned),
                percent(totalEarned, totalRequired));

        // 교양 필수과목 체크: 공백을 지운 과목명으로 비교 (명세 5장 9번)
        Set<String> takenRequired = recognized.stream()
                .filter(c -> c.getClassification() == Classification.GEN_REQUIRED)
                .map(c -> withoutSpaces(c.getCourseName())).collect(Collectors.toSet());
        List<RequiredCourse> requiredGeneralCourses = requiredCourses.stream()
                .filter(r -> !bigBangExempt(major, r))
                .map(r -> new RequiredCourse(r.getCourseName(), r.getCredits(),
                        EXPLORATION_SEMINAR.equals(r.getCourseName())
                                ? seminarDone(recognized, major)
                                : takenRequired.contains(withoutSpaces(r.getCourseName()))))
                .toList();

        boolean graduatable = totalEarned >= totalRequired
                && areas.stream().allMatch(a -> a.required() == null || a.earned() >= a.required())
                && areaCount >= generalEducation.getDistributionMinAreas()
                && requiredGeneralCourses.stream().allMatch(RequiredCourse::done)
                && (!hasDoubleMajor || doubleMajor.met(recognized));

        return new ProgressSummary(admissionYear, new MajorInfo(major.getId(), major.getDisplayName()), total, areas,
                groups, requiredGeneralCourses, checks(admissionYear, requirement), graduatable, approximate);
    }

    // ponytail: 대체 과목이 전공기초라 이수 여부를 보지 않는다. 생체의공·응용수학 다전공은 그 과목이 다전공 전공기초에서 빠져도 뺀다. 명세 5장 9번
    private static boolean bigBangExempt(Major major, GeneralEducationRequiredCourse course) {
        return BIG_BANG.equals(course.getCourseName()) && major.getCourseUnit() != null && BIG_BANG_EXEMPT_UNITS.contains(major.getCourseUnit());
    }

    // 자유이수로 기록되고 전공탐색2 는 전공선택이라 이수구분으로 거르지 않는다 (명세 5장 9번)
    // ponytail: 외국인·편입생 면제와 학과 지정 분반은 모른다. 면제 대상도 졸업 불가로 나온다. 명세 9장 29
    private static boolean seminarDone(Collection<CompletedCourse> recognized, Major major) {
        return recognized.stream().map(c -> withoutSpaces(c.getCourseName()))
                .anyMatch(name -> SEMINAR_NAMES.contains(name)
                        || name.equals(STARTUP_SEMINAR) && major.getCourseUnit() != null && STARTUP_SEMINAR_UNITS.contains(major.getCourseUnit()));
    }

    // ponytail: 주전공이 인정하는 타전공 과목이 부전공 목록에도 있으면 기타로 빠져 전공 학점이 적게 나온다. 명세 5장 13
    private static boolean movesToOther(CompletedCourse course, DoubleMajorCalculation doubleMajor, Set<String> minorOnly) {
        return doubleMajor != null && doubleMajor.onlyInSecondMajor(course)
                || MAJOR_CLASSIFICATIONS.contains(course.getClassification()) && minorOnly.contains(course.retakeKey());
    }

    // 명세 5장 4번: F·NP 제외 → 같은 학수번호(없으면 과목명)는 가장 최근 1건만 인정
    // ponytail: 문서 순서 그대로 F 를 먼저 빼서, 예전에 통과하고 재수강에서 F 면 예전 학점이 남는다. 학과 시행세칙 확인 후 순서 조정
    static Collection<CompletedCourse> recognize(List<CompletedCourse> courses) {
        return CompletedCourse.latestOfEachCourse(courses.stream()
                .filter(c -> c.getGrade() == null || !NOT_RECOGNIZED_GRADES.contains(c.getGrade()))
                .sorted(CompletedCourse.CHRONOLOGICAL)
                .toList());
    }

    private static List<Check> checks(int admissionYear, GraduationRequirement requirement) {
        List<Check> checks = new ArrayList<>();
        addCheck(checks, "SW기초교육", requirement.getSwRequirement());
        addCheck(checks, "영어강의", requirement.getEnglishLectureRequirement());
        addCheck(checks, "졸업논문", requirement.getThesisRequirement());
        addCheck(checks, "TOPIK(외국인)", requirement.getTopikRequirement());
        addCheck(checks, "졸업능력인증", requirement.getCompetencyCertification());
        if (admissionYear >= MULTI_PROGRAM_REQUIRED_FROM) {
            addCheck(checks, "다전공·부전공·마이크로디그리", "1개 이상 이수");
        }
        return checks;
    }

    private static void addCheck(List<Check> checks, String name, String requirement) {
        if (requirement != null && !requirement.isBlank()) {
            checks.add(new Check(name, requirement, "MANUAL"));
        }
    }

    private static Area area(String key, String label, Map<Classification, Integer> earned,
                             Classification classification, int required) {
        return new Area(key, label, earned.getOrDefault(classification, 0), required, null, null);
    }

    private static int sum(Map<Classification, Integer> earned, Classification... classifications) {
        int total = 0;
        for (Classification c : classifications) {
            total += earned.getOrDefault(c, 0);
        }
        return total;
    }

    /** 소수 첫째 자리 버림. 요구 학점이 0 이면 0.0 */
    static double percent(int earned, int required) {
        return required <= 0 ? 0.0 : (earned * 1000L / required) / 10.0;
    }

    static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    static String withoutSpaces(String value) {
        return value.replaceAll("\\s+", "");
    }
}
