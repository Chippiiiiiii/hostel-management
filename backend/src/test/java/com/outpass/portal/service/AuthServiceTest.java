package com.outpass.portal.service;

import com.outpass.portal.model.entity.Student;
import com.outpass.portal.repository.AdminRepository;
import com.outpass.portal.repository.EmailVerificationTokenRepository;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, tokenProvider, refreshTokenService,
                studentRepository, wardenRepository, securityGuardRepository, adminRepository,
                passwordEncoder, emailService, emailVerificationTokenRepository, roomService,
                emailUniquenessService);
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
}
