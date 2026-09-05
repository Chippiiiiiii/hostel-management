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
 * Covers the photo-upload size cap: the frontend's 2MB client-side check
 * (EditProfile.jsx) is trivially bypassed by any client calling the API directly, so the
 * server must independently reject an oversized profilePicture.
 */
class StudentProfileUpdateRequestValidationTest {

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

    private StudentProfileUpdateRequest baseRequest() {
        StudentProfileUpdateRequest request = new StudentProfileUpdateRequest();
        request.setContactNumber("9000000000");
        request.setParentNumber("9000000001");
        return request;
    }

    private boolean hasViolationOn(StudentProfileUpdateRequest request, String property) {
        Set<ConstraintViolation<StudentProfileUpdateRequest>> violations = validator.validate(request);
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    void noProfilePicture_isAccepted() {
        StudentProfileUpdateRequest request = baseRequest();

        assertThat(hasViolationOn(request, "profilePicture")).isFalse();
    }

    @Test
    void profilePictureAtMaxAllowedLength_isAccepted() {
        StudentProfileUpdateRequest request = baseRequest();
        request.setProfilePicture("a".repeat(2_800_000));

        assertThat(hasViolationOn(request, "profilePicture")).isFalse();
    }

    @Test
    void profilePictureOverMaxAllowedLength_isRejected() {
        StudentProfileUpdateRequest request = baseRequest();
        request.setProfilePicture("a".repeat(2_800_001));

        assertThat(hasViolationOn(request, "profilePicture")).isTrue();
    }
}
