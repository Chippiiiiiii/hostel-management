package com.outpass.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdCardPhotoUpdateRequest {

    // Same base64 data-URI representation and size bound as
    // StudentRegistrationRequest.profilePicture (2MB raw image -> ~2.8M base64 chars).
    // Real image-content validation happens separately in StudentService via
    // ImagePhotoValidator, since this bound only limits length, not content.
    @NotBlank(message = "ID card photo is required")
    @Size(max = 2_800_000, message = "ID card photo is too large (max 2MB)")
    private String idCardPhoto;
}
