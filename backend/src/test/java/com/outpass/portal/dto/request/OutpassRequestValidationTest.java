package com.outpass.portal.dto.request;

import com.outpass.portal.model.entity.Outpass;
import jakarta.persistence.Column;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the reason-field validation added to close the gap where every other OutpassRequest
 * field had @NotBlank/@Pattern but reason had none -- a request with a null, empty, or
 * arbitrarily long reason was previously accepted all the way to persistence.
 */
class OutpassRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private OutpassRequest baseRequest() {
        OutpassRequest request = new OutpassRequest();
        request.setPlaceOfVisit("Home");
        request.setDate(LocalDateTime.now().plusHours(1));
        request.setReturnDate(LocalDateTime.now().plusDays(1));
        request.setNoOfDays(1);
        request.setContactNumber("9000000000");
        request.setParentNumber("9000000001");
        return request;
    }

    private boolean hasViolationOn(OutpassRequest request, String property) {
        Set<ConstraintViolation<OutpassRequest>> violations = validator.validate(request);
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    void nullReasonIsRejected() {
        OutpassRequest request = baseRequest();
        request.setReason(null);

        assertThat(hasViolationOn(request, "reason")).isTrue();
    }

    @Test
    void emptyReasonIsRejected() {
        OutpassRequest request = baseRequest();
        request.setReason("");

        assertThat(hasViolationOn(request, "reason")).isTrue();
    }

    @Test
    void whitespaceOnlyReasonIsRejected() {
        OutpassRequest request = baseRequest();
        request.setReason("   ");

        assertThat(hasViolationOn(request, "reason")).isTrue();
    }

    @Test
    void normalReasonIsAccepted() {
        OutpassRequest request = baseRequest();
        request.setReason("Visiting family for a function");

        assertThat(hasViolationOn(request, "reason")).isFalse();
    }

    @Test
    void reasonAtMaxAllowedLengthIsAccepted() {
        OutpassRequest request = baseRequest();
        request.setReason("a".repeat(500));

        assertThat(hasViolationOn(request, "reason")).isFalse();
    }

    @Test
    void reasonOverMaxAllowedLengthIsRejected() {
        OutpassRequest request = baseRequest();
        request.setReason("a".repeat(501));

        assertThat(hasViolationOn(request, "reason")).isTrue();
    }

    // Regression for a mismatch found during live-environment verification: DTO validation
    // accepted up to 500 characters while the persisted Outpass.reason column was still
    // VARCHAR(50) (a leftover from before this @Size constraint existed), so a DTO-valid
    // request over 50 characters passed validation but crashed at insert time with a raw
    // DataIntegrityViolationException. Guards against the two limits drifting apart again.
    @Test
    void entityColumnLengthMatchesDtoMaxSize() throws NoSuchFieldException {
        Field reasonField = Outpass.class.getDeclaredField("reason");
        int columnLength = reasonField.getAnnotation(Column.class).length();

        assertThat(columnLength).isEqualTo(500);
    }
}
