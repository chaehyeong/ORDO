package com.ordo.global.error;

import com.ordo.global.common.ApiResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return toResponse(e.getErrorCode(), e.getMessage());
    }

    // @Valid 검증 실패 → message 에 필드별 사유
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return toResponse(ErrorCode.INVALID_INPUT, message.isEmpty() ? ErrorCode.INVALID_INPUT.getMessage() : message);
    }

    // JSON 형식 오류, 파라미터 타입 불일치, 깨진 multipart (크기 초과는 아래에서 따로 처리)
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(Exception e) {
        return toResponse(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
    }

    // 업로드 5MB 초과 (T12 파일 가져오기)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException e) {
        return toResponse(ErrorCode.IMPORT_TOO_LARGE, ErrorCode.IMPORT_TOO_LARGE.getMessage());
    }

    // 두 번 누름처럼 동시 요청이 대상 행을 먼저 지웠다. @Version 을 쓰지 않아 이 예외는 행이 사라진 경우뿐이다
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyGone(OptimisticLockingFailureException e) {
        return toResponse(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    // 동시 요청이 유일 제약에 걸렸거나 DB 가 값을 받지 못했다. 메시지에 입력값(이메일, 푸시 주소)이 있어 내용은 로그에 남기지 않는다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DB 제약 위반 ({})", e.getMostSpecificCause().getClass().getSimpleName());
        return toResponse(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        // 스프링 표준 4xx 예외(없는 경로, 지원하지 않는 메서드, 필수 파라미터 누락 등)
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            ErrorCode errorCode = errorResponse.getStatusCode().value() == 404 ? ErrorCode.NOT_FOUND : ErrorCode.INVALID_INPUT;
            return toResponse(errorCode, errorCode.getMessage());
        }
        log.error("처리되지 않은 예외", e);
        return toResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode, message));
    }
}
