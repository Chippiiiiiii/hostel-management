package com.outpass.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileUpdateRequest {

    // Room is locked after registration (see StudentService.updateProfile): these are
    // accepted only so an older client can still send its current, unchanged values;
    // any attempt to actually change them here is rejected. Room changes go exclusively
    // through the room allocation endpoints (self-service pre-lock, warden/admin after).
    private String hostel;

    private String roomNumber;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Contact number must be 10 digits")
    private String contactNumber;
    
    @NotBlank(message = "Parent number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Parent number must be 10 digits")
    private String parentNumber;

    // See StudentRegistrationRequest.profilePicture for how this limit was derived.
    @Size(max = 2_800_000, message = "Profile picture is too large (max 2MB)")
    private String profilePicture;
}
