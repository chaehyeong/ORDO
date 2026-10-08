package com.ordo.importer.controller;

import com.ordo.global.common.ApiResponse;
import com.ordo.global.security.LoginUser;
import com.ordo.importer.dto.ImportPreviewResponse;
import com.ordo.importer.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /** 수강신청확인서·전체 성적 보기 파일 → 미리보기 초안 (저장 안 함) */
    @PostMapping(value = "/api/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportPreviewResponse> preview(@LoginUser Long userId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(importService.preview(userId, file));
    }
}
