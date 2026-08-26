package com.outpass.portal.service;

import com.outpass.portal.dto.request.StudentProfileUpdateRequest;
import com.outpass.portal.dto.response.StudentProfileResponse;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        return StudentProfileResponse.builder()
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
                .build();
    }

    @Transactional
    public StudentProfileResponse updateProfile(Long studentId, StudentProfileUpdateRequest updateRequest) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Room is locked after registration. This endpoint never changes hostel/roomNumber:
        // room moves go exclusively through the room allocation endpoints. A caller trying
        // to actually change either value here is rejected rather than silently ignored.
        if (updateRequest.getHostel() != null && !updateRequest.getHostel().equals(student.getHostel())) {
            throw new RuntimeException("Room changes are not permitted here. Use room allocation endpoints.");
        }
        if (updateRequest.getRoomNumber() != null && !updateRequest.getRoomNumber().equals(student.getRoomNumber())) {
            throw new RuntimeException("Room changes are not permitted here. Use room allocation endpoints.");
        }

        // Update only editable fields
        student.setContactNumber(updateRequest.getContactNumber());
        student.setParentNumber(updateRequest.getParentNumber());
        if (updateRequest.getProfilePicture() != null) {
            student.setProfilePicture(updateRequest.getProfilePicture());
        }

        Student updated = studentRepository.save(student);
        return getProfile(updated.getId());
    }
}

