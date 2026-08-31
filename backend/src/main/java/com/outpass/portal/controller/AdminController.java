package com.outpass.portal.controller;

import com.outpass.portal.dto.request.AdminCreateSecurityGuardRequest;
import com.outpass.portal.dto.request.AdminCreateWardenRequest;
import com.outpass.portal.dto.response.ApiResponse;
import com.outpass.portal.dto.response.SecurityGuardSummaryResponse;
import com.outpass.portal.dto.response.WardenSummaryResponse;
import com.outpass.portal.service.AdminService;
import com.outpass.portal.service.HostelEligibilityService;
import com.outpass.portal.service.WardenBuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Admin-only: create/manage warden and security-guard accounts, and configure year-based
// hostel eligibility. Room management for ADMIN is not duplicated here — it reuses the
// existing /warden/rooms/** endpoints (see SecurityConfig), which ADMIN is also authorized
// to call.
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final HostelEligibilityService hostelEligibilityService;
    private final WardenBuildingService wardenBuildingService;

    @PostMapping("/wardens")
    public ResponseEntity<ApiResponse<WardenSummaryResponse>> createWarden(
            @Valid @RequestBody AdminCreateWardenRequest request) {
        WardenSummaryResponse warden = adminService.createWarden(request);
        return ResponseEntity.ok(ApiResponse.success("Warden created", warden));
    }

    @GetMapping("/wardens")
    public ResponseEntity<ApiResponse<List<WardenSummaryResponse>>> listWardens() {
        return ResponseEntity.ok(ApiResponse.success(adminService.listWardens()));
    }

    @PutMapping("/wardens/{id}/status")
    public ResponseEntity<ApiResponse<WardenSummaryResponse>> setWardenStatus(
            @PathVariable Long id, @RequestBody Map<String, Boolean> request) {
        boolean enabled = Boolean.TRUE.equals(request.get("enabled"));
        WardenSummaryResponse warden = adminService.setWardenEnabled(id, enabled);
        return ResponseEntity.ok(ApiResponse.success(
                enabled ? "Warden enabled" : "Warden disabled", warden));
    }

    // ==================== Warden <-> Buildings ====================
    // Admin-only by design (reaches only via "/admin/**", already ADMIN-only in
    // SecurityConfig). Lets an admin assign a warden to more than one building while
    // preserving Warden.hostel as the single "primary" hostel every existing warden-scoped
    // endpoint reads (see WardenBuildingService).

    @GetMapping("/wardens/{id}/buildings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getWardenBuildings(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(wardenBuildingService.getBuildingsForWarden(id)));
    }

    @PostMapping("/wardens/{id}/buildings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> assignWardenBuilding(
            @PathVariable Long id, @RequestBody Map<String, Object> request) {
        Long buildingId = ((Number) request.get("buildingId")).longValue();
        List<Map<String, Object>> buildings = wardenBuildingService.assignBuilding(id, buildingId);
        return ResponseEntity.ok(ApiResponse.success("Building assigned", buildings));
    }

    @DeleteMapping("/wardens/{id}/buildings/{buildingId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> unassignWardenBuilding(
            @PathVariable Long id, @PathVariable Long buildingId) {
        List<Map<String, Object>> buildings = wardenBuildingService.unassignBuilding(id, buildingId);
        return ResponseEntity.ok(ApiResponse.success("Building removed from warden", buildings));
    }

    @PostMapping("/security-guards")
    public ResponseEntity<ApiResponse<SecurityGuardSummaryResponse>> createSecurityGuard(
            @Valid @RequestBody AdminCreateSecurityGuardRequest request) {
        SecurityGuardSummaryResponse guard = adminService.createSecurityGuard(request);
        return ResponseEntity.ok(ApiResponse.success("Security guard created", guard));
    }

    @GetMapping("/security-guards")
    public ResponseEntity<ApiResponse<List<SecurityGuardSummaryResponse>>> listSecurityGuards() {
        return ResponseEntity.ok(ApiResponse.success(adminService.listSecurityGuards()));
    }

    @PutMapping("/security-guards/{id}/status")
    public ResponseEntity<ApiResponse<SecurityGuardSummaryResponse>> setSecurityGuardStatus(
            @PathVariable Long id, @RequestBody Map<String, Boolean> request) {
        boolean enabled = Boolean.TRUE.equals(request.get("enabled"));
        SecurityGuardSummaryResponse guard = adminService.setSecurityGuardEnabled(id, enabled);
        return ResponseEntity.ok(ApiResponse.success(
                enabled ? "Security guard enabled" : "Security guard disabled", guard));
    }

    // ==================== Year -> Hostel eligibility ====================
    // Admin-only by design (see SecurityConfig: only "/admin/**" reaches these, not
    // "/warden/rooms/**"). Wardens/security guards get no access to this configuration.

    @GetMapping("/year-hostels")
    public ResponseEntity<ApiResponse<Map<Integer, List<Map<String, Object>>>>> getYearHostelConfig() {
        return ResponseEntity.ok(ApiResponse.success(hostelEligibilityService.getConfiguration()));
    }

    @PostMapping("/year-hostels")
    public ResponseEntity<ApiResponse<Void>> addYearHostel(@RequestBody Map<String, Object> request) {
        Integer year = ((Number) request.get("year")).intValue();
        Long buildingId = ((Number) request.get("buildingId")).longValue();
        hostelEligibilityService.addMapping(year, buildingId);
        return ResponseEntity.ok(ApiResponse.success("Hostel added for year " + year, null));
    }

    @DeleteMapping("/year-hostels/{year}/{buildingId}")
    public ResponseEntity<ApiResponse<Void>> removeYearHostel(
            @PathVariable Integer year, @PathVariable Long buildingId) {
        hostelEligibilityService.removeMapping(year, buildingId);
        return ResponseEntity.ok(ApiResponse.success("Hostel removed for year " + year, null));
    }
}
