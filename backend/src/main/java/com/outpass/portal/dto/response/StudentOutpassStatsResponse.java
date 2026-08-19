package com.outpass.portal.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentOutpassStatsResponse {
    private Long studentId;
    private String name;
    private String rollNo;
    private String department;
    private String hostel;
    
    // Overall statistics
    private Long totalOutpasses;
    private Long totalApproved;
    private Long totalDeclined;
    private Long totalCompleted;
    private Long totalOverdue;
    
    // Current status
    private Long currentlyActive; // APPROVED or DEPARTED
    private Boolean hasOverdueOutpass;
    
    // Performance metrics
    private Long lateReturns;
    private Double approvalRate; // percentage
    private Double onTimeCompletionRate; // percentage
    
    // Recent activity
    private String lastOutpassDate;
    private String lastOutpassStatus;
    
    // Risk indicators
    private Boolean hasActiveOutpass;
    private Integer overdueCount;
    private String riskLevel; // LOW, MEDIUM, HIGH
}
