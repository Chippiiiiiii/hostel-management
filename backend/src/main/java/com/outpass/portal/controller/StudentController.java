package com.outpass.portal.controller;

import com.outpass.portal.dto.request.OutpassRequest;
import com.outpass.portal.dto.request.StudentProfileUpdateRequest;
import com.outpass.portal.dto.response.ApiResponse;
import com.outpass.portal.dto.response.OutpassResponse;
import com.outpass.portal.dto.response.StudentProfileResponse;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.model.entity.Announcement;
import com.outpass.portal.repository.AnnouncementRepository;
import com.outpass.portal.service.AttendanceService;
import com.outpass.portal.service.ComplaintService;
import com.outpass.portal.service.OutpassService;
import com.outpass.portal.service.RoomService;
import com.outpass.portal.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final OutpassService outpassService;
    private final AttendanceService attendanceService;
    private final RoomService roomService;
    private final ComplaintService complaintService;
    private final AnnouncementRepository announcementRepository;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> getProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        StudentProfileResponse profile = studentService.getProfile(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody StudentProfileUpdateRequest updateRequest) {
        StudentProfileResponse updated = studentService.updateProfile(userPrincipal.getId(), updateRequest);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updated));
    }

    @PostMapping("/outpass")
    public ResponseEntity<ApiResponse<OutpassResponse>> createOutpass(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody OutpassRequest request) {
        OutpassResponse outpass = outpassService.createOutpass(userPrincipal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Outpass created successfully", outpass));
    }

    @GetMapping("/outpass/history")
    public ResponseEntity<ApiResponse<List<OutpassResponse>>> getOutpassHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<OutpassResponse> history = outpassService.getStudentOutpasses(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/outpass/{id}")
    public ResponseEntity<ApiResponse<OutpassResponse>> getOutpass(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        OutpassResponse outpass = outpassService.getOutpassById(id, userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(outpass));
    }

    @DeleteMapping("/outpass/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelOutpass(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        outpassService.cancelOutpass(id, userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success("Outpass cancelled successfully", null));
    }

    // ==================== Attendance ====================

    // Each of these three previously took no identity at all -- see backend/AGENTS.md.
    // Now they resolve the calling student's OWN hostel (via Student.hostel ->
    // Building.name, the same lookup RoomService.allocateForRegistration already uses)
    // inside AttendanceService, rather than reading whichever building's config/session
    // happened to be the sole global one.
    @GetMapping("/attendance/location")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHostelLocation(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.getHostelLocation(userPrincipal.getId())));
    }

    @GetMapping("/attendance/verify-wifi")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyWifi(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = getClientIp(request);
        Map<String, Object> result = attendanceService.verifyWifi(userPrincipal.getId(), clientIp);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/attendance/session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActiveSession(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> session = attendanceService.getActiveSessionForStudent(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    @PostMapping("/attendance/mark")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAttendance(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, Object> request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String method = (String) request.get("method");
        Double latitude = request.get("latitude") != null ? ((Number) request.get("latitude")).doubleValue() : null;
        Double longitude = request.get("longitude") != null ? ((Number) request.get("longitude")).doubleValue() : null;
        Integer distance = request.get("distance") != null ? ((Number) request.get("distance")).intValue() : null;

        Map<String, Object> record = attendanceService.markAttendance(
                userPrincipal.getId(), method, latitude, longitude, distance, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Attendance marked successfully", record));
    }

    @GetMapping("/attendance/today")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTodayStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> status = attendanceService.getTodayStatus(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/attendance/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAttendanceHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Map<String, Object>> history = attendanceService.getHistory(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/attendance/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAttendanceStats(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Map<String, Object> stats = attendanceService.getStats(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // ==================== Room ====================

    @GetMapping("/rooms/buildings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBuildings() {
        return ResponseEntity.ok(ApiResponse.success(roomService.getBuildings(null)));
    }

    @GetMapping("/rooms/allocation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyAllocation(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        StudentProfileResponse profile = studentService.getProfile(userPrincipal.getId());
        Map<String, Object> allocation = roomService.getStudentAllocation(profile.getEmail());
        return ResponseEntity.ok(ApiResponse.success(allocation));
    }

    @PostMapping("/rooms/allocate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> allocateRoom(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, Object> request) {
        StudentProfileResponse profile = studentService.getProfile(userPrincipal.getId());
        Long roomId = ((Number) request.get("roomId")).longValue();

        // Atomic: the "does this student already have a room" check and the allocation
        // write happen inside one locked transaction (see RoomService.allocateStudentSelfService),
        // so a concurrent warden/admin assignment can never be silently overwritten by this
        // call — whichever wins the race, a staff-made assignment always wins over this one.
        Map<String, Object> allocation = roomService.allocateStudentSelfService(
                roomId, profile.getName(), profile.getRollNo(),
                profile.getDepartment(), profile.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Room allocated successfully", allocation));
    }

    @GetMapping("/rooms/roommates")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRoommates(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        StudentProfileResponse profile = studentService.getProfile(userPrincipal.getId());
        List<Map<String, Object>> roommates = roomService.getRoommates(profile.getEmail());
        return ResponseEntity.ok(ApiResponse.success(roommates));
    }

    @GetMapping("/rooms/allocations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllAllocations(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        StudentProfileResponse profile = studentService.getProfile(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(roomService.getRoomOccupancyForStudent(profile.getEmail())));
    }

    // ==================== Complaints ====================

    @PostMapping("/complaints")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createComplaint(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody Map<String, Object> request) {
        String category = (String) request.get("category");
        String description = (String) request.get("description");
        String photo = (String) request.get("photo");
        Map<String, Object> complaint = complaintService.createComplaint(
                userPrincipal.getId(), category, description, photo);
        return ResponseEntity.ok(ApiResponse.success("Complaint submitted successfully", complaint));
    }

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyComplaints(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Map<String, Object>> complaints = complaintService.getStudentComplaints(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(complaints));
    }

    // ==================== Announcements ====================

    @GetMapping("/announcements")
    public ResponseEntity<ApiResponse<List<Announcement>>> getAnnouncements() {
        return ResponseEntity.ok(ApiResponse.success(announcementRepository.findAllByOrderByCreatedAtDesc()));
    }

    // server.forward-headers-strategy=native (Tomcat's RemoteIpValve) already resolves
    // X-Forwarded-For into getRemoteAddr() using its trusted-proxy handling. Parsing the
    // raw header here instead would let a client spoof its own IP by prepending an
    // arbitrary value (e.g. to fake being on hostel WiFi), since proxies conventionally
    // append rather than replace X-Forwarded-For entries.
    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}

