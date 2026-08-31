package com.outpass.portal.service;

import com.outpass.portal.model.entity.Building;
import com.outpass.portal.model.entity.Warden;
import com.outpass.portal.model.entity.WardenBuilding;
import com.outpass.portal.repository.BuildingRepository;
import com.outpass.portal.repository.WardenBuildingRepository;
import com.outpass.portal.repository.WardenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Admin-managed warden <-> building assignment: one warden may cover several buildings.
// Warden.hostel stays the single "primary" hostel every existing warden-scoped runtime query
// reads (dashboard, outpass, attendance, complaints, room management -- see
// backend/AGENTS.md); this table tracks the full assigned set for the Admin UI and keeps
// `hostel` in sync as buildings are added/removed, so existing single-hostel warden behavior
// is unchanged for a warden with exactly one assignment.
@Service
@RequiredArgsConstructor
@Slf4j
public class WardenBuildingService {

    private final WardenBuildingRepository wardenBuildingRepository;
    private final WardenRepository wardenRepository;
    private final BuildingRepository buildingRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBuildingsForWarden(Long wardenId) {
        requireWarden(wardenId);
        return toResponse(wardenId);
    }

    // Authoritative source of the hostels a warden may actually operate on: every
    // warden-scoped controller/service method reads this (via WardenController's
    // resolveWardenHostels) instead of the single Warden.hostel string, so a warden
    // assigned to several buildings gets real access to all of them. Never returns null;
    // an empty list means the warden has zero assignments and must be denied everywhere,
    // not treated as unrestricted -- callers must not confuse this with the admin
    // "null == unrestricted" sentinel used throughout RoomService/OutpassService/etc.
    @Transactional(readOnly = true)
    public List<String> getAssignedHostelNames(Long wardenId) {
        return wardenBuildingRepository.findByWardenIdOrderByCreatedAtAsc(wardenId).stream()
                .map(wb -> wb.getBuilding().getName())
                .collect(Collectors.toList());
    }

    // Building.id equivalent of getAssignedHostelNames, for the FK-based tables
    // (attendance_sessions, room_config) that intentionally do not use the free-text
    // hostel-name matching convention the rest of the app relies on -- see
    // backend/AGENTS.md. Same never-null, same empty-means-deny-everything contract as
    // getAssignedHostelNames.
    @Transactional(readOnly = true)
    public List<Long> getAssignedBuildingIds(Long wardenId) {
        return wardenBuildingRepository.findByWardenIdOrderByCreatedAtAsc(wardenId).stream()
                .map(wb -> wb.getBuilding().getId())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<Map<String, Object>> assignBuilding(Long wardenId, Long buildingId) {
        Warden warden = requireWarden(wardenId);
        // Resolving the building by its own primary key (rather than the free-text name
        // matching Warden.hostel/Student.hostel elsewhere) means a typo can never silently
        // assign a warden to a non-existent hostel -- see backend/AGENTS.md's NRI incident.
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        if (wardenBuildingRepository.existsByWardenIdAndBuildingId(wardenId, buildingId)) {
            throw new RuntimeException("This building is already assigned to the warden");
        }
        wardenBuildingRepository.save(WardenBuilding.builder().warden(warden).building(building).build());
        // The first building ever assigned becomes (or keeps) the warden's primary hostel;
        // later additions extend the assigned set without touching the primary, so every
        // existing single-hostel-scoped endpoint keeps working unchanged.
        if (warden.getHostel() == null || warden.getHostel().isBlank()) {
            warden.setHostel(building.getName());
            wardenRepository.save(warden);
        }
        log.info("Building assigned to warden: wardenId={} buildingId={}", wardenId, buildingId);
        return toResponse(wardenId);
    }

    @Transactional
    public List<Map<String, Object>> unassignBuilding(Long wardenId, Long buildingId) {
        Warden warden = requireWarden(wardenId);
        if (!wardenBuildingRepository.existsByWardenIdAndBuildingId(wardenId, buildingId)) {
            throw new RuntimeException("This building is not assigned to the warden");
        }
        wardenBuildingRepository.deleteByWardenIdAndBuildingId(wardenId, buildingId);
        List<WardenBuilding> remaining = wardenBuildingRepository.findByWardenIdOrderByCreatedAtAsc(wardenId);
        Building removed = buildingRepository.findById(buildingId).orElse(null);
        // If the removed building was the warden's primary hostel, fall back to the
        // earliest-remaining assignment so the warden isn't silently locked out of every
        // hostel-scoped endpoint; with none left, hostel becomes null (explicitly unassigned).
        if (removed != null && removed.getName().equals(warden.getHostel())) {
            warden.setHostel(remaining.isEmpty() ? null : remaining.get(0).getBuilding().getName());
            wardenRepository.save(warden);
        }
        log.info("Building unassigned from warden: wardenId={} buildingId={}", wardenId, buildingId);
        return remaining.stream().map(WardenBuildingService::toMap).collect(Collectors.toList());
    }

    private Warden requireWarden(Long wardenId) {
        return wardenRepository.findById(wardenId)
                .orElseThrow(() -> new RuntimeException("Warden not found"));
    }

    private List<Map<String, Object>> toResponse(Long wardenId) {
        return wardenBuildingRepository.findByWardenIdOrderByCreatedAtAsc(wardenId).stream()
                .map(WardenBuildingService::toMap)
                .collect(Collectors.toList());
    }

    private static Map<String, Object> toMap(WardenBuilding wb) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", wb.getBuilding().getId());
        m.put("name", wb.getBuilding().getName());
        return m;
    }
}
