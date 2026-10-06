package com.ordo.ecampus.controller;

import com.ordo.ecampus.dto.EcampusEventsResponse;
import com.ordo.ecampus.dto.EcampusFeedRequest;
import com.ordo.ecampus.service.EcampusService;
import com.ordo.global.common.ApiResponse;
import com.ordo.global.security.LoginUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ecampus")
@RequiredArgsConstructor
public class EcampusController {

    private final EcampusService ecampusService;

    @PutMapping("/feed")
    public ResponseEntity<Void> connect(@LoginUser Long userId, @Valid @RequestBody EcampusFeedRequest request) {
        ecampusService.connect(userId, request.feedToken());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/feed")
    public ResponseEntity<Void> disconnect(@LoginUser Long userId) {
        ecampusService.disconnect(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events")
    public ApiResponse<EcampusEventsResponse> getEvents(
            @LoginUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(ecampusService.getEvents(userId, from, to));
    }
}
