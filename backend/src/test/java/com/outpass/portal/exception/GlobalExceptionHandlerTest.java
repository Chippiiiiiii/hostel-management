package com.outpass.portal.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpInputMessage;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
    void handleTypeMismatch_hidesInternalJavaTypeName() throws NoSuchMethodException {
        // Reproduces GET /student/outpass/abc -- {id} fails to bind to Long. The default
        // Spring message ("Failed to convert value of type 'java.lang.String' to required
        // type 'java.lang.Long'; ...") leaks internal class names and must not reach the client.
        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyLongParam", Long.class), 0);
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", param,
                        new NumberFormatException("For input string: \"abc\""));

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid value provided in request");
        assertThat(response.getBody().getMessage()).doesNotContain("java.lang");
    }

    private void dummyLongParam(Long id) {
    }

    @Test
    void handleMessageNotReadable_hidesParserInternals() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character ('n' (code 110))",
                (HttpInputMessage) null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMessageNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed request body");
    }

    @Test
    void handleRuntimeException_stillReturnsBusinessMessage() {
        RuntimeException ex = new RuntimeException("Only pending outpasses can be approved");

        ResponseEntity<ApiResponse<Void>> response = handler.handleRuntimeException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Only pending outpasses can be approved");
    }

    // 400-vs-403 semantics fix: a genuine ownership/authorization denial (e.g. "You can only
    // manage your own hostel") must be 403, not fall through to handleRuntimeException's 400
    // -- functionally still blocked either way, but 400 implies malformed client input, which
    // this isn't.
    @Test
    void handleForbiddenOperation_returns403NotBadRequest() {
        ForbiddenOperationException ex = new ForbiddenOperationException("You can only manage your own hostel");

        ResponseEntity<ApiResponse<Void>> response = handler.handleForbiddenOperation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("You can only manage your own hostel");
    }
}
