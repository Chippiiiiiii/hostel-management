package com.outpass.portal.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.outpass.portal.exception.ForbiddenOperationException;
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

import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutpassService {

    // Student-submitted `date`/`returnDate` values come from a browser
    // datetime-local input representing IST wall-clock time with no timezone
    // offset attached. Comparing them against a bare LocalDateTime.now() (which
    // resolves to the JVM/container default zone, not necessarily IST) would
    // silently skew every date comparison below by the UTC-IST offset.
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // A student may only ever have one PENDING/APPROVED/DEPARTED outpass at a time -- these
    // are the statuses where the request is still "in flight" (awaiting a decision, or the
    // student is literally out on this pass right now). DECLINED/COMPLETED/OVERDUE outpasses
    // are resolved and never block a new request.
    private static final List<OutpassStatus> ACTIVE_OUTPASS_STATUSES =
            List.of(OutpassStatus.PENDING, OutpassStatus.APPROVED, OutpassStatus.DEPARTED);

    private final OutpassRepository outpassRepository;
    private final StudentRepository studentRepository;

    // Locks the student row before the "does this student already have an active outpass"
    // check, the same pattern RoomService.performAllocation uses for its own "already
    // allocated" check -- so two concurrent creation requests from the same student
    // serialize instead of both reading "no active outpass yet" before either commits.
    @Transactional
    public OutpassResponse createOutpass(Long studentId, OutpassRequest request) {
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (request.getReturnDate().isBefore(request.getDate())) {
            throw new RuntimeException("Return date must be after departure date");
        }

        if (outpassRepository.existsByStudentIdAndStatusIn(studentId, ACTIVE_OUTPASS_STATUSES)) {
            throw new RuntimeException(
                    "You already have an active outpass request. Cancel it or wait for it to be resolved before submitting a new one.");
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
                .reason(request.getReason())
                .placeOfVisit(request.getPlaceOfVisit())
                .contactNumber(request.getContactNumber())
                .parentNumber(request.getParentNumber())
                .status(OutpassStatus.PENDING)
                .build();

        Outpass saved = outpassRepository.save(outpass);
        return mapToResponse(saved);
    }

    @Transactional
    public void cancelOutpass(Long outpassId, Long studentId) {
        Outpass outpass = lockOutpass(outpassId);
        if (!outpass.getStudent().getId().equals(studentId)) {
            throw new ForbiddenOperationException("Access denied");
        }
        if (outpass.getStatus() != OutpassStatus.PENDING) {
            throw new RuntimeException("Only pending outpasses can be cancelled");
        }
        outpassRepository.delete(outpass);
    }

    @Transactional(readOnly = true)
    public OutpassResponse getOutpassById(Long outpassId, Long studentId) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new RuntimeException("Outpass not found"));

        if (studentId != null && !outpass.getStudent().getId().equals(studentId)) {
            throw new ForbiddenOperationException("Access denied");
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
    public List<OutpassResponse> getPendingOutpassesByHostels(List<String> hostels) {
        if (hostels.isEmpty()) {
            return List.of();
        }
        return outpassRepository.findByHostelInAndStatusOrderByCreatedAtDesc(hostels, OutpassStatus.PENDING)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getAllOutpassesByHostels(List<String> hostels) {
        if (hostels.isEmpty()) {
            return List.of();
        }
        return outpassRepository.findByHostelInOrderByCreatedAtDesc(hostels)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OutpassResponse approveOutpass(Long outpassId, List<String> wardenHostels, Long wardenId, ApproveOutpassRequest request) {
        Outpass outpass = lockOutpass(outpassId);

        if (outpass.getStatus() != OutpassStatus.PENDING) {
            throw new RuntimeException("Only pending outpasses can be approved");
        }

        if (!wardenHostels.contains(outpass.getHostel())) {
            throw new ForbiddenOperationException("You can only approve outpasses from your own hostel");
        }

        outpass.setStatus(OutpassStatus.APPROVED);
        outpass.setWardenComments(request != null ? request.getComments() : null);
        outpass.setProcessedBy(wardenId);
        outpass.setProcessedAt(LocalDateTime.now(IST));
        Outpass updated = outpassRepository.save(outpass);
        return mapToResponse(updated);
    }

    @Transactional
    public OutpassResponse declineOutpass(Long outpassId, List<String> wardenHostels, Long wardenId, DeclineOutpassRequest request) {
        Outpass outpass = lockOutpass(outpassId);

        if (outpass.getStatus() != OutpassStatus.PENDING) {
            throw new RuntimeException("Only pending outpasses can be declined");
        }

        if (!wardenHostels.contains(outpass.getHostel())) {
            throw new ForbiddenOperationException("You can only decline outpasses from your own hostel");
        }

        outpass.setStatus(OutpassStatus.DECLINED);
        outpass.setDeclineReason(request.getDeclineReason());
        outpass.setWardenComments(request.getComments());
        outpass.setProcessedBy(wardenId);
        outpass.setProcessedAt(LocalDateTime.now(IST));
        Outpass updated = outpassRepository.save(outpass);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getActiveOutpasses() {
        LocalDateTime now = LocalDateTime.now(IST);
        return outpassRepository.findByStatusOrderByCreatedAtDesc(OutpassStatus.APPROVED)
                .stream()
                .filter(o -> o.getDate().isBefore(now) && o.getReturnDate().isAfter(now))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getActiveOutpassesByHostel(String hostel) {
        LocalDateTime now = LocalDateTime.now(IST);
        return outpassRepository.findByHostelAndStatusOrderByCreatedAtDesc(hostel, OutpassStatus.APPROVED)
                .stream()
                .filter(o -> o.getDate().isBefore(now) && o.getReturnDate().isAfter(now))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getTodayOutpasses() {
        LocalDateTime startOfDay = LocalDateTime.now(IST).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now(IST).withHour(23).withMinute(59).withSecond(59);

        return outpassRepository.findByDateBetween(startOfDay, endOfDay)
                .stream()
                .filter(o -> o.getStatus() == OutpassStatus.APPROVED)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OutpassResponse> getTodayOutpassesByHostel(String hostel) {
        LocalDateTime startOfDay = LocalDateTime.now(IST).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now(IST).withHour(23).withMinute(59).withSecond(59);

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
            throw new ForbiddenOperationException("You can only view outpasses from your own hostel");
        }
        
        return mapToResponse(outpass);
    }

    // Locks the outpass row before any check-then-act status transition, preventing
    // lost updates when two requests (e.g. approve + decline, or two departure scans)
    // race against the same outpass.
    private Outpass lockOutpass(Long outpassId) {
        return outpassRepository.findByIdForUpdate(outpassId)
                .orElseThrow(() -> new RuntimeException("Outpass not found"));
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
                .reason(outpass.getReason())
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
        Outpass outpass = lockOutpass(outpassId);

        if (!outpass.getHostel().equals(hostel)) {
            throw new ForbiddenOperationException("You can only verify outpasses from your own hostel");
        }

        if (outpass.getStatus() != OutpassStatus.APPROVED) {
            throw new RuntimeException("Only approved outpasses can be marked as departed");
        }

        if (outpass.getActualDepartureTime() != null) {
            throw new RuntimeException("Departure already verified");
        }

        LocalDateTime now = LocalDateTime.now(IST);
        outpass.setActualDepartureTime(now);
        outpass.setDepartureVerifiedBy(securityGuardId);
        outpass.setStatus(OutpassStatus.DEPARTED);

        Outpass updated = outpassRepository.save(outpass);
        return mapToResponse(updated);
    }

    @Transactional
    public OutpassResponse markReturn(Long outpassId, Long securityGuardId, String hostel) {
        Outpass outpass = lockOutpass(outpassId);

        if (!outpass.getHostel().equals(hostel)) {
            throw new ForbiddenOperationException("You can only verify outpasses from your own hostel");
        }

        if (outpass.getStatus() != OutpassStatus.DEPARTED) {
            throw new RuntimeException("Student must be marked as departed before marking return");
        }

        if (outpass.getActualReturnTime() != null) {
            throw new RuntimeException("Return already verified");
        }

        LocalDateTime now = LocalDateTime.now(IST);
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
    public StudentOutpassStatsResponse getStudentStatistics(Long studentId, List<String> wardenHostels) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (wardenHostels != null && !wardenHostels.contains(student.getHostel())) {
            throw new ForbiddenOperationException("You can only view statistics for students in your own hostel");
        }

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



