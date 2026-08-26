package com.outpass.portal.service;

import com.outpass.portal.model.entity.Admin;
import com.outpass.portal.model.entity.SecurityGuard;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.entity.Warden;
import com.outpass.portal.model.enums.Role;
import com.outpass.portal.repository.AdminRepository;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.security.UserPrincipal;
import com.outpass.portal.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final StudentRepository studentRepository;
    private final WardenRepository wardenRepository;
    private final SecurityGuardRepository securityGuardRepository;
    private final AdminRepository adminRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Normalize once so login/JWT lookups are case-insensitive regardless of how the
        // email was typed at login time or cased when originally stored.
        String normalized = EmailUtils.normalize(email);

        // Try to find student
        Student student = studentRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (student != null) {
            return UserPrincipal.create(student.getId(), student.getEmail(),
                    student.getPasswordHash(), Role.STUDENT);
        }

        // Try to find warden
        Warden warden = wardenRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (warden != null) {
            return UserPrincipal.create(warden.getId(), warden.getEmail(),
                    warden.getPasswordHash(), Role.WARDEN, isEnabled(warden.getEnabled()));
        }

        // Try to find security guard
        SecurityGuard guard = securityGuardRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (guard != null) {
            return UserPrincipal.create(guard.getId(), guard.getEmail(),
                    guard.getPasswordHash(), Role.SECURITY_GUARD, isEnabled(guard.getEnabled()));
        }

        // Try to find admin
        Admin admin = adminRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (admin != null) {
            return UserPrincipal.create(admin.getId(), admin.getEmail(),
                    admin.getPasswordHash(), Role.ADMIN);
        }

        throw new UsernameNotFoundException("User not found with email: " + email);
    }

    private boolean isEnabled(Boolean enabled) {
        return enabled == null || enabled;
    }
}
