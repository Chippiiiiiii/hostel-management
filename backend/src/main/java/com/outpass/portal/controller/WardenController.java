package com.outpass.portal.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.outpass.portal.dto.request.ApproveOutpassRequest;
import com.outpass.portal.dto.request.DeclineOutpassRequest;
import com.outpass.portal.dto.response.ApiResponse;
import com.outpass.portal.dto.response.OutpassResponse;
import com.outpass.portal.dto.response.StudentOutpassStatsResponse;
import com.outpass.portal.dto.response.StudentSummaryResponse;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.entity.Warden;
import com.outpass.portal.model.enums.Role;
import com.outpass.portal.repository.AttendanceRecordRepository;
import com.outpass.portal.repository.RoomAllocationRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.service.AttendanceService;
import com.outpass.portal.service.ComplaintService;
import com.outpass.portal.service.OutpassService;
import com.outpass.portal.service.RoomService;

import com.outpass.portal.model.entity.Announcement;
import com.outpass.portal.repository.AnnouncementRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/warden")
@RequiredArgsConstructor
@Slf4j
public class WardenController {

    private final OutpassService outpassService;
    private final WardenRepository wardenRepository;
    private final AttendanceService attendanceService;
    private final RoomService roomService;
    private final StudentRepository studentRepository;
    private final ComplaintService complaintService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final RoomAllocationRepository roomAllocationRepository;
    private final AnnouncementRepository announcementRepository;

    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalStudents", studentRepository.countByHostel(warden.getHostel()));
        stats.put("allocatedStudents", roomAllocationRepository.countByRoom_Building_Name(warden.getHostel()));
        stats.put("todayAttendance", attendanceRecordRepository.countByDateAndStudent_Hostel(
                LocalDate.now(ZoneId.of("Asia/Kolkata")), warden.getHostel()));
        Map<String, Object> complaintStats = complaintService.getStatsByHostel(warden.getHostel());
        stats.put("pendingComplaints", complaintStats.getOrDefault("pending", 0));
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/outpass/pending")
    public ResponseEntity<ApiResponse<List<OutpassResponse>>> getPendingOutpasses(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        
        List<OutpassResponse> pending = outpassService.getPendingOutpassesByHostel(warden.getHostel());
        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    @PutMapping("/outpass/{id}/approve")
    public ResponseEntity<ApiResponse<OutpassResponse>> approveOutpass(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveOutpassRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        
        OutpassResponse approved = outpassService.approveOutpass(id, warden.getHostel(), warden.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Outpass approved successfully", approved));
    }

    @PutMapping("/outpass/{id}/decline")
    public ResponseEntity<ApiResponse<OutpassResponse>> declineOutpass(
            @PathVariable Long id,
            @RequestBody DeclineOutpassRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        
        OutpassResponse declined = outpassService.declineOutpass(id, warden.getHostel(), warden.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Outpass declined successfully", declined));
    }

    @PutMapping("/outpass/bulk-approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkApprove(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) request.get("ids");
        int success = 0;
        int failed = 0;
        for (Number id : ids) {
            try {
                outpassService.approveOutpass(id.longValue(), warden.getHostel(), warden.getId(), null);
                success++;
            } catch (Exception e) {
                log.warn("Bulk approve failed for outpass {}: {}", id, e.getMessage());
                failed++;
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
                success + " outpasses approved",
                Map.of("approved", success, "failed", failed, "total", ids.size())));
    }

    @PutMapping("/outpass/bulk-decline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkDecline(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) request.get("ids");
        String reason = (String) request.getOrDefault("reason", "Bulk declined");
        com.outpass.portal.dto.request.DeclineOutpassRequest declineReq = new com.outpass.portal.dto.request.DeclineOutpassRequest();
        declineReq.setDeclineReason(reason);
        int success = 0;
        int failed = 0;
        for (Number id : ids) {
            try {
                outpassService.declineOutpass(id.longValue(), warden.getHostel(), warden.getId(), declineReq);
                success++;
            } catch (Exception e) {
                log.warn("Bulk decline failed for outpass {}: {}", id, e.getMessage());
                failed++;
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
                success + " outpasses declined",
                Map.of("declined", success, "failed", failed, "total", ids.size())));
    }

    @GetMapping("/outpass/history")
    public ResponseEntity<ApiResponse<List<OutpassResponse>>> getOutpassHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        
        List<OutpassResponse> history = outpassService.getAllOutpassesByHostel(warden.getHostel());
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/student/{studentId}/stats")
    public ResponseEntity<ApiResponse<StudentOutpassStatsResponse>> getStudentStats(
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        StudentOutpassStatsResponse stats = outpassService.getStudentStatistics(studentId, warden.getHostel());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // ==================== Attendance ====================

    @PostMapping("/attendance/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startAttendance(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> session = attendanceService.startSession(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success("Attendance session started", session));
    }

    @PostMapping("/attendance/stop")
    public ResponseEntity<ApiResponse<Void>> stopAttendance() {
        attendanceService.stopSession();
        return ResponseEntity.ok(ApiResponse.success("Attendance session closed", null));
    }

    @GetMapping("/attendance/session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActiveSession() {
        Map<String, Object> session = attendanceService.getActiveSession();
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    @GetMapping("/attendance/session/{sessionId}/records")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSessionRecords(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        List<Map<String, Object>> records = attendanceService.getSessionRecords(sessionId, warden.getHostel());
        return ResponseEntity.ok(ApiResponse.success(records));
    }

    @GetMapping("/attendance/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAttendanceConfig() {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getAttendanceConfig()));
    }

    @PutMapping("/attendance/config")
    public ResponseEntity<ApiResponse<Void>> updateAttendanceConfig(
            @RequestBody Map<String, String> request) {
        attendanceService.updateAttendanceConfig(
                request.getOrDefault("wifiAllowedSubnets", ""),
                request.getOrDefault("hostelLatitude", "0"),
                request.getOrDefault("hostelLongitude", "0"),
                request.getOrDefault("hostelRadius", "50"));
        return ResponseEntity.ok(ApiResponse.success("Attendance config updated", null));
    }

    @GetMapping("/attendance/report")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAttendanceReport(
            @RequestParam String from, @RequestParam String to,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        var records = attendanceRecordRepository.findByDateBetweenOrderByDateDescMarkedAtDesc(fromDate, toDate)
                .stream().filter(r -> warden.getHostel().equals(r.getStudent().getHostel()))
                .collect(java.util.stream.Collectors.toList());
        List<Map<String, Object>> result = records.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", r.getDate().toString());
            row.put("time", r.getTime());
            row.put("studentName", r.getStudent().getName());
            row.put("rollNo", r.getStudent().getRollNo());
            row.put("department", r.getStudent().getDepartment());
            row.put("method", r.getMethod().toString());
            row.put("status", r.getStatus().toString());
            return row;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== Rooms ====================

    // Shared with Admin's Room Management page (see SecurityConfig: /warden/rooms/**
    // permits both WARDEN and ADMIN). A warden's "hostel" IS a Building's name (see
    // student.setHostel(room.getBuilding().getName()) in RoomService), so every
    // building/floor/room/allocation mutation below must be confined to the calling
    // warden's own building; null means unrestricted (Admin manages every hostel).
    private String resolveWardenHostel(UserPrincipal userPrincipal) {
        if (userPrincipal.getRole() != Role.WARDEN) {
            return null;
        }
        return wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"))
                .getHostel();
    }

    @GetMapping("/rooms/buildings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuildings(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(roomService.getBuildings(resolveWardenHostel(userPrincipal))));
    }

    @PutMapping("/rooms/buildings/{id}/rename")
    public ResponseEntity<ApiResponse<Map<String, Object>>> renameBuilding(
            @PathVariable Long id, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> building = roomService.renameBuilding(id, request.get("name"), resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Building renamed", building));
    }

    @PutMapping("/rooms/buildings/{id}/type")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBuildingType(
            @PathVariable Long id, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> building = roomService.updateBuildingType(id, request.get("type"), resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Building type updated", building));
    }

    @PutMapping("/rooms/buildings/{id}/gender")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBuildingGender(
            @PathVariable Long id, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> building = roomService.updateBuildingGender(id, request.get("gender"), resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Building gender updated", building));
    }

    // Creating a brand-new hostel/building has no existing owner to scope by, and this
    // app's model is one warden per existing hostel (assigned at warden-creation time) —
    // there's no legitimate warden workflow that needs to spin up an entirely new
    // building, so this is Admin-only.
    @PostMapping("/rooms/buildings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addBuilding(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal.getRole() != Role.ADMIN) {
            throw new RuntimeException("Only an admin can add a new hostel building");
        }
        Map<String, Object> building = roomService.addBuilding(
                request.get("name"),
                request.get("type"),
                request.get("gender"));
        return ResponseEntity.ok(ApiResponse.success("Building added", building));
    }

    @DeleteMapping("/rooms/buildings/{id}")
    public ResponseEntity<ApiResponse<Void>> removeBuilding(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.removeBuilding(id, resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Building removed", null));
    }

    @GetMapping("/rooms/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig() {
        return ResponseEntity.ok(ApiResponse.success(roomService.getConfig()));
    }

    @PutMapping("/rooms/config")
    public ResponseEntity<ApiResponse<Void>> updateConfig(@RequestBody Map<String, Object> request) {
        roomService.updateConfig(
                ((Number) request.getOrDefault("maxRoomsPerFloor", 10)).intValue(),
                ((Number) request.getOrDefault("maxMembersPerRoom", 6)).intValue());
        if (request.containsKey("wifiAllowedSubnets")) {
            roomService.updateWifiSubnets((String) request.get("wifiAllowedSubnets"));
        }
        return ResponseEntity.ok(ApiResponse.success("Config updated", null));
    }

    @PutMapping("/rooms/{roomId}/max-members")
    public ResponseEntity<ApiResponse<Void>> updateRoomMaxMembers(
            @PathVariable Long roomId, @RequestBody Map<String, Integer> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.updateRoomMaxMembers(roomId, request.get("maxMembers"), resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room capacity updated", null));
    }

    @PostMapping("/rooms/buildings/{buildingId}/floors")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addFloor(
            @PathVariable Long buildingId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> floor = roomService.addFloor(buildingId, resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Floor added", floor));
    }

    @DeleteMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}")
    public ResponseEntity<ApiResponse<Void>> removeFloor(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.removeFloor(buildingId, floorNumber, resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Floor removed", null));
    }

    @PostMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}/rooms")
    public ResponseEntity<ApiResponse<Void>> addRoom(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.addRoomToFloor(buildingId, floorNumber, resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room added", null));
    }

    @DeleteMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}/rooms/last")
    public ResponseEntity<ApiResponse<Void>> removeLastRoom(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.removeLastRoomFromFloor(buildingId, floorNumber, resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room removed", null));
    }

    @GetMapping("/rooms/allocations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllAllocations(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        // Shared with Admin's Room Management page (see SecurityConfig: /warden/rooms/**
        // permits both WARDEN and ADMIN) — admins have no Warden row to scope by, and are
        // meant to see allocations across every hostel, so only narrow the result for wardens.
        List<Map<String, Object>> allocations = userPrincipal.getRole() == Role.WARDEN
                ? roomService.getAllocationsByHostel(wardenRepository.findById(userPrincipal.getId())
                        .orElseThrow(() -> new RuntimeException("Warden not found"))
                        .getHostel())
                : roomService.getAllAllocations();
        return ResponseEntity.ok(ApiResponse.success(allocations));
    }

    @PostMapping("/rooms/{roomId}/allocate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> allocateStudent(
            @PathVariable Long roomId, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> allocation = roomService.allocateStudent(
                roomId,
                request.get("name"),
                request.get("rollNo"),
                request.get("department"),
                request.get("email"),
                resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Student allocated", allocation));
    }

    @DeleteMapping("/rooms/allocations/{email}")
    public ResponseEntity<ApiResponse<Void>> removeAllocation(
            @PathVariable String email, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.removeAllocation(email, resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Student removed from room", null));
    }

    @PutMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}/department")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setFloorDepartment(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> result = roomService.setFloorDepartment(
                buildingId, floorNumber, request.get("department"), resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Floor department updated", result));
    }

    @PutMapping("/rooms/{roomId}/department")
    public ResponseEntity<ApiResponse<Void>> setRoomDepartmentOverride(
            @PathVariable Long roomId, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.setRoomDepartmentOverride(roomId, request.get("department"), resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room department override set", null));
    }

    @DeleteMapping("/rooms/{roomId}/department")
    public ResponseEntity<ApiResponse<Void>> removeRoomDepartmentOverride(
            @PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.removeRoomDepartmentOverride(roomId, resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room department override removed", null));
    }

    @PutMapping("/rooms/{roomId}/number")
    public ResponseEntity<ApiResponse<Void>> updateRoomNumber(
            @PathVariable Long roomId, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.updateRoomNumber(roomId, request.get("roomNumber"), resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room number updated", null));
    }

    @PostMapping("/rooms/buildings/{buildingId}/auto-allocate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> autoAllocate(
            @PathVariable Long buildingId,
            @RequestParam(required = false) Integer floorNumber,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> result = roomService.bulkAllocate(
                buildingId, floorNumber, userPrincipal.getEmail(), userPrincipal.getRole().name(),
                resolveWardenHostel(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Bulk allocation completed", result));
    }

    @GetMapping("/students")
    public ResponseEntity<ApiResponse<List<StudentSummaryResponse>>> getStudents(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        // Shared with Admin's Room Management page (see SecurityConfig: /warden/students
        // permits both WARDEN and ADMIN) — admins have no Warden row to scope by, and are
        // meant to see students across every hostel, so only narrow the result for wardens.
        List<Student> students = userPrincipal.getRole() == Role.WARDEN
                ? studentRepository.findByHostel(wardenRepository.findById(userPrincipal.getId())
                        .orElseThrow(() -> new RuntimeException("Warden not found"))
                        .getHostel())
                : studentRepository.findAll();
        List<StudentSummaryResponse> response = students.stream()
                .map(StudentSummaryResponse::from)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==================== Complaints ====================

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllComplaints(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        List<Map<String, Object>> complaints = status != null && !status.isEmpty()
                ? complaintService.getComplaintsByHostelAndStatus(warden.getHostel(), status)
                : complaintService.getComplaintsByHostel(warden.getHostel());
        return ResponseEntity.ok(ApiResponse.success(complaints));
    }

    @GetMapping("/complaints/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getComplaintStats(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        return ResponseEntity.ok(ApiResponse.success(complaintService.getStatsByHostel(warden.getHostel())));
    }

    @PutMapping("/complaints/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateComplaint(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        String status = request.get("status");
        String wardenResponse = request.get("wardenResponse");
        Map<String, Object> complaint = complaintService.updateComplaintStatus(
                id, status, wardenResponse, userPrincipal.getId(), warden.getHostel());
        return ResponseEntity.ok(ApiResponse.success("Complaint updated", complaint));
    }

    // ==================== Announcements ====================

    @GetMapping("/announcements")
    public ResponseEntity<ApiResponse<List<Announcement>>> getAnnouncements() {
        return ResponseEntity.ok(ApiResponse.success(announcementRepository.findAllByOrderByCreatedAtDesc()));
    }

    @PostMapping("/announcements")
    public ResponseEntity<ApiResponse<Announcement>> createAnnouncement(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        Warden warden = wardenRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        Announcement announcement = Announcement.builder()
                .title(request.get("title"))
                .content(request.get("content"))
                .priority(request.getOrDefault("priority", "NORMAL"))
                .postedBy(warden.getId())
                .postedByName(warden.getName())
                .build();
        announcementRepository.save(announcement);
        return ResponseEntity.ok(ApiResponse.success("Announcement posted", announcement));
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAnnouncement(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Announcement not found"));
        if (!announcement.getPostedBy().equals(userPrincipal.getId())) {
            throw new RuntimeException("You can only delete your own announcements");
        }
        announcementRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Announcement deleted", null));
    }
}

