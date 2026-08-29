package com.outpass.portal.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "floor_departments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_floor_department_building_floor", columnNames = {"building_id", "floor_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FloorDepartment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }
}
