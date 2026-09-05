package com.outpass.portal.util;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Validates that a client-supplied base64 data URI is actually decodable image content,
// not just a string with an image/* filename or MIME prefix. Nothing else in the codebase
// does this today (profilePicture/complaint photos rely only on client-side + @Size checks),
// so this is scoped to the id-card-photo upload path only -- see
// StudentService.updateIdCardPhoto -- rather than retrofitted onto those existing fields.
public final class ImagePhotoValidator {

    private static final Pattern DATA_URI_PATTERN =
            Pattern.compile("^data:image/(jpeg|jpg|png|gif|webp);base64,(.+)$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private ImagePhotoValidator() {
    }

    // Throws RuntimeException (caught by GlobalExceptionHandler.handleRuntimeException,
    // same as every other business-rule rejection in this codebase) with a client-safe message.
    public static void validate(String dataUri) {
        if (dataUri == null || dataUri.isBlank()) {
            throw new RuntimeException("ID card photo is required");
        }

        Matcher matcher = DATA_URI_PATTERN.matcher(dataUri.trim());
        if (!matcher.matches()) {
            throw new RuntimeException("ID card photo must be an image (JPEG, PNG, GIF, or WEBP)");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("ID card photo data is corrupted");
        }

        if (decoded.length == 0) {
            throw new RuntimeException("ID card photo data is empty");
        }

        try {
            if (ImageIO.read(new ByteArrayInputStream(decoded)) == null) {
                throw new RuntimeException("ID card photo does not appear to be a valid image");
            }
        } catch (IOException e) {
            throw new RuntimeException("ID card photo does not appear to be a valid image");
        }
    }
}
