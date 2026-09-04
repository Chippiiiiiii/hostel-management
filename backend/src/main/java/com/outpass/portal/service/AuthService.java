package com.outpass.portal.service;

import com.outpass.portal.model.entity.Admin;
import com.outpass.portal.model.entity.EmailVerificationToken;
import com.outpass.portal.model.entity.RefreshToken;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.Role;
import com.outpass.portal.model.entity.SecurityGuard;
import com.outpass.portal.model.entity.Warden;
import com.outpass.portal.repository.AdminRepository;
import com.outpass.portal.repository.EmailVerificationTokenRepository;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.security.JwtTokenProvider;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.util.EmailUtils;
import com.outpass.portal.util.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final StudentRepository studentRepository;
    private final WardenRepository wardenRepository;
    private final SecurityGuardRepository securityGuardRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final RoomService roomService;
    private final EmailUniquenessService emailUniquenessService;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public AuthResponse login(String email, String password, Role role) {
        // Keyed by normalized email only (never IP or role) so brute-forcing one account
        // can't be spread across IPs or across the student/warden/security/admin login
        // endpoints to dodge the per-account backoff -- see LoginAttemptService.
        String normalizedEmail = EmailUtils.normalize(email);
        loginAttemptService.checkNotBlocked(normalizedEmail);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (AuthenticationException ex) {
            // Covers both a wrong password and an unknown email: SecurityConfig's
            // DaoAuthenticationProvider has hideUserNotFoundExceptions=true, so both
            // surface here as the same BadCredentialsException -- recording a failure
            // never distinguishes "account doesn't exist" from "wrong password".
            loginAttemptService.recordFailure(normalizedEmail);
            throw ex;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        if (userPrincipal.getRole() != role) {
            // Correct credentials for a different role counts as an unsuccessful attempt
            // against this account too -- otherwise an attacker could brute-force a
            // warden/admin account's password via the student login endpoint without
            // ever tripping that account's backoff.
            loginAttemptService.recordFailure(normalizedEmail);
            throw new RuntimeException("Invalid credentials for this user type");
        }

        // Block unverified students (NULL means existing/seed account = allowed)
        if (role == Role.STUDENT) {
            Student student = studentRepository.findByEmailIgnoreCase(email).orElseThrow();
            if (Boolean.FALSE.equals(student.getEmailVerified())) {
                throw new RuntimeException("EMAIL_NOT_VERIFIED");
            }
        }

        loginAttemptService.recordSuccess(normalizedEmail);

        String accessToken = tokenProvider.generateAccessToken(authentication);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                userPrincipal.getId(), role.name());

        return new AuthResponse(accessToken, refreshToken.getToken(), userPrincipal.getEmail(), role.name());
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenStr) {
        RefreshToken presented = refreshTokenService.findByToken(refreshTokenStr)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));
        RefreshToken verified = refreshTokenService.verifyExpiration(presented);

        // Rotate, atomically: the presented refresh token is single-use. Consume it via a
        // single DELETE statement and check the affected-row count *before* doing anything
        // else, rather than the old findByToken()-then-delete(entity) sequence -- that left a
        // TOCTOU window where two concurrent requests presenting the identical token could
        // both observe it as present and both go on to mint a new token pair. Consuming first
        // means only the request whose DELETE actually removed the row (count == 1) may
        // proceed; a losing concurrent request (or any later replay) sees 0 and is rejected
        // identically to an unknown token, so at most one valid session is ever issued per
        // presented token.
        if (refreshTokenService.consumeToken(refreshTokenStr) != 1) {
            throw new RuntimeException("Refresh token not found");
        }

        UserPrincipal userPrincipal = getUserPrincipalById(
                verified.getUserId(), Role.valueOf(verified.getUserType()));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());

        String accessToken = tokenProvider.generateAccessToken(authentication);
        RefreshToken rotated = refreshTokenService.createRefreshToken(
                verified.getUserId(), verified.getUserType());

        return new AuthResponse(accessToken, rotated.getToken(),
                userPrincipal.getEmail(), verified.getUserType());
    }

    @Transactional
    public void logout(Long userId, String userType) {
        refreshTokenService.deleteByUserIdAndUserType(userId, userType);
    }

    @Transactional
    public Student registerStudent(Student student) {
        student.setEmail(EmailUtils.normalize(student.getEmail()));

        // Student, Warden, SecurityGuard, and Admin are separate tables, so email must be
        // checked as a single global namespace here, not just against the students table —
        // otherwise this email could collide with (and shadow) an existing staff account.
        if (emailUniquenessService.existsAnywhere(student.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        if (studentRepository.existsByRollNo(student.getRollNo())) {
            throw new RuntimeException("Roll number already exists");
        }

        student.setEmailVerified(false);
        student.setPasswordHash(passwordEncoder.encode(student.getPasswordHash()));
        Student registered = studentRepository.save(student);

        // Validates the real Room (capacity + effective department) and creates the
        // RoomAllocation that locks the student into this room. Runs in the same
        // transaction as the student insert above, so any failure here (room not
        // found, full, or department mismatch) rolls back the whole registration
        // rather than leaving a student without a valid room.
        roomService.allocateForRegistration(registered);
        studentRepository.save(registered);

        try {
            dispatchVerificationEmail(registered.getEmail(), registered.getName());
        } catch (Exception e) {
            log.warn("Verification email failed for {}: {}", registered.getEmail(), e.getMessage());
        }

        return registered;
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken vToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired verification link"));

        if (vToken.isExpired()) {
            emailVerificationTokenRepository.delete(vToken);
            throw new RuntimeException("Verification link has expired. Please request a new one.");
        }

        Student student = studentRepository.findByEmail(vToken.getEmail())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        student.setEmailVerified(true);
        studentRepository.save(student);
        emailVerificationTokenRepository.delete(vToken);
    }

    @Transactional
    public void resendVerification(String email) {
        // Deliberately does not throw when the account doesn't exist or is already
        // verified — the caller always returns the same generic response so this
        // endpoint can't be used to enumerate registered emails.
        studentRepository.findByEmailIgnoreCase(email).ifPresent(student -> {
            if (Boolean.FALSE.equals(student.getEmailVerified())) {
                try {
                    dispatchVerificationEmail(email, student.getName());
                } catch (Exception e) {
                    log.warn("Verification email resend failed for {}: {}", email, e.getMessage());
                }
            }
        });
    }

    private void dispatchVerificationEmail(String email, String name) {
        emailVerificationTokenRepository.deleteByEmail(email);
        String token = UUID.randomUUID().toString();
        emailVerificationTokenRepository.save(
                EmailVerificationToken.builder()
                        .token(token)
                        .email(email)
                        .expiresAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusHours(24))
                        .build()
        );
        emailService.sendVerificationEmail(email, name, token);
    }

    private UserPrincipal getUserPrincipalById(Long userId, Role role) {
        return switch (role) {
            case STUDENT -> {
                Student student = studentRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Student not found"));
                yield UserPrincipal.create(student.getId(), student.getEmail(),
                        student.getPasswordHash(), Role.STUDENT);
            }
            case WARDEN -> {
                Warden warden = wardenRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Warden not found"));
                if (Boolean.FALSE.equals(warden.getEnabled())) {
                    throw new RuntimeException("This account has been disabled. Contact an administrator.");
                }
                yield UserPrincipal.create(warden.getId(), warden.getEmail(),
                        warden.getPasswordHash(), Role.WARDEN);
            }
            case SECURITY_GUARD -> {
                SecurityGuard guard = securityGuardRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Security guard not found"));
                if (Boolean.FALSE.equals(guard.getEnabled())) {
                    throw new RuntimeException("This account has been disabled. Contact an administrator.");
                }
                yield UserPrincipal.create(guard.getId(), guard.getEmail(),
                        guard.getPasswordHash(), Role.SECURITY_GUARD);
            }
            case ADMIN -> {
                Admin admin = adminRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("Admin not found"));
                yield UserPrincipal.create(admin.getId(), admin.getEmail(),
                        admin.getPasswordHash(), Role.ADMIN);
            }
        };
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
