package com.ordo.academic.service;

import com.ordo.academic.domain.CompletedCourse;
import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.dto.ProgressSummary.Area;
import com.ordo.academic.dto.ProgressSummary.Check;
import com.ordo.academic.dto.ProgressSummary.Group;
import com.ordo.academic.dto.ProgressSummary.MajorInfo;
import com.ordo.academic.dto.ProgressSummary.RequiredCourse;
import com.ordo.academic.dto.ProgressSummary.Total;
import com.ordo.academic.repository.CompletedCourseRepository;
import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.GeneralEducationRequiredCourse;
import com.ordo.catalog.domain.GeneralEducationRequirement;
import com.ordo.catalog.domain.GraduationRequirement;
import com.ordo.catalog.domain.Major;
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
import java.util.LinkedHashMap;
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
    private static final Set<String> NOT_RECOGNIZED_GRADES = Set.of("F", "NP");
    private static final Set<Classification> OTHER = Set.of(
            Classification.GENERAL_ELECTIVE, Classification.TEACHING, Classification.TEACHING_MAJOR);

    private final UserRepository userRepository;
    private final CompletedCourseRepository completedCourseRepository;
    private final GraduationRequirementRepository graduationRequirementRepository;
    private final GeneralEducationRequirementRepository generalEducationRequirementRepository;
    private final GeneralEducationRequiredCourseRepository generalEducationRequiredCourseRepository;

    /** 입학년도·전공이 없으면 PROFILE_INCOMPLETE */
    public ProgressSummary getSummary(Long userId) {
        return findSummary(userId).orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_INCOMPLETE));
    }

    /** 홈·마이페이지용: 입학년도·전공이 없으면 empty */
    public Optional<ProgressSummary> findSummary(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
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
                requiredCourses, completedCourseRepository.findByUserId(userId)));
    }

    /**
     * 순수 계산 (DB 조회 없음). 나중에 졸업 시뮬레이션은 courses 에 계획 과목을 더해 그대로 부르면 된다.
     */
    static ProgressSummary calculate(int admissionYear, Major major, GraduationRequirement requirement,
                                     GeneralEducationRequirement generalEducation, boolean approximate,
                                     List<GeneralEducationRequiredCourse> requiredCourses,
                                     List<CompletedCourse> courses) {
        Collection<CompletedCourse> recognized = recognize(courses);
        Map<Classification, Integer> earned = new EnumMap<>(Classification.class);
        recognized.forEach(c -> earned.merge(c.getClassification(), c.getCredits(), Integer::sum));
        int areaCount = (int) recognized.stream()
                .filter(c -> c.getClassification() == Classification.GEN_DISTRIBUTION && c.getDistributionArea() != null)
                .map(CompletedCourse::getDistributionArea).distinct().count();
        int otherEarned = OTHER.stream().mapToInt(c -> earned.getOrDefault(c, 0)).sum();

        List<Area> areas = List.of(
                area("GEN_REQUIRED", "필수교양", earned, Classification.GEN_REQUIRED, generalEducation.getRequiredCredits()),
                new Area("GEN_DISTRIBUTION", "배분이수", earned.getOrDefault(Classification.GEN_DISTRIBUTION, 0),
                        generalEducation.getDistributionCredits(), areaCount, generalEducation.getDistributionMinAreas()),
                area("GEN_FREE", "자유이수", earned, Classification.GEN_FREE, generalEducation.getFreeCredits()),
                area("MAJOR_BASIC", "전공기초", earned, Classification.MAJOR_BASIC, zeroIfNull(requirement.getBasicCredits())),
                area("MAJOR_REQUIRED", "전공필수", earned, Classification.MAJOR_REQUIRED, zeroIfNull(requirement.getRequiredCredits())),
                area("MAJOR_ELECTIVE", "전공선택", earned, Classification.MAJOR_ELECTIVE, zeroIfNull(requirement.getElectiveCredits())),
                new Area("OTHER", "기타", otherEarned, null, null, null));

        int totalRequired = zeroIfNull(requirement.getTotalCredits());
        int majorRequired = zeroIfNull(requirement.getMajorTotalCredits());
        int generalRequired = generalEducation.getTotalCredits();
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
                .map(r -> new RequiredCourse(r.getCourseName(), r.getCredits(),
                        takenRequired.contains(withoutSpaces(r.getCourseName()))))
                .toList();

        boolean graduatable = totalEarned >= totalRequired
                && areas.stream().allMatch(a -> a.required() == null || a.earned() >= a.required())
                && areaCount >= generalEducation.getDistributionMinAreas()
                && requiredGeneralCourses.stream().allMatch(RequiredCourse::done);

        return new ProgressSummary(admissionYear, new MajorInfo(major.getId(), major.getDisplayName()), total, areas,
                groups, requiredGeneralCourses, checks(requirement), graduatable, approximate);
    }

    // 명세 5장 4번: F·NP 제외 → 같은 학수번호(없으면 과목명)는 가장 최근 1건만 인정
    // ponytail: 문서 순서 그대로 F 를 먼저 빼서, 예전에 통과하고 재수강에서 F 면 예전 학점이 남는다. 학과 시행세칙 확인 후 순서 조정
    private static Collection<CompletedCourse> recognize(List<CompletedCourse> courses) {
        Map<String, CompletedCourse> latest = new LinkedHashMap<>();
        courses.stream()
                .filter(c -> c.getGrade() == null || !NOT_RECOGNIZED_GRADES.contains(c.getGrade()))
                .sorted(CompletedCourse.CHRONOLOGICAL)
                .forEach(c -> latest.put(c.getCourseCode() != null ? "code:" + c.getCourseCode()
                        : "name:" + withoutSpaces(c.getCourseName()), c));
        return latest.values();
    }

    private static List<Check> checks(GraduationRequirement requirement) {
        List<Check> checks = new ArrayList<>();
        addCheck(checks, "SW기초교육", requirement.getSwRequirement());
        addCheck(checks, "영어강의", requirement.getEnglishLectureRequirement());
        addCheck(checks, "졸업논문", requirement.getThesisRequirement());
        addCheck(checks, "TOPIK(외국인)", requirement.getTopikRequirement());
        addCheck(checks, "졸업능력인증", requirement.getCompetencyCertification());
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

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private static String withoutSpaces(String value) {
        return value.replaceAll("\\s+", "");
    }
}
