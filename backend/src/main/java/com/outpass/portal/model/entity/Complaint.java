package com.outpass.portal.model.entity;

import com.outpass.portal.model.enums.ComplaintCategory;
import com.outpass.portal.model.enums.ComplaintStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "complaints")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Column(name = "student_roll_no", nullable = false, length = 20)
    private String studentRollNo;

    @Column(name = "hostel", nullable = false, length = 100)
    private String hostel;

    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComplaintCategory category;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(columnDefinition = "LONGTEXT")
    private String photo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ComplaintStatus status = ComplaintStatus.PENDING;

    @Column(name = "warden_response", length = 1000)
    private String wardenResponse;

    @Column(name = "responded_by")
    private Long respondedBy;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }
}
