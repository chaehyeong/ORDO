package com.ordo.academic.controller;

import com.ordo.academic.dto.CompletedCourseBulkRequest;
import com.ordo.academic.dto.CompletedCourseRequest;
import com.ordo.academic.dto.CompletedCourseResponse;
import com.ordo.academic.dto.CompletedCourseUpdateRequest;
import com.ordo.academic.dto.DoubleMajorRequest;
import com.ordo.academic.dto.DoubleMajorResponse;
import com.ordo.academic.dto.GpaSummary;
import com.ordo.academic.dto.MicroDegreeAddRequest;
import com.ordo.academic.dto.MicroDegreeProgressResponse;
import com.ordo.academic.dto.PlannedCoursesRequest;
import com.ordo.academic.dto.PlannedCoursesResponse;
import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.dto.RemainingCoursesResponse;
import com.ordo.academic.service.AcademicGpaService;
import com.ordo.academic.service.AcademicProgressService;
import com.ordo.academic.service.CompletedCourseService;
import com.ordo.academic.service.DoubleMajorService;
import com.ordo.academic.service.MicroDegreeService;
import com.ordo.academic.service.PlannedCourseService;
import com.ordo.academic.service.RemainingCourseService;
import com.ordo.global.common.ApiResponse;
import com.ordo.global.security.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academic")
@RequiredArgsConstructor
public class AcademicController {

    private final CompletedCourseService completedCourseService;
    private final AcademicProgressService academicProgressService;
    private final AcademicGpaService academicGpaService;
    private final RemainingCourseService remainingCourseService;
    private final PlannedCourseService plannedCourseService;
    private final DoubleMajorService doubleMajorService;
    private final MicroDegreeService microDegreeService;

    @GetMapping("/completed-courses")
    public ApiResponse<List<CompletedCourseResponse>> getCourses(@LoginUser Long userId) {
        return ApiResponse.ok(completedCourseService.getCourses(userId));
    }

    @PostMapping("/completed-courses")
    public ResponseEntity<ApiResponse<CompletedCourseResponse>> create(
            @LoginUser Long userId, @Valid @RequestBody CompletedCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(completedCourseService.create(userId, request)));
    }

    @PostMapping("/completed-courses/bulk")
    public ResponseEntity<ApiResponse<List<CompletedCourseResponse>>> createAll(
            @LoginUser Long userId, @Valid @RequestBody CompletedCourseBulkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(completedCourseService.createAll(userId, request)));
    }

    @PatchMapping("/completed-courses/{id}")
    public ApiResponse<CompletedCourseResponse> update(@LoginUser Long userId, @PathVariable Long id,
                                                       @Valid @RequestBody CompletedCourseUpdateRequest request) {
        return ApiResponse.ok(completedCourseService.update(userId, id, request));
    }

    @DeleteMapping("/completed-courses/{id}")
    public ResponseEntity<Void> delete(@LoginUser Long userId, @PathVariable Long id) {
        completedCourseService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/progress")
    public ApiResponse<ProgressSummary> getProgress(@LoginUser Long userId) {
        return ApiResponse.ok(academicProgressService.getSummary(userId));
    }

    @GetMapping("/gpa")
    public ApiResponse<GpaSummary> getGpa(@LoginUser Long userId) {
        return ApiResponse.ok(academicGpaService.getGpa(userId));
    }

    @GetMapping("/remaining-courses")
    public ApiResponse<RemainingCoursesResponse> getRemainingCourses(@LoginUser Long userId) {
        return ApiResponse.ok(remainingCourseService.getRemainingCourses(userId));
    }

    @GetMapping("/plans")
    public ApiResponse<PlannedCoursesResponse> getPlans(@LoginUser Long userId) {
        return ApiResponse.ok(plannedCourseService.getPlans(userId));
    }

    @PutMapping("/plans")
    public ApiResponse<PlannedCoursesResponse> replacePlans(@LoginUser Long userId,
                                                            @Valid @RequestBody PlannedCoursesRequest request) {
        return ApiResponse.ok(plannedCourseService.replaceAll(userId, request));
    }

    @GetMapping("/double-major")
    public ApiResponse<DoubleMajorResponse> getDoubleMajor(@LoginUser Long userId) {
        return ApiResponse.ok(doubleMajorService.get(userId));
    }

    @PutMapping("/double-major")
    public ApiResponse<DoubleMajorResponse> registerDoubleMajor(@LoginUser Long userId,
                                                                @Valid @RequestBody DoubleMajorRequest request) {
        return ApiResponse.ok(doubleMajorService.register(userId, request));
    }

    @DeleteMapping("/double-major")
    public ResponseEntity<Void> removeDoubleMajor(@LoginUser Long userId) {
        doubleMajorService.remove(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/micro-degrees")
    public ApiResponse<List<MicroDegreeProgressResponse>> getMicroDegrees(@LoginUser Long userId) {
        return ApiResponse.ok(microDegreeService.getMine(userId));
    }

    @PostMapping("/micro-degrees")
    public ResponseEntity<ApiResponse<List<MicroDegreeProgressResponse>>> addMicroDegree(
            @LoginUser Long userId, @Valid @RequestBody MicroDegreeAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(microDegreeService.add(userId, request)));
    }

    @DeleteMapping("/micro-degrees/{microDegreeId}")
    public ResponseEntity<Void> removeMicroDegree(@LoginUser Long userId, @PathVariable Long microDegreeId) {
        microDegreeService.remove(userId, microDegreeId);
        return ResponseEntity.noContent().build();
    }
}
