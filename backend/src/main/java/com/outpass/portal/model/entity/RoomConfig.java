package com.outpass.portal.model.entity;

import jakarta.persistence.*;
import lombok.*;

// A row with building == null is the ADMIN-set campus-wide default template that
// RoomService.addBuilding seeds new buildings from (see BuildingConfigService) -- it is
// never written by a warden-facing endpoint, only read as a fallback, so exactly one
// default row per configKey is expected to ever exist without needing DB-level
// enforcement (MySQL does not treat NULLs as equal in a composite unique index, so
// uk_config_key_building cannot by itself prevent duplicate default rows -- see
// backend/AGENTS.md's service layer notes for the accepted risk).
@Entity
@Table(name = "room_config", uniqueConstraints = {
    @UniqueConstraint(name = "uk_config_key_building", columnNames = {"config_key", "building_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, length = 50)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    @ManyToOne(optional = true)
    @JoinColumn(name = "building_id")
    private Building building;
}
