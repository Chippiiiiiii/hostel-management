package com.outpass.portal.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.outpass.portal.dto.response.ApiResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDataAccessException_hidesInternalDetails() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement; constraint [uk_record_student_date]; table [attendance_records]");

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataAccessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage())
                .isEqualTo("An unexpected error occurred. Please try again later.");
        assertThat(response.getBody().getMessage()).doesNotContain("uk_record_student_date", "attendance_records");
    }

    @Test
    void handleIllegalArgumentException_hidesInternalEnumClassName() {
        IllegalArgumentException ex = new IllegalArgumentException(
                "No enum constant com.outpass.portal.model.enums.ComplaintCategory.FOO");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).doesNotContain("com.outpass.portal");
    }

    @Test
    void handleRuntimeException_stillReturnsBusinessMessage() {
        RuntimeException ex = new RuntimeException("Only pending outpasses can be approved");

        ResponseEntity<ApiResponse<Void>> response = handler.handleRuntimeException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Only pending outpasses can be approved");
    }
}
