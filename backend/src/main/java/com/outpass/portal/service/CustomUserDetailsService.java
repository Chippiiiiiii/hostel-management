package com.outpass.portal.service;

import com.outpass.portal.model.entity.SecurityGuard;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.model.entity.Warden;
import com.outpass.portal.model.enums.Role;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.security.UserPrincipal;
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

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Try to find student
        Student student = studentRepository.findByEmail(email).orElse(null);
        if (student != null) {
            return UserPrincipal.create(student.getId(), student.getEmail(),
                    student.getPasswordHash(), Role.STUDENT);
        }

        // Try to find warden
        Warden warden = wardenRepository.findByEmail(email).orElse(null);
        if (warden != null) {
            return UserPrincipal.create(warden.getId(), warden.getEmail(),
                    warden.getPasswordHash(), Role.WARDEN);
        }

        // Try to find security guard
        SecurityGuard guard = securityGuardRepository.findByEmail(email).orElse(null);
        if (guard != null) {
            return UserPrincipal.create(guard.getId(), guard.getEmail(),
                    guard.getPasswordHash(), Role.SECURITY_GUARD);
        }

        throw new UsernameNotFoundException("User not found with email: " + email);
    }
}

