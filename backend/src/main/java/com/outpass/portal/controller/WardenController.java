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
import com.outpass.portal.service.WardenBuildingService;

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
    private final WardenBuildingService wardenBuildingService;

    // Every warden-scoped read/write below is confined to exactly the buildings an admin
    // assigned via warden_buildings (see WardenBuildingService) -- a warden with N assigned
    // buildings can operate across all N, not just Warden.hostel's single "primary". Always
    // non-null for a WARDEN caller: an empty list correctly means "no buildings assigned,
    // deny everything" rather than falling through to unrestricted (that's the ADMIN-only
    // null sentinel used by resolveWardenHostels in the rooms section below).
    private List<String> assignedHostelsOf(Long wardenId) {
        return wardenBuildingService.getAssignedHostelNames(wardenId);
    }

    // Building.id equivalent of assignedHostelsOf, for the attendance session/config and
    // room config endpoints below -- these target attendance_sessions/room_config, which
    // are keyed by a real building_id FK rather than the free-text hostel-name string
    // every other warden-scoped query uses. Same never-null-for-WARDEN contract.
    private List<Long> assignedBuildingIdsOf(Long wardenId) {
        return wardenBuildingService.getAssignedBuildingIds(wardenId);
    }

    // ADMIN equivalent of resolveWardenHostels (rooms section below), for the same
    // building_id-based endpoints: null means unrestricted.
    private List<Long> resolveWardenBuildingIds(UserPrincipal userPrincipal) {
        if (userPrincipal.getRole() != Role.WARDEN) {
            return null;
        }
        return assignedBuildingIdsOf(userPrincipal.getId());
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardStats(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<String> hostels = assignedHostelsOf(userPrincipal.getId());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalStudents", studentRepository.countByHostelIn(hostels));
        stats.put("allocatedStudents", roomAllocationRepository.countByRoom_Building_NameIn(hostels));
        stats.put("todayAttendance", attendanceRecordRepository.countByDateAndStudent_HostelIn(
                LocalDate.now(ZoneId.of("Asia/Kolkata")), hostels));
        Map<String, Object> complaintStats = complaintService.getStatsByHostels(hostels);
        stats.put("pendingComplaints", complaintStats.getOrDefault("pending", 0));
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/outpass/pending")
    public ResponseEntity<ApiResponse<List<OutpassResponse>>> getPendingOutpasses(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<OutpassResponse> pending = outpassService.getPendingOutpassesByHostels(assignedHostelsOf(userPrincipal.getId()));
        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    @PutMapping("/outpass/{id}/approve")
    public ResponseEntity<ApiResponse<OutpassResponse>> approveOutpass(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveOutpassRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        OutpassResponse approved = outpassService.approveOutpass(
                id, assignedHostelsOf(userPrincipal.getId()), userPrincipal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Outpass approved successfully", approved));
    }

    @PutMapping("/outpass/{id}/decline")
    public ResponseEntity<ApiResponse<OutpassResponse>> declineOutpass(
            @PathVariable Long id,
            @RequestBody DeclineOutpassRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        OutpassResponse declined = outpassService.declineOutpass(
                id, assignedHostelsOf(userPrincipal.getId()), userPrincipal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Outpass declined successfully", declined));
    }

    @PutMapping("/outpass/bulk-approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkApprove(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<String> hostels = assignedHostelsOf(userPrincipal.getId());
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) request.get("ids");
        int success = 0;
        int failed = 0;
        for (Number id : ids) {
            try {
                outpassService.approveOutpass(id.longValue(), hostels, userPrincipal.getId(), null);
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
        List<String> hostels = assignedHostelsOf(userPrincipal.getId());
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) request.get("ids");
        String reason = (String) request.getOrDefault("reason", "Bulk declined");
        com.outpass.portal.dto.request.DeclineOutpassRequest declineReq = new com.outpass.portal.dto.request.DeclineOutpassRequest();
        declineReq.setDeclineReason(reason);
        int success = 0;
        int failed = 0;
        for (Number id : ids) {
            try {
                outpassService.declineOutpass(id.longValue(), hostels, userPrincipal.getId(), declineReq);
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
        List<OutpassResponse> history = outpassService.getAllOutpassesByHostels(assignedHostelsOf(userPrincipal.getId()));
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/student/{studentId}/stats")
    public ResponseEntity<ApiResponse<StudentOutpassStatsResponse>> getStudentStats(
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        StudentOutpassStatsResponse stats = outpassService.getStudentStatistics(
                studentId, assignedHostelsOf(userPrincipal.getId()));
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // ==================== Attendance ====================

    // Every request below must name the building it targets -- there is no implicit
    // "my hostel" for a warden who may now be assigned to several (see
    // backend/AGENTS.md). A buildingId outside the caller's assigned set (or, for a
    // WARDEN with zero assignments, any buildingId at all) is rejected by
    // AttendanceService's ownership check, never silently ignored or widened.
    private Long requireBuildingId(Map<String, Object> request) {
        Object raw = request.get("buildingId");
        if (raw == null) {
            throw new RuntimeException("buildingId is required");
        }
        return ((Number) raw).longValue();
    }

    @PostMapping("/attendance/start")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startAttendance(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Long buildingId = requireBuildingId(request);
        Map<String, Object> session = attendanceService.startSession(
                userPrincipal.getId(), buildingId, resolveWardenBuildingIds(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Attendance session started", session));
    }

    @PostMapping("/attendance/stop")
    public ResponseEntity<ApiResponse<Void>> stopAttendance(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Long buildingId = requireBuildingId(request);
        attendanceService.stopSession(buildingId, resolveWardenBuildingIds(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Attendance session closed", null));
    }

    @GetMapping("/attendance/session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActiveSession(
            @RequestParam Long buildingId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> session = attendanceService.getActiveSession(
                buildingId, resolveWardenBuildingIds(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    @GetMapping("/attendance/session/{sessionId}/records")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSessionRecords(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Map<String, Object>> records = attendanceService.getSessionRecords(
                sessionId, assignedHostelsOf(userPrincipal.getId()));
        return ResponseEntity.ok(ApiResponse.success(records));
    }

    @GetMapping("/attendance/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAttendanceConfig(
            @RequestParam Long buildingId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> config = attendanceService.getAttendanceConfig(
                buildingId, resolveWardenBuildingIds(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/attendance/config")
    public ResponseEntity<ApiResponse<Void>> updateAttendanceConfig(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Long buildingId = requireBuildingId(request);
        attendanceService.updateAttendanceConfig(
                buildingId, resolveWardenBuildingIds(userPrincipal),
                asString(request.get("wifiAllowedSubnets"), ""),
                asString(request.get("hostelLatitude"), "0"),
                asString(request.get("hostelLongitude"), "0"),
                asString(request.get("hostelRadius"), "50"));
        return ResponseEntity.ok(ApiResponse.success("Attendance config updated", null));
    }

    // Request bodies here carry a mix of a numeric buildingId and plain-text config
    // fields in one Map<String, Object> -- this normalizes whichever JSON type a field
    // arrived as (String from a text input, Number if a client ever sends one) into the
    // String every config value is stored as.
    private String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = value.toString();
        return s.isEmpty() ? defaultValue : s;
    }

    @GetMapping("/attendance/report")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAttendanceReport(
            @RequestParam String from, @RequestParam String to,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<String> hostels = assignedHostelsOf(userPrincipal.getId());
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        var records = attendanceRecordRepository.findByDateBetweenOrderByDateDescMarkedAtDesc(fromDate, toDate)
                .stream().filter(r -> hostels.contains(r.getStudent().getHostel()))
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
    // building/floor/room/allocation mutation below must be confined to exactly the
    // buildings assigned to the calling warden (warden_buildings, via assignedHostelsOf) --
    // null means unrestricted (ADMIN manages every hostel; ADMIN is never scoped through
    // warden_buildings, it simply has no Warden row to look one up for).
    private List<String> resolveWardenHostels(UserPrincipal userPrincipal) {
        if (userPrincipal.getRole() != Role.WARDEN) {
            return null;
        }
        return assignedHostelsOf(userPrincipal.getId());
    }

    @GetMapping("/rooms/buildings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuildings(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(roomService.getBuildings(resolveWardenHostels(userPrincipal))));
    }

    @PutMapping("/rooms/buildings/{id}/rename")
    public ResponseEntity<ApiResponse<Map<String, Object>>> renameBuilding(
            @PathVariable Long id, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> building = roomService.renameBuilding(id, request.get("name"), resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Building renamed", building));
    }

    @PutMapping("/rooms/buildings/{id}/type")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBuildingType(
            @PathVariable Long id, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> building = roomService.updateBuildingType(id, request.get("type"), resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Building type updated", building));
    }

    @PutMapping("/rooms/buildings/{id}/gender")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBuildingGender(
            @PathVariable Long id, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> building = roomService.updateBuildingGender(id, request.get("gender"), resolveWardenHostels(userPrincipal));
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
        roomService.removeBuilding(id, resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Building removed", null));
    }

    @GetMapping("/rooms/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig(
            @RequestParam Long buildingId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(
                roomService.getConfig(buildingId, resolveWardenBuildingIds(userPrincipal))));
    }

    @PutMapping("/rooms/config")
    public ResponseEntity<ApiResponse<Void>> updateConfig(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Long buildingId = requireBuildingId(request);
        List<Long> wardenBuildingIds = resolveWardenBuildingIds(userPrincipal);
        roomService.updateConfig(
                buildingId, wardenBuildingIds,
                ((Number) request.getOrDefault("maxRoomsPerFloor", 10)).intValue(),
                ((Number) request.getOrDefault("maxMembersPerRoom", 6)).intValue());
        if (request.containsKey("wifiAllowedSubnets")) {
            roomService.updateWifiSubnets(buildingId, wardenBuildingIds, (String) request.get("wifiAllowedSubnets"));
        }
        return ResponseEntity.ok(ApiResponse.success("Config updated", null));
    }

    @PutMapping("/rooms/{roomId}/max-members")
    public ResponseEntity<ApiResponse<Void>> updateRoomMaxMembers(
            @PathVariable Long roomId, @RequestBody Map<String, Integer> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.updateRoomMaxMembers(roomId, request.get("maxMembers"), resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room capacity updated", null));
    }

    @PostMapping("/rooms/buildings/{buildingId}/floors")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addFloor(
            @PathVariable Long buildingId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> floor = roomService.addFloor(buildingId, resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Floor added", floor));
    }

    @DeleteMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}")
    public ResponseEntity<ApiResponse<Void>> removeFloor(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.removeFloor(buildingId, floorNumber, resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Floor removed", null));
    }

    @PostMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}/rooms")
    public ResponseEntity<ApiResponse<Void>> addRoom(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.addRoomToFloor(buildingId, floorNumber, resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room added", null));
    }

    @DeleteMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}/rooms/last")
    public ResponseEntity<ApiResponse<Void>> removeLastRoom(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.removeLastRoomFromFloor(buildingId, floorNumber, resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room removed", null));
    }

    @GetMapping("/rooms/allocations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllAllocations(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        // Shared with Admin's Room Management page (see SecurityConfig: /warden/rooms/**
        // permits both WARDEN and ADMIN) — admins have no Warden row to scope by, and are
        // meant to see allocations across every hostel, so only narrow the result for wardens.
        List<Map<String, Object>> allocations = userPrincipal.getRole() == Role.WARDEN
                ? roomService.getAllocationsByHostels(assignedHostelsOf(userPrincipal.getId()))
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
                resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Student allocated", allocation));
    }

    @DeleteMapping("/rooms/allocations/{email}")
    public ResponseEntity<ApiResponse<Void>> removeAllocation(
            @PathVariable String email, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.removeAllocation(email, resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Student removed from room", null));
    }

    @PutMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}/department")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setFloorDepartment(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> result = roomService.setFloorDepartment(
                buildingId, floorNumber, request.get("department"), resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Floor department updated", result));
    }

    @PutMapping("/rooms/{roomId}/department")
    public ResponseEntity<ApiResponse<Void>> setRoomDepartmentOverride(
            @PathVariable Long roomId, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.setRoomDepartmentOverride(roomId, request.get("department"), resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room department override set", null));
    }

    @DeleteMapping("/rooms/{roomId}/department")
    public ResponseEntity<ApiResponse<Void>> removeRoomDepartmentOverride(
            @PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.removeRoomDepartmentOverride(roomId, resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room department override removed", null));
    }

    @PutMapping("/rooms/{roomId}/number")
    public ResponseEntity<ApiResponse<Void>> updateRoomNumber(
            @PathVariable Long roomId, @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.updateRoomNumber(roomId, request.get("roomNumber"), resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Room number updated", null));
    }

    @PostMapping("/rooms/buildings/{buildingId}/auto-allocate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> autoAllocate(
            @PathVariable Long buildingId,
            @RequestParam(required = false) Integer floorNumber,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> result = roomService.bulkAllocate(
                buildingId, floorNumber, userPrincipal.getEmail(), userPrincipal.getRole().name(),
                resolveWardenHostels(userPrincipal));
        return ResponseEntity.ok(ApiResponse.success("Bulk allocation completed", result));
    }

    @GetMapping("/students")
    public ResponseEntity<ApiResponse<List<StudentSummaryResponse>>> getStudents(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        // Shared with Admin's Room Management page (see SecurityConfig: /warden/students
        // permits both WARDEN and ADMIN) — admins have no Warden row to scope by, and are
        // meant to see students across every hostel, so only narrow the result for wardens.
        List<Student> students = userPrincipal.getRole() == Role.WARDEN
                ? studentRepository.findByHostelIn(assignedHostelsOf(userPrincipal.getId()))
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
        List<String> hostels = assignedHostelsOf(userPrincipal.getId());
        List<Map<String, Object>> complaints = status != null && !status.isEmpty()
                ? complaintService.getComplaintsByHostelsAndStatus(hostels, status)
                : complaintService.getComplaintsByHostels(hostels);
        return ResponseEntity.ok(ApiResponse.success(complaints));
    }

    @GetMapping("/complaints/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getComplaintStats(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(
                complaintService.getStatsByHostels(assignedHostelsOf(userPrincipal.getId()))));
    }

    @PutMapping("/complaints/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateComplaint(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, String> request) {
        String status = request.get("status");
        String wardenResponse = request.get("wardenResponse");
        Map<String, Object> complaint = complaintService.updateComplaintStatus(
                id, status, wardenResponse, userPrincipal.getId(), assignedHostelsOf(userPrincipal.getId()));
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

