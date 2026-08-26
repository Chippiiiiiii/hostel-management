package com.outpass.portal.service;

import com.outpass.portal.repository.AdminRepository;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.repository.StudentRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Student, Warden, SecurityGuard, and Admin are separate entities/tables (no unified User
// table), but an email address must be globally unique across all of them — otherwise
// CustomUserDetailsService's per-table lookup order can silently "shadow" one account with
// another that happens to share an email. This is the single place that cross-table check
// happens, reused by every account-creation path (student self-registration, and admin
// creating a warden/security-guard).
@Service
@RequiredArgsConstructor
public class EmailUniquenessService {

    private final StudentRepository studentRepository;
    private final WardenRepository wardenRepository;
    private final SecurityGuardRepository securityGuardRepository;
    private final AdminRepository adminRepository;

    @Transactional(readOnly = true)
    public boolean existsAnywhere(String email) {
        String normalized = EmailUtils.normalize(email);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return studentRepository.existsByEmailIgnoreCase(normalized)
                || wardenRepository.existsByEmailIgnoreCase(normalized)
                || securityGuardRepository.existsByEmailIgnoreCase(normalized)
                || adminRepository.existsByEmailIgnoreCase(normalized);
    }
}
