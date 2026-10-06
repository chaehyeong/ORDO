package com.ordo.timetable.controller;

import com.ordo.global.common.ApiResponse;
import com.ordo.global.common.Term;
import com.ordo.global.security.LoginUser;
import com.ordo.timetable.dto.TimetableEntryCreateRequest;
import com.ordo.timetable.dto.TimetableEntryResponse;
import com.ordo.timetable.dto.TimetableEntryUpdateRequest;
import com.ordo.timetable.dto.TimetableResponse;
import com.ordo.timetable.service.TimetableService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;

    @GetMapping
    public ApiResponse<TimetableResponse> getTimetable(@LoginUser Long userId,
                                                       @RequestParam(required = false) Integer year,
                                                       @RequestParam(required = false) Term term) {
        return ApiResponse.ok(timetableService.getTimetable(userId, year, term));
    }

    @PostMapping("/entries")
    public ResponseEntity<ApiResponse<TimetableEntryResponse>> create(
            @LoginUser Long userId, @Valid @RequestBody TimetableEntryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(timetableService.create(userId, request)));
    }

    @PatchMapping("/entries/{id}")
    public ApiResponse<TimetableEntryResponse> update(@LoginUser Long userId, @PathVariable Long id,
                                                      @Valid @RequestBody TimetableEntryUpdateRequest request) {
        return ApiResponse.ok(timetableService.update(userId, id, request));
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> delete(@LoginUser Long userId, @PathVariable Long id) {
        timetableService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
