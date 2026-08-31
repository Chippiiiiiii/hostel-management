package com.outpass.portal.service;

import com.outpass.portal.dto.request.AdminCreateSecurityGuardRequest;
import com.outpass.portal.dto.request.AdminCreateWardenRequest;
import com.outpass.portal.dto.response.SecurityGuardSummaryResponse;
import com.outpass.portal.dto.response.WardenSummaryResponse;
import com.outpass.portal.model.entity.Building;
import com.outpass.portal.model.entity.SecurityGuard;
import com.outpass.portal.model.entity.Warden;
import com.outpass.portal.repository.BuildingRepository;
import com.outpass.portal.repository.SecurityGuardRepository;
import com.outpass.portal.repository.WardenRepository;
import com.outpass.portal.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final WardenRepository wardenRepository;
    private final SecurityGuardRepository securityGuardRepository;
    private final BuildingRepository buildingRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailUniquenessService emailUniquenessService;
    private final WardenBuildingService wardenBuildingService;

    @Transactional
    public WardenSummaryResponse createWarden(AdminCreateWardenRequest request) {
        String email = EmailUtils.normalize(request.getEmail());
        // Checked globally (Student/Warden/SecurityGuard/Admin), not just wardens — an email
        // already used by any account type would otherwise silently shadow one of them.
        if (emailUniquenessService.existsAnywhere(email)) {
            throw new RuntimeException("Email already exists");
        }
        // The entire hostel-scoping/authorization model relies on exact string equality
        // between Warden.hostel and Building.name (see Student.hostel/RoomService). A typo
        // here would silently create a warden who can never see or approve anything.
        Building building = buildingRepository.findByName(request.getHostel())
                .orElseThrow(() -> new RuntimeException("Hostel does not match any existing building"));
        Warden warden = Warden.builder()
                .name(request.getName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .hostel(request.getHostel())
                .phone(request.getPhone())
                .enabled(true)
                .build();
        Warden saved = wardenRepository.save(warden);
        // Keep the warden_buildings assignment table in sync with the hostel set at creation
        // time, so the admin's "manage buildings" view for this warden isn't empty until they
        // manually re-add the building they just assigned above.
        wardenBuildingService.assignBuilding(saved.getId(), building.getId());
        log.info("Warden account created: id={} email={}", saved.getId(), saved.getEmail());
        return WardenSummaryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<WardenSummaryResponse> listWardens() {
        return wardenRepository.findAll().stream()
                .map(WardenSummaryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public WardenSummaryResponse setWardenEnabled(Long wardenId, boolean enabled) {
        Warden warden = wardenRepository.findById(wardenId)
                .orElseThrow(() -> new RuntimeException("Warden not found"));
        warden.setEnabled(enabled);
        Warden saved = wardenRepository.save(warden);
        log.info("Warden account {}: id={} email={}", enabled ? "enabled" : "disabled", saved.getId(), saved.getEmail());
        return WardenSummaryResponse.from(saved);
    }

    @Transactional
    public SecurityGuardSummaryResponse createSecurityGuard(AdminCreateSecurityGuardRequest request) {
        String email = EmailUtils.normalize(request.getEmail());
        if (emailUniquenessService.existsAnywhere(email)) {
            throw new RuntimeException("Email already exists");
        }
        if (buildingRepository.findByName(request.getHostel()).isEmpty()) {
            throw new RuntimeException("Hostel does not match any existing building");
        }
        SecurityGuard guard = SecurityGuard.builder()
                .name(request.getName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .hostel(request.getHostel())
                .phone(request.getPhone())
                .enabled(true)
                .build();
        SecurityGuard saved = securityGuardRepository.save(guard);
        log.info("Security guard account created: id={} email={}", saved.getId(), saved.getEmail());
        return SecurityGuardSummaryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SecurityGuardSummaryResponse> listSecurityGuards() {
        return securityGuardRepository.findAll().stream()
                .map(SecurityGuardSummaryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public SecurityGuardSummaryResponse setSecurityGuardEnabled(Long guardId, boolean enabled) {
        SecurityGuard guard = securityGuardRepository.findById(guardId)
                .orElseThrow(() -> new RuntimeException("Security guard not found"));
        guard.setEnabled(enabled);
        SecurityGuard saved = securityGuardRepository.save(guard);
        log.info("Security guard account {}: id={} email={}", enabled ? "enabled" : "disabled", saved.getId(), saved.getEmail());
        return SecurityGuardSummaryResponse.from(saved);
    }
}
