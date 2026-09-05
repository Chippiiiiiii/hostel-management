package com.outpass.portal.service;

import com.outpass.portal.exception.ForbiddenOperationException;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplaintService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Complaint.photo has no DTO/@Size to lean on -- the controller passes it through as a
    // raw Map value -- so the same 2.8M-character cap used for profilePicture (see
    // StudentRegistrationRequest.profilePicture) is enforced explicitly here instead.
    private static final int MAX_PHOTO_BASE64_LENGTH = 2_800_000;

    private final ComplaintRepository complaintRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public Map<String, Object> createComplaint(Long studentId, String category, String description, String photo) {
        // Plain RuntimeException (not IllegalArgumentException): this message is a safe,
        // intentional business-rule string meant to reach the client verbatim, unlike
        // IllegalArgumentException's use elsewhere for framework-internal messages that
        // GlobalExceptionHandler deliberately replaces with a generic one.
        if (photo != null && photo.length() > MAX_PHOTO_BASE64_LENGTH) {
            throw new RuntimeException("Photo is too large (max 2MB)");
        }

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
    public List<Map<String, Object>> getComplaintsByHostels(List<String> hostels) {
        if (hostels.isEmpty()) {
            return List.of();
        }
        return complaintRepository.findByHostelInOrderByCreatedAtDesc(hostels)
                .stream().map(this::mapComplaint).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getComplaintsByHostelsAndStatus(List<String> hostels, String status) {
        if (hostels.isEmpty()) {
            return List.of();
        }
        return complaintRepository.findByHostelInAndStatusOrderByCreatedAtDesc(hostels, ComplaintStatus.valueOf(status))
                .stream().map(this::mapComplaint).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> updateComplaintStatus(Long complaintId, String status, String wardenResponse, Long wardenId, List<String> wardenHostels) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        if (!wardenHostels.contains(complaint.getHostel())) {
            throw new ForbiddenOperationException("You can only respond to complaints from your own hostel");
        }

        complaint.setStatus(ComplaintStatus.valueOf(status));
        if (wardenResponse != null && !wardenResponse.isBlank()) {
            complaint.setWardenResponse(wardenResponse);
        }
        complaint.setRespondedBy(wardenId);
        complaint.setRespondedAt(LocalDateTime.now(IST));

        complaintRepository.save(complaint);
        return mapComplaint(complaint);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatsByHostels(List<String> hostels) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (hostels.isEmpty()) {
            stats.put("total", 0L);
            stats.put("pending", 0L);
            stats.put("inProgress", 0L);
            stats.put("resolved", 0L);
            stats.put("rejected", 0L);
            return stats;
        }
        stats.put("total", complaintRepository.countByHostelIn(hostels));
        stats.put("pending", complaintRepository.countByHostelInAndStatus(hostels, ComplaintStatus.PENDING));
        stats.put("inProgress", complaintRepository.countByHostelInAndStatus(hostels, ComplaintStatus.IN_PROGRESS));
        stats.put("resolved", complaintRepository.countByHostelInAndStatus(hostels, ComplaintStatus.RESOLVED));
        stats.put("rejected", complaintRepository.countByHostelInAndStatus(hostels, ComplaintStatus.REJECTED));
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
