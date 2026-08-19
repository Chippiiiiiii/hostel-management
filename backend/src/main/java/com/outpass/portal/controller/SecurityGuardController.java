package com.outpass.portal.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.outpass.portal.dto.response.ApiResponse;
import com.outpass.portal.dto.response.OutpassResponse;
import com.outpass.portal.model.entity.SecurityGuard;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.service.OutpassService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/security")
@RequiredArgsConstructor
public class SecurityGuardController {

    private final OutpassService outpassService;
    private final SecurityGuardRepository securityGuardRepository;

    @GetMapping("/outpass/active")
    public ResponseEntity<ApiResponse<List<OutpassResponse>>> getActiveOutpasses(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        SecurityGuard securityGuard = securityGuardRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Security guard not found"));
        
        List<OutpassResponse> active = outpassService.getActiveOutpassesByHostel(securityGuard.getHostel());
        return ResponseEntity.ok(ApiResponse.success(active));
    }

    @GetMapping("/outpass/{id}")
    public ResponseEntity<ApiResponse<OutpassResponse>> getOutpass(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        SecurityGuard securityGuard = securityGuardRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Security guard not found"));
        
        OutpassResponse outpass = outpassService.getOutpassByIdAndHostel(id, securityGuard.getHostel());
        return ResponseEntity.ok(ApiResponse.success(outpass));
    }

    @GetMapping("/outpass/today")
    public ResponseEntity<ApiResponse<List<OutpassResponse>>> getTodayOutpasses(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        SecurityGuard securityGuard = securityGuardRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Security guard not found"));
        
        List<OutpassResponse> today = outpassService.getTodayOutpassesByHostel(securityGuard.getHostel());
        return ResponseEntity.ok(ApiResponse.success(today));
    }

    @GetMapping("/outpass/departed")
    public ResponseEntity<ApiResponse<List<OutpassResponse>>> getDepartedOutpasses(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        SecurityGuard securityGuard = securityGuardRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Security guard not found"));
        
        List<OutpassResponse> departed = outpassService.getDepartedOutpassesByHostel(securityGuard.getHostel());
        return ResponseEntity.ok(ApiResponse.success(departed));
    }

    @PutMapping("/outpass/{id}/mark-departure")
    public ResponseEntity<ApiResponse<OutpassResponse>> markDeparture(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        SecurityGuard securityGuard = securityGuardRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Security guard not found"));
        
        OutpassResponse outpass = outpassService.markDeparture(id, securityGuard.getId(), securityGuard.getHostel());
        return ResponseEntity.ok(ApiResponse.success("Student departure verified successfully", outpass));
    }

    @PutMapping("/outpass/{id}/mark-return")
    public ResponseEntity<ApiResponse<OutpassResponse>> markReturn(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        SecurityGuard securityGuard = securityGuardRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("Security guard not found"));
        
        OutpassResponse outpass = outpassService.markReturn(id, securityGuard.getId(), securityGuard.getHostel());
        return ResponseEntity.ok(ApiResponse.success("Student return verified successfully", outpass));
    }
}

