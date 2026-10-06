package com.ordo.catalog.controller;

import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.dto.CollegeResponse;
import com.ordo.catalog.dto.CoursePageResponse;
import com.ordo.catalog.dto.GeneralEducationResponse;
import com.ordo.catalog.dto.GraduationRequirementResponse;
import com.ordo.catalog.dto.MajorResponse;
import com.ordo.catalog.service.CatalogService;
import com.ordo.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회원가입 전에도 조회할 수 있는 학사 기준정보 API. */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@SecurityRequirements
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(summary = "단과대 목록")
    @GetMapping("/colleges")
    public ApiResponse<List<CollegeResponse>> getColleges() {
        return ApiResponse.ok(catalogService.getColleges());
    }

    @Operation(summary = "입학년도별 선택 가능한 전공 목록")
    @GetMapping("/majors")
    public ApiResponse<List<MajorResponse>> getMajors(
            @RequestParam @Positive int admissionYear,
            @RequestParam(required = false) @Positive Long collegeId) {
        return ApiResponse.ok(catalogService.getMajors(admissionYear, collegeId));
    }

    @Operation(summary = "입학년도·전공별 졸업요건 원본")
    @GetMapping("/requirements")
    public ApiResponse<GraduationRequirementResponse> getRequirements(
            @RequestParam @Positive Long majorId,
            @RequestParam @Positive int admissionYear) {
        return ApiResponse.ok(catalogService.getRequirements(majorId, admissionYear));
    }

    @Operation(summary = "전공 교과목 검색", description = "2026 교육과정 편성 자료이며 실제 학기별 개설 확정 정보가 아닙니다.")
    @GetMapping("/courses")
    public ApiResponse<CoursePageResponse> searchCourses(
            @RequestParam @Positive Long majorId,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Classification classification,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(catalogService.searchCourses(majorId, q, classification, page, size));
    }

    @Operation(summary = "교양 기본구조·필수과목", description = "해당 입학년도 자료가 없으면 2026 기준과 approximate=true를 반환합니다.")
    @GetMapping("/general-education")
    public ApiResponse<GeneralEducationResponse> getGeneralEducation(@RequestParam @Positive int admissionYear) {
        return ApiResponse.ok(catalogService.getGeneralEducation(admissionYear));
    }
}
