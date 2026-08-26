package com.outpass.portal.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentRegistrationRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Roll number is required")
    @Size(max = 20, message = "Roll number cannot exceed 20 characters")
    private String rollNo;

    @NotBlank(message = "Department is required")
    private String department;

    @NotNull(message = "Academic year is required")
    @Min(value = 1, message = "Academic year must be between 1 and 4")
    @Max(value = 4, message = "Academic year must be between 1 and 4")
    private Integer year;

    @NotBlank(message = "Hostel is required")
    private String hostel;

    @NotBlank(message = "Room number is required")
    private String roomNumber;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Contact number must be 10 digits")
    private String contactNumber;

    @NotBlank(message = "Parent number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Parent number must be 10 digits")
    private String parentNumber;

    private String profilePicture;

    private String gender;
}

