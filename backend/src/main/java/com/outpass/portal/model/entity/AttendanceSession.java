package com.outpass.portal.model.entity;

import com.outpass.portal.model.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "attendance_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable at the DB/entity level only to accommodate historical rows created before
    // this column existed (see db/backfill-attendance-room-building.sql) -- every new
    // session created by AttendanceService.startSession is required (via a non-null
    // service-layer parameter, not just this annotation) to carry a real building, so no
    // code path in this app can produce a new row with a null building going forward.
    // Tightening this to a DB-level NOT NULL is a deliberately separate follow-up
    // migration once the backfill's "unresolved sessions" report is empty or accepted.
    @ManyToOne(optional = true)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(name = "started_by", nullable = false)
    private Long startedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    @PrePersist
    public void prePersist() {
        this.startedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }
}
