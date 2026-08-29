package com.outpass.portal.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.outpass.portal.dto.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUsernameNotFound(UsernameNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("This account has been disabled. Contact an administrator."));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(RateLimitExceededException ex) {
        return ResponseEntity.status(429) // Too Many Requests
                .body(ApiResponse.error(ex.getMessage()));
    }

    // IllegalArgumentException is what Enum.valueOf() throws for an invalid client-supplied
    // string (e.g. an unknown complaint category/status or attendance method); its default
    // message embeds the enum's fully-qualified internal class name (e.g. "No enum constant
    // com.outpass.portal.model.enums.ComplaintCategory.FOO"). It is itself a RuntimeException,
    // so without this dedicated handler it would fall through to handleRuntimeException and
    // leak that internal detail to the client.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid value provided in request"));
    }

    // DataAccessException (constraint violations, query/connection failures, etc.) is itself
    // a RuntimeException, so without this dedicated handler it would be caught by
    // handleRuntimeException below and its raw message (SQL/constraint/table names) would be
    // echoed straight back to the client — exactly the internal-detail leak the generic
    // handleGlobalException handler further down exists to prevent.
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(DataAccessException ex) {
        log.error("Data access exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex) {
        // Unlike RuntimeException above (used throughout the codebase as an intentional,
        // user-facing validation/business-rule message), anything reaching this generic
        // handler is unexpected — a DB, framework, or driver exception whose message can
        // contain internal details (query fragments, column/table names, stack info) that
        // shouldn't be echoed back to the client. Log the real cause server-side only.
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}

