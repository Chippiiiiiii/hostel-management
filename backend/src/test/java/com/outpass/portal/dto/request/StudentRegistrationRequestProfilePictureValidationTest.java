package com.outpass.portal.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the photo-upload size cap on the (unauthenticated, permitAll) registration
 * endpoint -- an oversized profilePicture here is a pre-auth attack surface, so it needs the
 * same server-side cap as the authenticated profile-update path.
 */
class StudentRegistrationRequestProfilePictureValidationTest {

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

    private boolean hasViolationOn(StudentRegistrationRequest request, String property) {
        Set<ConstraintViolation<StudentRegistrationRequest>> violations = validator.validate(request);
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    void profilePictureAtMaxAllowedLength_isAccepted() {
        StudentRegistrationRequest request = new StudentRegistrationRequest();
        request.setProfilePicture("a".repeat(2_800_000));

        assertThat(hasViolationOn(request, "profilePicture")).isFalse();
    }

    @Test
    void profilePictureOverMaxAllowedLength_isRejected() {
        StudentRegistrationRequest request = new StudentRegistrationRequest();
        request.setProfilePicture("a".repeat(2_800_001));

        assertThat(hasViolationOn(request, "profilePicture")).isTrue();
    }
}
