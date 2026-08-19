package com.outpass.portal.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApproveOutpassRequest {
    
    @Size(max = 500, message = "Comments cannot exceed 500 characters")
    private String comments;
}
