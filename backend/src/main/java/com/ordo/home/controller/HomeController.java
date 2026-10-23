package com.ordo.home.controller;

import com.ordo.global.common.ApiResponse;
import com.ordo.global.security.LoginUser;
import com.ordo.home.dto.HomeResponse;
import com.ordo.home.service.HomeService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /** date 생략 시 오늘(Asia/Seoul) */
    @GetMapping
    public ApiResponse<HomeResponse> getHome(@LoginUser Long userId,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate baseDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul"));
        return ApiResponse.ok(homeService.getHome(userId, baseDate));
    }
}
