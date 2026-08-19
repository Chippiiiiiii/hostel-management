package com.outpass.portal.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.outpass.portal.dto.request.ApproveOutpassRequest;
import com.outpass.portal.dto.request.DeclineOutpassRequest;
import com.outpass.portal.dto.request.OutpassRequest;
import com.outpass.portal.dto.response.OutpassResponse;
import com.outpass.portal.dto.response.StudentOutpassStatsResponse;
import com.outpass.portal.model.entity.Outpass;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.OutpassStatus;
import com.outpass.portal.repository.OutpassRepository;
import com.outpass.portal.repository.StudentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutpassService {

    private final OutpassRepository outpassRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public OutpassResponse createOutpass(Long studentId, OutpassRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (request.getReturnDate().isBefore(request.getDate())) {
            throw new RuntimeException("Return date must be after departure date");
        }

        Outpass outpass = Outpass.builder()
                .student(student)
                .name(student.getName())
                .rollNo(student.getRollNo())
                .department(student.getDepartment())
                .hostel(student.getHostel())
                .roomNumber(student.getRoomNumber())
                .date(request.getDate())
                .returnDate(request.getReturnDate())
                .noOfDays(request.getNoOfDays())
                .placeOfVisit(request.getPlaceOfVisit())
                .contactNumber(request.getContactNumber())
                .parentNumber(request.getParentNumber())
                .status(OutpassStatus.PENDING)
                .build();

        Outpass saved = outpassRepository.save(outpass);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OutpassResponse getOutpassById(Long outpassId, Long studentId) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new RuntimeException("Outpass not found"));

        if (studentId != null && !outpass.getStudent().getId().equals(studentId)) {
            throw new RuntimeException("Access denied");
        }

        return mapToResponse(outpass);
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getStudentOutpasses(Long studentId) {
        return outpassRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getPendingOutpasses() {
        return outpassRepository.findByStatusOrderByCreatedAtDesc(OutpassStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getPendingOutpassesByHostel(String hostel) {
        return outpassRepository.findByHostelAndStatusOrderByCreatedAtDesc(hostel, OutpassStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getAllOutpassesByHostel(String hostel) {
        return outpassRepository.findByHostelOrderByCreatedAtDesc(hostel)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OutpassResponse approveOutpass(Long outpassId, String wardenHostel, Long wardenId, ApproveOutpassRequest request) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new RuntimeException("Outpass not found"));

        if (outpass.getStatus() != OutpassStatus.PENDING) {
            throw new RuntimeException("Only pending outpasses can be approved");
        }

        if (!outpass.getHostel().equals(wardenHostel)) {
            throw new RuntimeException("You can only approve outpasses from your own hostel");
        }

        outpass.setStatus(OutpassStatus.APPROVED);
        outpass.setWardenComments(request != null ? request.getComments() : null);
        outpass.setProcessedBy(wardenId);
        outpass.setProcessedAt(LocalDateTime.now());
        Outpass updated = outpassRepository.save(outpass);
        return mapToResponse(updated);
    }

    @Transactional
    public OutpassResponse declineOutpass(Long outpassId, String wardenHostel, Long wardenId, DeclineOutpassRequest request) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new RuntimeException("Outpass not found"));

        if (outpass.getStatus() != OutpassStatus.PENDING) {
            throw new RuntimeException("Only pending outpasses can be declined");
        }

        if (!outpass.getHostel().equals(wardenHostel)) {
            throw new RuntimeException("You can only decline outpasses from your own hostel");
        }

        outpass.setStatus(OutpassStatus.DECLINED);
        outpass.setDeclineReason(request.getDeclineReason());
        outpass.setWardenComments(request.getComments());
        outpass.setProcessedBy(wardenId);
        outpass.setProcessedAt(LocalDateTime.now());
        Outpass updated = outpassRepository.save(outpass);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getActiveOutpasses() {
        LocalDateTime now = LocalDateTime.now();
        return outpassRepository.findByStatusOrderByCreatedAtDesc(OutpassStatus.APPROVED)
                .stream()
                .filter(o -> o.getDate().isBefore(now) && o.getReturnDate().isAfter(now))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getActiveOutpassesByHostel(String hostel) {
        LocalDateTime now = LocalDateTime.now();
        return outpassRepository.findByHostelAndStatusOrderByCreatedAtDesc(hostel, OutpassStatus.APPROVED)
                .stream()
                .filter(o -> o.getDate().isBefore(now) && o.getReturnDate().isAfter(now))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getTodayOutpasses() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        return outpassRepository.findByDateBetween(startOfDay, endOfDay)
                .stream()
                .filter(o -> o.getStatus() == OutpassStatus.APPROVED)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getTodayOutpassesByHostel(String hostel) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        return outpassRepository.findByHostelAndDateBetween(hostel, startOfDay, endOfDay)
                .stream()
                .filter(o -> o.getStatus() == OutpassStatus.APPROVED)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OutpassResponse getOutpassByIdAndHostel(Long id, String hostel) {
        Outpass outpass = outpassRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Outpass not found"));
        
        if (hostel != null && !outpass.getHostel().equals(hostel)) {
            throw new RuntimeException("You can only view outpasses from your own hostel");
        }
        
        return mapToResponse(outpass);
    }

    private OutpassResponse mapToResponse(Outpass outpass) {
        return OutpassResponse.builder()
                .id(outpass.getId())
                .studentId(outpass.getStudent().getId())
                .name(outpass.getName())
                .rollNo(outpass.getRollNo())
                .department(outpass.getDepartment())
                .hostel(outpass.getHostel())
                .roomNumber(outpass.getRoomNumber())
                .date(outpass.getDate())
                .returnDate(outpass.getReturnDate())
                .noOfDays(outpass.getNoOfDays())
                .placeOfVisit(outpass.getPlaceOfVisit())
                .contactNumber(outpass.getContactNumber())
                .parentNumber(outpass.getParentNumber())
                .status(outpass.getStatus())
                .actualDepartureTime(outpass.getActualDepartureTime())
                .actualReturnTime(outpass.getActualReturnTime())
                .departureVerifiedBy(outpass.getDepartureVerifiedBy())
                .returnVerifiedBy(outpass.getReturnVerifiedBy())
                .isLateReturn(outpass.getIsLateReturn())
                .declineReason(outpass.getDeclineReason())
                .wardenComments(outpass.getWardenComments())
                .processedBy(outpass.getProcessedBy())
                .processedAt(outpass.getProcessedAt())
                .createdAt(outpass.getCreatedAt())
                .updatedAt(outpass.getUpdatedAt())
                .build();
    }

    @Transactional
    public OutpassResponse markDeparture(Long outpassId, Long securityGuardId, String hostel) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new RuntimeException("Outpass not found"));

        if (!outpass.getHostel().equals(hostel)) {
            throw new RuntimeException("You can only verify outpasses from your own hostel");
        }

        if (outpass.getStatus() != OutpassStatus.APPROVED) {
            throw new RuntimeException("Only approved outpasses can be marked as departed");
        }

        if (outpass.getActualDepartureTime() != null) {
            throw new RuntimeException("Departure already verified");
        }

        LocalDateTime now = LocalDateTime.now();
        outpass.setActualDepartureTime(now);
        outpass.setDepartureVerifiedBy(securityGuardId);
        outpass.setStatus(OutpassStatus.DEPARTED);

        Outpass updated = outpassRepository.save(outpass);
        return mapToResponse(updated);
    }

    @Transactional
    public OutpassResponse markReturn(Long outpassId, Long securityGuardId, String hostel) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new RuntimeException("Outpass not found"));

        if (!outpass.getHostel().equals(hostel)) {
            throw new RuntimeException("You can only verify outpasses from your own hostel");
        }

        if (outpass.getStatus() != OutpassStatus.DEPARTED) {
            throw new RuntimeException("Student must be marked as departed before marking return");
        }

        if (outpass.getActualReturnTime() != null) {
            throw new RuntimeException("Return already verified");
        }

        LocalDateTime now = LocalDateTime.now();
        outpass.setActualReturnTime(now);
        outpass.setReturnVerifiedBy(securityGuardId);

        // Check if student is late
        if (now.isAfter(outpass.getReturnDate())) {
            outpass.setIsLateReturn(true);
            outpass.setStatus(OutpassStatus.OVERDUE);
        } else {
            outpass.setIsLateReturn(false);
            outpass.setStatus(OutpassStatus.COMPLETED);
        }

        Outpass updated = outpassRepository.save(outpass);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getDepartedOutpassesByHostel(String hostel) {
        return outpassRepository.findByHostelAndStatusOrderByCreatedAtDesc(hostel, OutpassStatus.DEPARTED)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StudentOutpassStatsResponse getStudentStatistics(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<Outpass> allOutpasses = outpassRepository.findByStudentIdOrderByCreatedAtDesc(studentId);

        long totalOutpasses = allOutpasses.size();
        long totalApproved = allOutpasses.stream()
                .filter(o -> o.getStatus() == OutpassStatus.APPROVED || 
                            o.getStatus() == OutpassStatus.DEPARTED ||
                            o.getStatus() == OutpassStatus.COMPLETED ||
                            o.getStatus() == OutpassStatus.OVERDUE)
                .count();
        long totalDeclined = allOutpasses.stream()
                .filter(o -> o.getStatus() == OutpassStatus.DECLINED)
                .count();
        long totalCompleted = allOutpasses.stream()
                .filter(o -> o.getStatus() == OutpassStatus.COMPLETED)
                .count();
        long totalOverdue = allOutpasses.stream()
                .filter(o -> o.getStatus() == OutpassStatus.OVERDUE)
                .count();
        long lateReturns = allOutpasses.stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsLateReturn()))
                .count();

        // Current status
        long currentlyActive = allOutpasses.stream()
                .filter(o -> o.getStatus() == OutpassStatus.APPROVED || o.getStatus() == OutpassStatus.DEPARTED)
                .count();
        boolean hasOverdueOutpass = allOutpasses.stream()
                .anyMatch(o -> o.getStatus() == OutpassStatus.OVERDUE);

        // Rates
        double approvalRate = totalOutpasses > 0 ? (totalApproved * 100.0 / totalOutpasses) : 0.0;
        double onTimeCompletionRate = totalCompleted > 0 ? 
            ((totalCompleted - lateReturns) * 100.0 / totalCompleted) : 100.0;

        // Recent activity
        String lastOutpassDate = null;
        String lastOutpassStatus = null;
        if (!allOutpasses.isEmpty()) {
            Outpass lastOutpass = allOutpasses.get(0);
            lastOutpassDate = lastOutpass.getCreatedAt().toString();
            lastOutpassStatus = lastOutpass.getStatus().toString();
        }

        // Risk assessment
        boolean hasActiveOutpass = currentlyActive > 0;
        int overdueCount = (int) totalOverdue;
        String riskLevel = calculateRiskLevel(lateReturns, overdueCount, totalOutpasses);

        return StudentOutpassStatsResponse.builder()
                .studentId(student.getId())
                .name(student.getName())
                .rollNo(student.getRollNo())
                .department(student.getDepartment())
                .hostel(student.getHostel())
                .totalOutpasses(totalOutpasses)
                .totalApproved(totalApproved)
                .totalDeclined(totalDeclined)
                .totalCompleted(totalCompleted)
                .totalOverdue(totalOverdue)
                .currentlyActive(currentlyActive)
                .hasOverdueOutpass(hasOverdueOutpass)
                .lateReturns(lateReturns)
                .approvalRate(Math.round(approvalRate * 10.0) / 10.0)
                .onTimeCompletionRate(Math.round(onTimeCompletionRate * 10.0) / 10.0)
                .lastOutpassDate(lastOutpassDate)
                .lastOutpassStatus(lastOutpassStatus)
                .hasActiveOutpass(hasActiveOutpass)
                .overdueCount(overdueCount)
                .riskLevel(riskLevel)
                .build();
    }

    private String calculateRiskLevel(long lateReturns, int overdueCount, long totalOutpasses) {
        if (totalOutpasses == 0) return "LOW";
        
        if (overdueCount > 0 || lateReturns >= 3) {
            return "HIGH";
        } else if (lateReturns > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }
}



