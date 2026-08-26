package com.outpass.portal.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Admin-configured mapping: which buildings/hostels a student in a given academic year
// (1-4) is allowed to select at registration. A (year, building) pair present here means
// "allowed"; absence means "not allowed" -- there is no wildcard/unconfigured-year fallback,
// so registration must be rejected whenever this table has no matching row.
@Entity
@Table(name = "year_hostel_eligibility", uniqueConstraints = {
    @UniqueConstraint(name = "uk_year_hostel", columnNames = {"year", "building_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YearHostelEligibility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer year;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
