package com.outpass.portal.controller;

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
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.entity.Warden;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.service.AttendanceService;
import com.outpass.portal.service.OutpassService;
import com.outpass.portal.service.RoomService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/warden")
@RequiredArgsConstructor
public class WardenController {

    private final OutpassService outpassService;
    private final WardenRepository wardenRepository;
    private final AttendanceService attendanceService;
    private final RoomService roomService;
    private final StudentRepository studentRepository;

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
        StudentOutpassStatsResponse stats = outpassService.getStudentStatistics(studentId);
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
            @PathVariable Long sessionId) {
        List<Map<String, Object>> records = attendanceService.getSessionRecords(sessionId);
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

    // ==================== Rooms ====================

    @GetMapping("/rooms/buildings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuildings() {
        return ResponseEntity.ok(ApiResponse.success(roomService.getBuildings()));
    }

    @PutMapping("/rooms/buildings/{id}/rename")
    public ResponseEntity<ApiResponse<Map<String, Object>>> renameBuilding(
            @PathVariable Long id, @RequestBody Map<String, String> request) {
        Map<String, Object> building = roomService.renameBuilding(id, request.get("name"));
        return ResponseEntity.ok(ApiResponse.success("Building renamed", building));
    }

    @PutMapping("/rooms/buildings/{id}/type")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBuildingType(
            @PathVariable Long id, @RequestBody Map<String, String> request) {
        Map<String, Object> building = roomService.updateBuildingType(id, request.get("type"));
        return ResponseEntity.ok(ApiResponse.success("Building type updated", building));
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
            @PathVariable Long roomId, @RequestBody Map<String, Integer> request) {
        roomService.updateRoomMaxMembers(roomId, request.get("maxMembers"));
        return ResponseEntity.ok(ApiResponse.success("Room capacity updated", null));
    }

    @PostMapping("/rooms/buildings/{buildingId}/floors")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addFloor(@PathVariable Long buildingId) {
        Map<String, Object> floor = roomService.addFloor(buildingId);
        return ResponseEntity.ok(ApiResponse.success("Floor added", floor));
    }

    @DeleteMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}")
    public ResponseEntity<ApiResponse<Void>> removeFloor(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber) {
        roomService.removeFloor(buildingId, floorNumber);
        return ResponseEntity.ok(ApiResponse.success("Floor removed", null));
    }

    @PostMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}/rooms")
    public ResponseEntity<ApiResponse<Void>> addRoom(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber) {
        roomService.addRoomToFloor(buildingId, floorNumber);
        return ResponseEntity.ok(ApiResponse.success("Room added", null));
    }

    @DeleteMapping("/rooms/buildings/{buildingId}/floors/{floorNumber}/rooms/last")
    public ResponseEntity<ApiResponse<Void>> removeLastRoom(
            @PathVariable Long buildingId, @PathVariable Integer floorNumber) {
        roomService.removeLastRoomFromFloor(buildingId, floorNumber);
        return ResponseEntity.ok(ApiResponse.success("Room removed", null));
    }

    @GetMapping("/rooms/allocations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllAllocations() {
        return ResponseEntity.ok(ApiResponse.success(roomService.getAllAllocations()));
    }

    @PostMapping("/rooms/{roomId}/allocate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> allocateStudent(
            @PathVariable Long roomId, @RequestBody Map<String, String> request) {
        Map<String, Object> allocation = roomService.allocateStudent(
                roomId,
                request.get("name"),
                request.get("rollNo"),
                request.get("department"),
                request.get("email"));
        return ResponseEntity.ok(ApiResponse.success("Student allocated", allocation));
    }

    @DeleteMapping("/rooms/allocations/{email}")
    public ResponseEntity<ApiResponse<Void>> removeAllocation(@PathVariable String email) {
        roomService.removeAllocation(email);
        return ResponseEntity.ok(ApiResponse.success("Student removed from room", null));
    }

    @GetMapping("/students")
    public ResponseEntity<ApiResponse<List<Student>>> getStudents(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Student> students = studentRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(students));
    }
}

