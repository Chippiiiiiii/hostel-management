package com.outpass.portal.dto.response;

import com.outpass.portal.model.entity.Student;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StudentSummaryResponse {
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
    private String gender;
    private LocalDateTime createdAt;

    public static StudentSummaryResponse from(Student student) {
        return StudentSummaryResponse.builder()
                .id(student.getId())
                .name(student.getName())
                .email(student.getEmail())
                .rollNo(student.getRollNo())
                .department(student.getDepartment())
                .hostel(student.getHostel())
                .roomNumber(student.getRoomNumber())
                .contactNumber(student.getContactNumber())
                .parentNumber(student.getParentNumber())
                .profilePicture(student.getProfilePicture())
                .gender(student.getGender())
                .createdAt(student.getCreatedAt())
                .build();
    }
}
