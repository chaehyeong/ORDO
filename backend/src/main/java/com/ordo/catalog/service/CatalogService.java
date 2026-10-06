package com.ordo.catalog.service;

import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.GeneralEducationRequirement;
import com.ordo.catalog.domain.Major;
import com.ordo.catalog.dto.CollegeResponse;
import com.ordo.catalog.dto.CoursePageResponse;
import com.ordo.catalog.dto.GeneralEducationResponse;
import com.ordo.catalog.dto.GraduationRequirementResponse;
import com.ordo.catalog.dto.MajorResponse;
import com.ordo.catalog.repository.CollegeRepository;
import com.ordo.catalog.repository.CourseRepository;
import com.ordo.catalog.repository.GeneralEducationRequirementRepository;
import com.ordo.catalog.repository.GeneralEducationRequiredCourseRepository;
import com.ordo.catalog.repository.GraduationRequirementRepository;
import com.ordo.catalog.repository.MajorRepository;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private static final int GENERAL_EDUCATION_FALLBACK_YEAR = 2026;

    private final CollegeRepository collegeRepository;
    private final MajorRepository majorRepository;
    private final CourseRepository courseRepository;
    private final GraduationRequirementRepository graduationRequirementRepository;
    private final GeneralEducationRequirementRepository generalEducationRequirementRepository;
    private final GeneralEducationRequiredCourseRepository generalEducationRequiredCourseRepository;

    public List<CollegeResponse> getColleges() {
        return collegeRepository.findAllByOrderByNameAscIdAsc().stream().map(CollegeResponse::from).toList();
    }

    public List<MajorResponse> getMajors(int admissionYear, Long collegeId) {
        return majorRepository.findAllSelectable(admissionYear, collegeId).stream().map(MajorResponse::from).toList();
    }

    public GraduationRequirementResponse getRequirements(Long majorId, int admissionYear) {
        getMajor(majorId);
        return graduationRequirementRepository.findByMajorIdAndAdmissionYear(majorId, admissionYear)
                .map(GraduationRequirementResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIREMENT_NOT_FOUND));
    }

    public CoursePageResponse searchCourses(Long majorId, String query, Classification classification,
                                            int page, int size) {
        Major major = getMajor(majorId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name", "id"));
        // 대응하는 교육과정이 없는 전공은 다른 학과의 과목을 대신 반환하지 않는다.
        if (major.getCourseUnit() == null) {
            return CoursePageResponse.from(Page.empty(pageable));
        }
        return CoursePageResponse.from(courseRepository.search(
                major.getCourseUnit(), searchPattern(query), classification, pageable));
    }

    public GeneralEducationResponse getGeneralEducation(int admissionYear) {
        GeneralEducationRequirement requirement = generalEducationRequirementRepository.findByAdmissionYear(admissionYear)
                .or(() -> admissionYear == GENERAL_EDUCATION_FALLBACK_YEAR
                        ? java.util.Optional.empty()
                        : generalEducationRequirementRepository.findByAdmissionYear(GENERAL_EDUCATION_FALLBACK_YEAR))
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIREMENT_NOT_FOUND));
        return GeneralEducationResponse.from(admissionYear, requirement,
                generalEducationRequiredCourseRepository.findAllByAdmissionYearOrderByIdAsc(requirement.getAdmissionYear()));
    }

    private Major getMajor(Long majorId) {
        return majorRepository.findById(majorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MAJOR_NOT_FOUND));
    }

    private String searchPattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        // %, _ 를 검색어 그대로 취급한다. JPQL의 ESCAPE 문자(!)도 먼저 이스케이프한다.
        String escaped = query.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
