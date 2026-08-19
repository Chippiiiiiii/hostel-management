package com.outpass.portal.service;

import com.outpass.portal.model.entity.RefreshToken;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.Role;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.security.JwtTokenProvider;
import com.outpass.portal.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(String email, String password, Role role) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        if (userPrincipal.getRole() != role) {
            throw new RuntimeException("Invalid credentials for this user type");
        }

        String accessToken = tokenProvider.generateAccessToken(authentication);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                userPrincipal.getId(), role.name());

        return new AuthResponse(accessToken, refreshToken.getToken(), userPrincipal.getEmail(), role.name());
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenStr) {
        return refreshTokenService.findByToken(refreshTokenStr)
                .map(refreshTokenService::verifyExpiration)
                .map(refreshToken -> {
                    UserPrincipal userPrincipal = getUserPrincipalById(
                            refreshToken.getUserId(),
                            Role.valueOf(refreshToken.getUserType()));

                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            userPrincipal, null, userPrincipal.getAuthorities());

                    String accessToken = tokenProvider.generateAccessToken(authentication);

                    return new AuthResponse(accessToken, refreshToken.getToken(),
                            userPrincipal.getEmail(), refreshToken.getUserType());
                })
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));
    }

    @Transactional
    public void logout(Long userId, String userType) {
        refreshTokenService.deleteByUserIdAndUserType(userId, userType);
    }

    @Transactional
    public Student registerStudent(Student student) {
        if (studentRepository.existsByEmail(student.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        if (studentRepository.existsByRollNo(student.getRollNo())) {
            throw new RuntimeException("Roll number already exists");
        }

        student.setPasswordHash(passwordEncoder.encode(student.getPasswordHash()));
        return studentRepository.save(student);
    }

    private UserPrincipal getUserPrincipalById(Long userId, Role role) {
        switch (role) {
            case STUDENT:
                Student student = studentRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Student not found"));
                return UserPrincipal.create(student.getId(), student.getEmail(),
                        student.getPasswordHash(), Role.STUDENT);
            default:
                throw new RuntimeException("User type not supported for refresh");
        }
    }

    public static class AuthResponse {
        public String accessToken;
        public String refreshToken;
        public String email;
        public String role;

        public AuthResponse(String accessToken, String refreshToken, String email, String role) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.email = email;
            this.role = role;
        }
    }
}

