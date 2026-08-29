package com.outpass.portal.controller;

import com.outpass.portal.dto.request.LoginRequest;
import com.outpass.portal.dto.request.RefreshTokenRequest;
import com.outpass.portal.dto.request.StudentRegistrationRequest;
import com.outpass.portal.dto.response.ApiResponse;
import com.outpass.portal.dto.response.AuthResponse;
import com.outpass.portal.dto.response.StudentSummaryResponse;
import com.outpass.portal.exception.RateLimitExceededException;
import com.outpass.portal.model.entity.Admin;
import com.outpass.portal.model.entity.PasswordResetToken;
import com.outpass.portal.model.entity.SecurityGuard;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.entity.Warden;
import com.outpass.portal.model.enums.Role;
import com.outpass.portal.repository.AdminRepository;
import com.outpass.portal.repository.PasswordResetTokenRepository;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.service.AuthService;
import com.outpass.portal.service.EmailService;
import com.outpass.portal.service.RefreshTokenService;
import com.outpass.portal.service.RoomService;
import com.outpass.portal.util.EmailUtils;
import com.outpass.portal.util.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final RoomService roomService;
    private final EmailService emailService;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final StudentRepository studentRepository;
    private final WardenRepository wardenRepository;
    private final SecurityGuardRepository securityGuardRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiterService rateLimiterService;

    @Value("${rate.limit.auth.forgot-password:3}")
    private int forgotPasswordLimit;

    @Value("${rate.limit.auth.resend-verification:3}")
    private int resendVerificationLimit;

    @Value("${rate.limit.auth.register:5}")
    private int registerLimit;

    @Value("${rate.limit.auth.verify-email:20}")
    private int verifyEmailLimit;

    @Value("${rate.limit.auth.window-seconds:3600}")
    private long authRateLimitWindowSeconds;

    private static final String GENERIC_RESET_MESSAGE =
            "If an account exists for that email, a password reset link has been sent.";

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void enforceRateLimit(String key, int maxRequests) {
        if (!rateLimiterService.isAllowed(key, maxRequests, authRateLimitWindowSeconds)) {
            throw new RateLimitExceededException(
                    "Too many requests. Please try again later.");
        }
    }

    @GetMapping("/buildings")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getPublicBuildings() {
        return ResponseEntity.ok(ApiResponse.success(roomService.getBuildingsPublic()));
    }

    // Hostels available to a student registering in the given academic year (Admin-configured
    // via /admin/year-hostels). The frontend uses this instead of /auth/buildings once a year
    // is selected, so a hostel not allowed for that year is never even shown -- the backend
    // still independently re-validates the choice in RoomService.allocateForRegistration.
    @GetMapping("/hostels-by-year")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getHostelsByYear(
            @RequestParam Integer year) {
        return ResponseEntity.ok(ApiResponse.success(roomService.getBuildingsPublicForYear(year)));
    }

    @PostMapping("/student/register")
    public ResponseEntity<ApiResponse<StudentSummaryResponse>> registerStudent(
            @Valid @RequestBody StudentRegistrationRequest request, HttpServletRequest httpRequest) {

        enforceRateLimit("authrl:register:" + clientIp(httpRequest), registerLimit);

        Student student = Student.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(request.getPassword())
                .rollNo(request.getRollNo())
                .department(request.getDepartment())
                .year(request.getYear())
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

    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<AuthResponse>> loginAdmin(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResponse response = authService.login(request.getEmail(), request.getPassword(), Role.ADMIN);

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

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token, HttpServletRequest httpRequest) {
        enforceRateLimit("authrl:verify-email:" + clientIp(httpRequest), verifyEmailLimit);
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully. You can now log in.", null));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String email = EmailUtils.normalize(request.get("email"));
        enforceRateLimit("authrl:resend-verification:" + clientIp(httpRequest) + ":" + email,
                resendVerificationLimit);
        authService.resendVerification(email);
        return ResponseEntity.ok(ApiResponse.success(
                "If an account with that email exists and isn't verified yet, a verification link has been sent.", null));
    }

    @Transactional
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String email = EmailUtils.normalize(request.get("email"));
        String role = request.getOrDefault("role", "STUDENT");

        enforceRateLimit("authrl:forgot-password:" + clientIp(httpRequest) + ":" + email, forgotPasswordLimit);

        String name = switch (role) {
            case "WARDEN" -> wardenRepository.findByEmailIgnoreCase(email).map(Warden::getName).orElse(null);
            case "SECURITY_GUARD" -> securityGuardRepository.findByEmailIgnoreCase(email).map(SecurityGuard::getName).orElse(null);
            case "ADMIN" -> adminRepository.findByEmailIgnoreCase(email).map(Admin::getName).orElse(null);
            default -> studentRepository.findByEmailIgnoreCase(email).map(Student::getName).orElse(null);
        };

        // Deliberately do not reveal whether the account exists: only issue a token
        // and attempt delivery when a match is found, but always return the same
        // response either way.
        if (name != null) {
            resetTokenRepository.deleteByEmail(email);
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .email(email)
                    .userType(role)
                    .expiresAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusMinutes(15))
                    .build();
            resetTokenRepository.save(resetToken);

            try {
                emailService.sendPasswordResetEmail(email, name, token);
            } catch (Exception e) {
                log.warn("Password reset email failed for {}: {}", email, e.getMessage());
            }
        } else {
            log.info("Password reset requested for unregistered email/role combination");
        }

        return ResponseEntity.ok(ApiResponse.success(GENERIC_RESET_MESSAGE, null));
    }

    @Transactional
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String newPassword = request.get("newPassword");

        // Registration and admin-created accounts both enforce a 6-character minimum via
        // DTO validation; this endpoint takes a raw request body, so the same rule must be
        // enforced explicitly here to avoid a weaker password ending up on the account.
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters");
        }

        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (resetToken.isExpired()) {
            resetTokenRepository.delete(resetToken);
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }

        String encoded = passwordEncoder.encode(newPassword);
        switch (resetToken.getUserType()) {
            case "WARDEN" -> wardenRepository.findByEmailIgnoreCase(resetToken.getEmail())
                    .ifPresent(w -> {
                        w.setPasswordHash(encoded);
                        wardenRepository.save(w);
                        refreshTokenService.deleteByUserIdAndUserType(w.getId(), "WARDEN");
                    });
            case "SECURITY_GUARD" -> securityGuardRepository.findByEmailIgnoreCase(resetToken.getEmail())
                    .ifPresent(s -> {
                        s.setPasswordHash(encoded);
                        securityGuardRepository.save(s);
                        refreshTokenService.deleteByUserIdAndUserType(s.getId(), "SECURITY_GUARD");
                    });
            case "ADMIN" -> adminRepository.findByEmailIgnoreCase(resetToken.getEmail())
                    .ifPresent(a -> {
                        a.setPasswordHash(encoded);
                        adminRepository.save(a);
                        refreshTokenService.deleteByUserIdAndUserType(a.getId(), "ADMIN");
                    });
            default -> studentRepository.findByEmailIgnoreCase(resetToken.getEmail())
                    .ifPresent(s -> {
                        s.setPasswordHash(encoded);
                        studentRepository.save(s);
                        refreshTokenService.deleteByUserIdAndUserType(s.getId(), "STUDENT");
                    });
        }

        resetTokenRepository.delete(resetToken);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }
}

