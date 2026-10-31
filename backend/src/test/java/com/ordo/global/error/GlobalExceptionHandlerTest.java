package com.ordo.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MultipartException;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rowDeletedByConcurrentRequestIsNotFound() {
        var response = handler.handleAlreadyGone(new ObjectOptimisticLockingFailureException(Object.class, 1L));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().error().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void brokenMultipartIsBadRequest() {
        var response = handler.handleUnreadable(new MultipartException("Failed to parse multipart servlet request"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("INVALID_INPUT");
    }

    @Test
    void constraintViolationIsBadRequestAndKeepsInputValuesOutOfTheLog(CapturedOutput output) {
        var response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("Duplicate entry 'haeun@khu.ac.kr' for key 'users.uk_users_email'"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("INVALID_INPUT");
        assertThat(response.getBody().error().message()).doesNotContain("haeun@khu.ac.kr");
        assertThat(output).contains("DB 제약 위반").doesNotContain("haeun@khu.ac.kr");
    }
}
