package com.outpass.portal.service;

import com.outpass.portal.model.entity.Complaint;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.ComplaintCategory;
import com.outpass.portal.model.enums.ComplaintStatus;
import com.outpass.portal.repository.ComplaintRepository;
import com.outpass.portal.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public Map<String, Object> createComplaint(Long studentId, String category, String description, String photo) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Complaint complaint = Complaint.builder()
                .student(student)
                .studentName(student.getName())
                .studentRollNo(student.getRollNo())
                .hostel(student.getHostel())
                .roomNumber(student.getRoomNumber())
                .category(ComplaintCategory.valueOf(category))
                .description(description)
                .photo(photo)
                .build();

        complaintRepository.save(complaint);
        return mapComplaint(complaint);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStudentComplaints(Long studentId) {
        return complaintRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream().map(this::mapComplaint).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllComplaints() {
        return complaintRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::mapComplaint).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getComplaintsByStatus(String status) {
        return complaintRepository.findByStatusOrderByCreatedAtDesc(ComplaintStatus.valueOf(status))
                .stream().map(this::mapComplaint).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> updateComplaintStatus(Long complaintId, String status, String wardenResponse, Long wardenId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        complaint.setStatus(ComplaintStatus.valueOf(status));
        if (wardenResponse != null && !wardenResponse.isBlank()) {
            complaint.setWardenResponse(wardenResponse);
        }
        complaint.setRespondedBy(wardenId);
        complaint.setRespondedAt(LocalDateTime.now());

        complaintRepository.save(complaint);
        return mapComplaint(complaint);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", complaintRepository.count());
        stats.put("pending", complaintRepository.countByStatus(ComplaintStatus.PENDING));
        stats.put("inProgress", complaintRepository.countByStatus(ComplaintStatus.IN_PROGRESS));
        stats.put("resolved", complaintRepository.countByStatus(ComplaintStatus.RESOLVED));
        stats.put("rejected", complaintRepository.countByStatus(ComplaintStatus.REJECTED));
        return stats;
    }

    private Map<String, Object> mapComplaint(Complaint c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("studentName", c.getStudentName());
        map.put("studentRollNo", c.getStudentRollNo());
        map.put("hostel", c.getHostel());
        map.put("roomNumber", c.getRoomNumber());
        map.put("category", c.getCategory().name());
        map.put("description", c.getDescription());
        map.put("photo", c.getPhoto());
        map.put("status", c.getStatus().name());
        map.put("wardenResponse", c.getWardenResponse());
        map.put("createdAt", c.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        map.put("respondedAt", c.getRespondedAt() != null
                ? c.getRespondedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
        return map;
    }
}
