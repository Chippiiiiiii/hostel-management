package com.outpass.portal.dto.response;

import java.time.LocalDateTime;

import com.outpass.portal.model.enums.OutpassStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutpassResponse {
    private Long id;
    private Long studentId;
    // Only populated for Warden/Security-facing responses (see
    // OutpassService.mapToResponse(Outpass, boolean)) -- never included in Student-facing
    // outpass responses, since a student viewing their own outpass has no need for it there.
    private String studentIdCardPhoto;
    private String name;
    private String rollNo;
    private String department;
    private String hostel;
    private String roomNumber;
    private LocalDateTime date;
    private LocalDateTime returnDate;
    private Integer noOfDays;
    private String reason;
    private String placeOfVisit;
    private String contactNumber;
    private String parentNumber;
    private OutpassStatus status;
    private LocalDateTime actualDepartureTime;
    private LocalDateTime actualReturnTime;
    private Long departureVerifiedBy;
    private Long returnVerifiedBy;
    private Boolean isLateReturn;
    private String declineReason;
    private String wardenComments;
    private Long processedBy;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

