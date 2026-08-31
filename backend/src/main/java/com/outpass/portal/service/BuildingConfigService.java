package com.outpass.portal.service;

import com.outpass.portal.model.entity.Building;
import com.outpass.portal.model.entity.RoomConfig;
import com.outpass.portal.repository.BuildingRepository;
import com.outpass.portal.repository.RoomConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Single scoped access point for the room_config table, shared by AttendanceService
// (wifi_allowed_subnets, hostel_latitude/longitude/radius) and RoomService
// (max_rooms_per_floor, max_members_per_room, and the same wifi_allowed_subnets key) --
// see backend/AGENTS.md. Before this class existed, both services carried independent
// copies of "find row by key, get value or default, write value" with no shared scoping,
// which is how wifi_allowed_subnets ended up with two different writers that could
// silently disagree on how a hostel was resolved. Every read/write here goes through
// exactly this class now, so a scoping fix applied once applies to both callers.
@Service
@RequiredArgsConstructor
public class BuildingConfigService {

    // Every config key either service reads/writes through this class, in one place so a
    // new building's seeding (RoomService.addBuilding) never drifts out of sync with the
    // set of keys AttendanceService/RoomService actually consume.
    public static final List<String> ALL_CONFIG_KEYS = List.of(
            "max_rooms_per_floor", "max_members_per_room", "wifi_allowed_subnets",
            "hostel_latitude", "hostel_longitude", "hostel_radius");

    private final RoomConfigRepository configRepository;
    private final BuildingRepository buildingRepository;

    // buildingId == null reads only the admin-set campus default (used by callers that are
    // themselves reading the default template, e.g. seedBuildingDefaults). Every other
    // caller passes a real buildingId and falls back to the default row, then to
    // defaultValue, if no per-building override has ever been written.
    @Transactional(readOnly = true)
    public String getConfigString(String key, Long buildingId, String defaultValue) {
        if (buildingId != null) {
            var byBuilding = configRepository.findByConfigKeyAndBuilding_Id(key, buildingId);
            if (byBuilding.isPresent()) {
                return byBuilding.get().getConfigValue();
            }
        }
        return configRepository.findByConfigKeyAndBuilding_IdIsNull(key)
                .map(RoomConfig::getConfigValue)
                .orElse(defaultValue);
    }

    // Always writes a per-building row -- there is no code path in this app that writes
    // the building == null default row (it is seeded once by the migration/backfill and
    // read-only from application code), so a null buildingId here is a programming error,
    // not a valid "write the global default" request.
    @Transactional
    public void saveConfigValue(String key, Long buildingId, String value) {
        if (buildingId == null) {
            throw new IllegalArgumentException("buildingId is required to write config key '" + key + "'");
        }
        RoomConfig config = configRepository.findByConfigKeyAndBuilding_Id(key, buildingId)
                .orElseGet(() -> RoomConfig.builder()
                        .configKey(key)
                        .building(getBuildingReference(buildingId))
                        .build());
        config.setConfigValue(value);
        configRepository.save(config);
    }

    // Copies the admin-set default template into a brand-new building's own rows, so it
    // starts from the admin's intended baseline instead of silently falling through to
    // hardcoded Java literals the admin never reviewed. Called once, from
    // RoomService.addBuilding. A no-op for any key with no default row configured.
    @Transactional
    public void seedBuildingDefaults(Long buildingId, List<String> keys) {
        for (String key : keys) {
            configRepository.findByConfigKeyAndBuilding_IdIsNull(key).ifPresent(defaultRow -> {
                if (configRepository.findByConfigKeyAndBuilding_Id(key, buildingId).isEmpty()) {
                    configRepository.save(RoomConfig.builder()
                            .configKey(key)
                            .configValue(defaultRow.getConfigValue())
                            .building(getBuildingReference(buildingId))
                            .build());
                }
            });
        }
    }

    private Building getBuildingReference(Long buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
    }
}
