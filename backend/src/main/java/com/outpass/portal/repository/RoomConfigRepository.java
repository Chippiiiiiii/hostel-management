package com.outpass.portal.repository;

import com.outpass.portal.model.entity.RoomConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomConfigRepository extends JpaRepository<RoomConfig, Long> {
    // No campus-wide findByConfigKey(key) here by design -- every config read/write must
    // resolve a specific building first, falling back to the explicit
    // findByConfigKeyAndBuilding_IdIsNull(key) admin-default row only when no per-building
    // override exists (see BuildingConfigService). A global finder here reopens exactly the
    // cross-hostel WiFi/geofence/capacity finding this table was migrated to fix.
    Optional<RoomConfig> findByConfigKeyAndBuilding_Id(String configKey, Long buildingId);
    Optional<RoomConfig> findByConfigKeyAndBuilding_IdIsNull(String configKey);
}
