package com.outpass.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutpassRequest {

    @NotBlank(message = "Place of visit is required")
    private String placeOfVisit;

    @NotNull(message = "Date of leaving is required")
    private LocalDateTime date;

    @NotNull(message = "Return date is required")
    private LocalDateTime returnDate;

    @NotNull(message = "Number of days is required")
    @Positive(message = "Number of days must be positive")
    private Integer noOfDays;

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Contact number must be 10 digits")
    private String contactNumber;

    @NotBlank(message = "Parent number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Parent number must be 10 digits")
    private String parentNumber;
}

