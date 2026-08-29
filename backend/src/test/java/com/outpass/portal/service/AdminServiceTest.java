package com.outpass.portal.service;

import com.outpass.portal.dto.request.AdminCreateSecurityGuardRequest;
import com.outpass.portal.dto.request.AdminCreateWardenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Admin-created Warden/SecurityGuard accounts must also be checked against the global
 * email namespace (Student/Warden/SecurityGuard/Admin) -- not just their own table --
 * otherwise a colliding email would silently create an unreachable account (shadowed by
 * whichever table CustomUserDetailsService checks first).
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private com.outpass.portal.repository.WardenRepository wardenRepository;
    @Mock private com.outpass.portal.repository.SecurityGuardRepository securityGuardRepository;
    @Mock private com.outpass.portal.repository.BuildingRepository buildingRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailUniquenessService emailUniquenessService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(wardenRepository, securityGuardRepository, buildingRepository, passwordEncoder, emailUniquenessService);
    }

    private AdminCreateWardenRequest wardenRequest(String email) {
        AdminCreateWardenRequest r = new AdminCreateWardenRequest();
        r.setName("W"); r.setEmail(email); r.setPassword("password123"); r.setHostel("NRI");
        return r;
    }

    private AdminCreateSecurityGuardRequest guardRequest(String email) {
        AdminCreateSecurityGuardRequest r = new AdminCreateSecurityGuardRequest();
        r.setName("G"); r.setEmail(email); r.setPassword("password123"); r.setHostel("NRI");
        return r;
    }

    @Test
    void cannotCreateWardenUsingExistingStudentEmail() {
        when(emailUniquenessService.existsAnywhere("student@x.com")).thenReturn(true);

        assertThatThrownBy(() -> adminService.createWarden(wardenRequest("student@x.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");

        verify(wardenRepository, never()).save(any());
    }

    @Test
    void cannotCreateWardenUsingExistingAdminEmail() {
        when(emailUniquenessService.existsAnywhere("admin@x.com")).thenReturn(true);

        assertThatThrownBy(() -> adminService.createWarden(wardenRequest("admin@x.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");

        verify(wardenRepository, never()).save(any());
    }

    @Test
    void cannotCreateSecurityGuardUsingExistingStudentEmail() {
        when(emailUniquenessService.existsAnywhere("student2@x.com")).thenReturn(true);

        assertThatThrownBy(() -> adminService.createSecurityGuard(guardRequest("student2@x.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");

        verify(securityGuardRepository, never()).save(any());
    }

    @Test
    void createWardenNormalizesEmailCaseBeforeCheckingAndStoring() {
        when(emailUniquenessService.existsAnywhere("newwarden@x.com")).thenReturn(false);
        when(buildingRepository.findByName("NRI"))
                .thenReturn(java.util.Optional.of(com.outpass.portal.model.entity.Building.builder().id(1L).name("NRI").build()));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(wardenRepository.save(any())).thenAnswer(inv -> {
            var w = inv.getArgument(0, com.outpass.portal.model.entity.Warden.class);
            w.setId(1L);
            return w;
        });

        var result = adminService.createWarden(wardenRequest("NewWarden@X.com"));

        assertThat(result.getEmail()).isEqualTo("newwarden@x.com");
        verify(emailUniquenessService).existsAnywhere("newwarden@x.com");
    }

    @Test
    void createWardenSucceedsWhenEmailIsGloballyUnique() {
        when(emailUniquenessService.existsAnywhere("free@x.com")).thenReturn(false);
        when(buildingRepository.findByName("NRI"))
                .thenReturn(java.util.Optional.of(com.outpass.portal.model.entity.Building.builder().id(1L).name("NRI").build()));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(wardenRepository.save(any())).thenAnswer(inv -> {
            var w = inv.getArgument(0, com.outpass.portal.model.entity.Warden.class);
            w.setId(2L);
            return w;
        });

        adminService.createWarden(wardenRequest("free@x.com"));

        verify(wardenRepository).save(any());
    }

    @Test
    void cannotCreateWardenWithHostelThatDoesNotMatchAnyBuilding() {
        when(emailUniquenessService.existsAnywhere("newwarden2@x.com")).thenReturn(false);
        when(buildingRepository.findByName("NRI")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> adminService.createWarden(wardenRequest("newwarden2@x.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not match any existing building");

        verify(wardenRepository, never()).save(any());
    }

    @Test
    void cannotCreateSecurityGuardWithHostelThatDoesNotMatchAnyBuilding() {
        when(emailUniquenessService.existsAnywhere("newguard@x.com")).thenReturn(false);
        when(buildingRepository.findByName("NRI")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> adminService.createSecurityGuard(guardRequest("newguard@x.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not match any existing building");

        verify(securityGuardRepository, never()).save(any());
    }
}
