package com.outpass.portal.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

// Admin-configured mapping: which buildings a warden is assigned to manage. A warden may be
// assigned to several buildings, but Warden.hostel remains the single "primary" hostel that
// every existing warden-scoped runtime query reads (dashboard, outpass, attendance,
// complaints, room management -- see backend/AGENTS.md and service/AGENTS.md). This table is
// the source of truth for the Admin "manage buildings" UI and does not by itself widen a
// warden's runtime data access across multiple hostels.
@Entity
@Table(name = "warden_buildings", uniqueConstraints = {
    @UniqueConstraint(name = "uk_warden_building", columnNames = {"warden_id", "building_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WardenBuilding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "warden_id", nullable = false)
    private Warden warden;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }
}
