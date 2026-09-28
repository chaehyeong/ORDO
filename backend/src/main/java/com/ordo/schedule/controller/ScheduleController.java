package com.ordo.schedule.controller;

import com.ordo.global.common.ApiResponse;
import com.ordo.global.security.LoginUser;
import com.ordo.schedule.domain.ScheduleCategory;
import com.ordo.schedule.dto.ScheduleCreateRequest;
import com.ordo.schedule.dto.ScheduleDoneRequest;
import com.ordo.schedule.dto.ScheduleResponse;
import com.ordo.schedule.dto.ScheduleUpdateRequest;
import com.ordo.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    public ApiResponse<List<ScheduleResponse>> getSchedules(
            @LoginUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ScheduleCategory category) {
        return ApiResponse.ok(scheduleService.getSchedules(userId, from, to, category));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(@LoginUser Long userId,
                                                                @Valid @RequestBody ScheduleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(scheduleService.create(userId, request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScheduleResponse> getSchedule(@LoginUser Long userId, @PathVariable Long id) {
        return ApiResponse.ok(scheduleService.getSchedule(userId, id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ScheduleResponse> update(@LoginUser Long userId, @PathVariable Long id,
                                                @Valid @RequestBody ScheduleUpdateRequest request) {
        return ApiResponse.ok(scheduleService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@LoginUser Long userId, @PathVariable Long id) {
        scheduleService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/done")
    public ApiResponse<ScheduleResponse> changeDone(@LoginUser Long userId, @PathVariable Long id,
                                                    @Valid @RequestBody ScheduleDoneRequest request) {
        return ApiResponse.ok(scheduleService.changeDone(userId, id, request.done()));
    }
}
