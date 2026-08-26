package com.outpass.portal.service;

import com.outpass.portal.repository.AdminRepository;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Student, Warden, SecurityGuard, and Admin are separate tables with independent unique
 * constraints, so nothing at the database level stops the same email being used across
 * them. This service is the single place that cross-table check happens; these tests
 * verify it actually looks at all four tables and normalizes case before comparing.
 */
@ExtendWith(MockitoExtension.class)
class EmailUniquenessServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private WardenRepository wardenRepository;
    @Mock private SecurityGuardRepository securityGuardRepository;
    @Mock private AdminRepository adminRepository;

    private EmailUniquenessService service;

    @BeforeEach
    void setUp() {
        service = new EmailUniquenessService(studentRepository, wardenRepository, securityGuardRepository, adminRepository);
    }

    @Test
    void reportsCollisionWhenEmailBelongsToAnExistingStudent() {
        when(studentRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(true);

        assertThat(service.existsAnywhere("new@x.com")).isTrue();
    }

    @Test
    void reportsCollisionWhenEmailBelongsToAnExistingWarden() {
        when(studentRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(wardenRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(true);

        assertThat(service.existsAnywhere("new@x.com")).isTrue();
    }

    @Test
    void reportsCollisionWhenEmailBelongsToAnExistingSecurityGuard() {
        when(studentRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(wardenRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(securityGuardRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(true);

        assertThat(service.existsAnywhere("new@x.com")).isTrue();
    }

    @Test
    void reportsCollisionWhenEmailBelongsToAnExistingAdmin() {
        when(studentRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(wardenRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(securityGuardRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(adminRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(true);

        assertThat(service.existsAnywhere("new@x.com")).isTrue();
    }

    @Test
    void reportsNoCollisionWhenEmailIsFreeEverywhere() {
        when(studentRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(wardenRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(securityGuardRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(adminRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        assertThat(service.existsAnywhere("nobody@x.com")).isFalse();
    }

    @Test
    void normalizesCaseAndWhitespaceBeforeCheckingEveryTable() {
        when(studentRepository.existsByEmailIgnoreCase("test@gmail.com")).thenReturn(false);
        when(wardenRepository.existsByEmailIgnoreCase("test@gmail.com")).thenReturn(false);
        when(securityGuardRepository.existsByEmailIgnoreCase("test@gmail.com")).thenReturn(false);
        when(adminRepository.existsByEmailIgnoreCase("test@gmail.com")).thenReturn(false);

        // "Test@gmail.com" and "test@gmail.com" (with incidental whitespace) must be
        // treated as the same email -- every repository call must receive the normalized form.
        service.existsAnywhere("  Test@Gmail.com  ");

        verify(studentRepository).existsByEmailIgnoreCase(eq("test@gmail.com"));
        verify(wardenRepository).existsByEmailIgnoreCase(eq("test@gmail.com"));
        verify(securityGuardRepository).existsByEmailIgnoreCase(eq("test@gmail.com"));
        verify(adminRepository).existsByEmailIgnoreCase(eq("test@gmail.com"));
    }

    @Test
    void shortCircuitsOnFirstMatchWithoutCheckingLaterTables() {
        when(studentRepository.existsByEmailIgnoreCase("student@x.com")).thenReturn(true);

        assertThat(service.existsAnywhere("student@x.com")).isTrue();

        verify(wardenRepository, never()).existsByEmailIgnoreCase(anyString());
        verify(securityGuardRepository, never()).existsByEmailIgnoreCase(anyString());
        verify(adminRepository, never()).existsByEmailIgnoreCase(anyString());
    }
}
