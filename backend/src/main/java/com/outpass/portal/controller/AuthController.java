package com.outpass.portal.controller;

import com.outpass.portal.dto.request.LoginRequest;
import com.outpass.portal.dto.request.RefreshTokenRequest;
import com.outpass.portal.dto.request.StudentRegistrationRequest;
import com.outpass.portal.dto.response.ApiResponse;
import com.outpass.portal.dto.response.AuthResponse;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.Role;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.service.AuthService;
import com.outpass.portal.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RoomService roomService;

    @GetMapping("/buildings")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getPublicBuildings() {
        return ResponseEntity.ok(ApiResponse.success(roomService.getBuildingsPublic()));
    }

    @PostMapping("/student/register")
    public ResponseEntity<ApiResponse<Student>> registerStudent(
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
                .build();

        Student registered = authService.registerStudent(student);
        return ResponseEntity.ok(ApiResponse.success("Student registered successfully", registered));
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
}

