package com.outpass.portal.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileResponse {
    private Long id;
    private String name;
    private String email;
    private String rollNo;
    private String department;
    private String hostel;
    private String roomNumber;
    private String contactNumber;
    private String parentNumber;
    private String profilePicture;
}

