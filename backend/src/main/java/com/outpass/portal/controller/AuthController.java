package com.outpass.portal.controller;

import com.outpass.portal.dto.request.LoginRequest;
import com.outpass.portal.dto.request.RefreshTokenRequest;
import com.outpass.portal.dto.request.StudentRegistrationRequest;
import com.outpass.portal.dto.response.ApiResponse;
import com.outpass.portal.dto.response.AuthResponse;
import com.outpass.portal.dto.response.StudentSummaryResponse;
import com.outpass.portal.model.entity.PasswordResetToken;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.entity.Warden;
import com.outpass.portal.model.entity.SecurityGuard;
import com.outpass.portal.model.enums.Role;
import com.outpass.portal.repository.PasswordResetTokenRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.service.AuthService;
import com.outpass.portal.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RoomService roomService;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final StudentRepository studentRepository;
    private final WardenRepository wardenRepository;
    private final SecurityGuardRepository securityGuardRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/buildings")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getPublicBuildings() {
        return ResponseEntity.ok(ApiResponse.success(roomService.getBuildingsPublic()));
    }

    @PostMapping("/student/register")
    public ResponseEntity<ApiResponse<StudentSummaryResponse>> registerStudent(
            @Valid @RequestBody StudentRegistrationRequest request) {

        Student student = Student.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(request.getPassword())
                .rollNo(request.getRollNo())
                .department(request.getDepartment())
                .hostel(request.getHostel())
                .roomNumber(request.getRoomNumber())
                .contactNumber(request.getContactNumber())
                .parentNumber(request.getParentNumber())
                .profilePicture(request.getProfilePicture())
                .gender(request.getGender() != null ? request.getGender() : "BOY")
                .build();

        Student registered = authService.registerStudent(student);
        return ResponseEntity.ok(ApiResponse.success("Student registered successfully", StudentSummaryResponse.from(registered)));
    }

    @PostMapping("/student/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginStudent(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResponse response = authService.login(request.getEmail(), request.getPassword(), Role.STUDENT);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(response.accessToken)
                .refreshToken(response.refreshToken)
                .email(response.email)
                .role(response.role)
                .tokenType("Bearer")
                .build();

        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @PostMapping("/warden/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWarden(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResponse response = authService.login(request.getEmail(), request.getPassword(), Role.WARDEN);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(response.accessToken)
                .refreshToken(response.refreshToken)
                .email(response.email)
                .role(response.role)
                .tokenType("Bearer")
                .build();

        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @PostMapping("/security/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginSecurityGuard(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResponse response = authService.login(request.getEmail(), request.getPassword(), Role.SECURITY_GUARD);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(response.accessToken)
                .refreshToken(response.refreshToken)
                .email(response.email)
                .role(response.role)
                .tokenType("Bearer")
                .build();

        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthService.AuthResponse response = authService.refreshToken(request.getRefreshToken());

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(response.accessToken)
                .refreshToken(response.refreshToken)
                .email(response.email)
                .role(response.role)
                .tokenType("Bearer")
                .build();

        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        authService.logout(userPrincipal.getId(), userPrincipal.getRole().name());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @Transactional
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String role = request.getOrDefault("role", "STUDENT");

        boolean exists = switch (role) {
            case "WARDEN" -> wardenRepository.findByEmail(email).isPresent();
            case "SECURITY_GUARD" -> securityGuardRepository.findByEmail(email).isPresent();
            default -> studentRepository.findByEmail(email).isPresent();
        };

        if (!exists) {
            throw new RuntimeException("No account found with this email");
        }

        resetTokenRepository.deleteByEmail(email);
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .email(email)
                .userType(role)
                .expiresAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusMinutes(15))
                .build();
        resetTokenRepository.save(resetToken);

        // In production, send token via email. For now, include in response for demo only.
        return ResponseEntity.ok(ApiResponse.success(
                "Reset token generated. In production this would be sent to your email.",
                Map.of("token", token, "demo", true)));
    }

    @Transactional
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");

        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (resetToken.isExpired()) {
            resetTokenRepository.delete(resetToken);
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }

        String encoded = passwordEncoder.encode(newPassword);
        switch (resetToken.getUserType()) {
            case "WARDEN" -> wardenRepository.findByEmail(resetToken.getEmail())
                    .ifPresent(w -> { w.setPasswordHash(encoded); wardenRepository.save(w); });
            case "SECURITY_GUARD" -> securityGuardRepository.findByEmail(resetToken.getEmail())
                    .ifPresent(s -> { s.setPasswordHash(encoded); securityGuardRepository.save(s); });
            default -> studentRepository.findByEmail(resetToken.getEmail())
                    .ifPresent(s -> { s.setPasswordHash(encoded); studentRepository.save(s); });
        }

        resetTokenRepository.delete(resetToken);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }
}

