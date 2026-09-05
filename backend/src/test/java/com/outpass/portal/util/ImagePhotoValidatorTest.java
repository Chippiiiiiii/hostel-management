package com.outpass.portal.util;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagePhotoValidatorTest {

    private String realPngDataUri() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void acceptsARealPngImage() throws Exception {
        assertThatCode(() -> ImagePhotoValidator.validate(realPngDataUri())).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullOrBlank() {
        assertThatThrownBy(() -> ImagePhotoValidator.validate(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> ImagePhotoValidator.validate("  ")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsAValueThatIsNotADataUriAtAll() {
        assertThatThrownBy(() -> ImagePhotoValidator.validate("not-an-image"))
                .isInstanceOf(RuntimeException.class);
    }

    // A malicious/careless client can label arbitrary bytes with an image/* MIME prefix --
    // the filename/prefix alone must not be trusted as proof of actual image content.
    @Test
    void rejectsBase64PayloadThatIsNotActuallyDecodableImageContent() {
        String fakeImage = "data:image/png;base64," + Base64.getEncoder().encodeToString("not a real image".getBytes());
        assertThatThrownBy(() -> ImagePhotoValidator.validate(fakeImage))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("valid image");
    }

    @Test
    void rejectsMalformedBase64Data() {
        assertThatThrownBy(() -> ImagePhotoValidator.validate("data:image/png;base64,***not-base64***"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsUnsupportedMimeType() throws Exception {
        String svg = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg></svg>".getBytes());
        assertThatThrownBy(() -> ImagePhotoValidator.validate(svg))
                .isInstanceOf(RuntimeException.class);
    }
}
