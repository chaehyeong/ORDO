package com.ordo.academic.controller;

import com.ordo.academic.dto.CompletedCourseBulkRequest;
import com.ordo.academic.dto.CompletedCourseRequest;
import com.ordo.academic.dto.CompletedCourseResponse;
import com.ordo.academic.dto.CompletedCourseUpdateRequest;
import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.service.AcademicProgressService;
import com.ordo.academic.service.CompletedCourseService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academic")
@RequiredArgsConstructor
public class AcademicController {

    private final CompletedCourseService completedCourseService;
    private final AcademicProgressService academicProgressService;

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
}
