package com.outpass.portal.service;

import com.outpass.portal.model.entity.RefreshToken;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.enums.Role;
import com.outpass.portal.repository.AdminRepository;
import com.outpass.portal.repository.EmailVerificationTokenRepository;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.security.JwtTokenProvider;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.util.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Registration must reject an email that already belongs to ANY account type (Student,
 * Warden, SecurityGuard, Admin) -- not just an existing student -- since the four are
 * separate tables with no shared uniqueness constraint at the database level.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private StudentRepository studentRepository;
    @Mock private WardenRepository wardenRepository;
    @Mock private SecurityGuardRepository securityGuardRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private RoomService roomService;
    @Mock private EmailUniquenessService emailUniquenessService;
    @Mock private LoginAttemptService loginAttemptService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, tokenProvider, refreshTokenService,
                studentRepository, wardenRepository, securityGuardRepository, adminRepository,
                passwordEncoder, emailService, emailVerificationTokenRepository, roomService,
                emailUniquenessService, loginAttemptService);
    }

    private UserPrincipal principal(Long id, String email, Role role) {
        return UserPrincipal.builder()
                .id(id)
                .email(email)
                .password("hashed")
                .role(role)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role.name())))
                .enabled(true)
                .build();
    }

    private Student student(String email) {
        return Student.builder().name("S").email(email).passwordHash("pw").rollNo("R1")
                .department("CT").hostel("Building A").roomNumber("101")
                .contactNumber("9000000000").parentNumber("9000000001").gender("BOY").build();
    }

    // Covers: student email cannot equal an existing Warden/SecurityGuard/Admin email.
    // EmailUniquenessService is the single reusable check across all three account types
    // (see EmailUniquenessServiceTest for per-table coverage); here we verify AuthService
    // actually calls it -- and rejects -- rather than only checking its own table.
    @Test
    void registrationRejectedWhenEmailAlreadyExistsForAnyAccountType() {
        Student s = student("shared@x.com");
        when(emailUniquenessService.existsAnywhere("shared@x.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerStudent(s))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");

        verify(studentRepository, never()).save(any());
        verify(roomService, never()).allocateForRegistration(any());
    }

    @Test
    void registrationRejectedCaseInsensitively() {
        Student s = student("Shared@X.com");
        when(emailUniquenessService.existsAnywhere("shared@x.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerStudent(s))
                .isInstanceOf(RuntimeException.class);

        verify(studentRepository, never()).save(any());
    }

    @Test
    void registrationNormalizesEmailBeforeCheckingAndStoring() {
        Student s = student("  Fresh@X.com  ");
        when(emailUniquenessService.existsAnywhere("fresh@x.com")).thenReturn(false);
        when(studentRepository.existsByRollNo("R1")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        Student registered = authService.registerStudent(s);

        assertThat(registered.getEmail()).isEqualTo("fresh@x.com");
        verify(emailUniquenessService).existsAnywhere("fresh@x.com");
    }

    @Test
    void registrationProceedsWhenEmailIsGloballyUnique() {
        Student s = student("fresh2@x.com");
        when(emailUniquenessService.existsAnywhere("fresh2@x.com")).thenReturn(false);
        when(studentRepository.existsByRollNo("R1")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.registerStudent(s);

        // registerStudent saves the student, then again after allocateForRegistration syncs
        // hostel/roomNumber -- pre-existing behavior, unrelated to this fix.
        verify(studentRepository, atLeastOnce()).save(any(Student.class));
        verify(roomService).allocateForRegistration(any(Student.class));
    }

    // ==================== Login: account backoff wiring ====================
    // These verify AuthService.login correctly drives LoginAttemptService -- checking the
    // block before authenticating, and recording failure/success afterwards -- not the
    // backoff math itself (see LoginAttemptServiceTest for that).

    @Test
    void loginChecksAccountNotBlockedBeforeAuthenticating() {
        doThrow(new com.outpass.portal.exception.RateLimitExceededException("blocked"))
                .when(loginAttemptService).checkNotBlocked("blocked@x.com");

        assertThatThrownBy(() -> authService.login("Blocked@X.com", "pw", Role.STUDENT))
                .isInstanceOf(com.outpass.portal.exception.RateLimitExceededException.class);

        verify(loginAttemptService).checkNotBlocked("blocked@x.com");
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void loginRecordsFailureOnBadCredentialsAndRethrows() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad creds"));

        assertThatThrownBy(() -> authService.login("User@X.com", "wrong", Role.STUDENT))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptService).recordFailure("user@x.com");
        verify(loginAttemptService, never()).recordSuccess(anyString());
    }

    @Test
    void loginRecordsFailureOnRoleMismatchSoSwitchingLoginEndpointsCannotBypassBackoff() {
        UserPrincipal wardenPrincipal = principal(1L, "user@x.com", Role.WARDEN);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                wardenPrincipal, null, wardenPrincipal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        // Correct warden credentials submitted through the STUDENT login endpoint.
        assertThatThrownBy(() -> authService.login("User@X.com", "correct-warden-password", Role.STUDENT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid credentials for this user type");

        verify(loginAttemptService).recordFailure("user@x.com");
        verify(loginAttemptService, never()).recordSuccess(anyString());
        verify(refreshTokenService, never()).createRefreshToken(any(), any());
    }

    @Test
    void loginRecordsSuccessAndClearsBackoffOnValidCredentials() {
        UserPrincipal studentPrincipal = principal(2L, "user@x.com", Role.STUDENT);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                studentPrincipal, null, studentPrincipal.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(tokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(studentRepository.findByEmailIgnoreCase("User@X.com"))
                .thenReturn(Optional.of(student("User@X.com")));
        when(refreshTokenService.createRefreshToken(2L, "STUDENT"))
                .thenReturn(RefreshToken.builder().token("rt-1").userId(2L).userType("STUDENT")
                        .expiryDate(Instant.now().plusSeconds(600)).build());

        authService.login("User@X.com", "correct-password", Role.STUDENT);

        verify(loginAttemptService).checkNotBlocked("user@x.com");
        verify(loginAttemptService).recordSuccess("user@x.com");
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    // ==================== Refresh token rotation ====================

    @Test
    void refreshTokenRotatesTokenOnSuccessfulUse() {
        RefreshToken oldToken = RefreshToken.builder()
                .id(10L).token("old-token").userId(5L).userType("STUDENT")
                .expiryDate(Instant.now().plusSeconds(600)).build();
        when(refreshTokenService.findByToken("old-token")).thenReturn(Optional.of(oldToken));
        when(refreshTokenService.verifyExpiration(oldToken)).thenReturn(oldToken);
        when(refreshTokenService.consumeToken("old-token")).thenReturn(1);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(
                Student.builder().id(5L).email("user@x.com").passwordHash("hashed").build()));
        when(tokenProvider.generateAccessToken(any())).thenReturn("new-access-token");
        RefreshToken newToken = RefreshToken.builder()
                .id(11L).token("new-token").userId(5L).userType("STUDENT")
                .expiryDate(Instant.now().plusSeconds(600)).build();
        when(refreshTokenService.createRefreshToken(5L, "STUDENT")).thenReturn(newToken);

        AuthService.AuthResponse response = authService.refreshToken("old-token");

        assertThat(response.refreshToken).isEqualTo("new-token");
        // The old token must be atomically consumed (not just left in place) so a later
        // replay of "old-token" can never be exchanged for another access token.
        verify(refreshTokenService).consumeToken("old-token");
        verify(refreshTokenService).createRefreshToken(5L, "STUDENT");
    }

    @Test
    void refreshTokenReplayOfAlreadyRotatedTokenFails() {
        // Once rotated (deleted), a subsequent lookup of the same token string finds
        // nothing -- simulating a second, replayed use of the same refresh token.
        when(refreshTokenService.findByToken("old-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("old-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Refresh token not found");

        verify(refreshTokenService, never()).createRefreshToken(any(), any());
    }

    @Test
    void refreshTokenRejectedWhenConcurrentRequestAlreadyConsumedIt() {
        // Simulates the losing side of a race: findByToken/verifyExpiration both still see
        // the token (it hadn't been deleted yet when this thread read it), but by the time
        // this thread's atomic consumeToken() runs, a concurrent request has already deleted
        // the row -- consumeToken reports 0 affected rows rather than 1. This must be
        // rejected exactly like an unknown/replayed token, and must NOT mint a new token pair.
        RefreshToken presented = RefreshToken.builder()
                .id(10L).token("raced-token").userId(5L).userType("STUDENT")
                .expiryDate(Instant.now().plusSeconds(600)).build();
        when(refreshTokenService.findByToken("raced-token")).thenReturn(Optional.of(presented));
        when(refreshTokenService.verifyExpiration(presented)).thenReturn(presented);
        when(refreshTokenService.consumeToken("raced-token")).thenReturn(0);

        assertThatThrownBy(() -> authService.refreshToken("raced-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Refresh token not found");

        verify(refreshTokenService, never()).createRefreshToken(any(), any());
        verifyNoInteractions(tokenProvider);
    }
}
