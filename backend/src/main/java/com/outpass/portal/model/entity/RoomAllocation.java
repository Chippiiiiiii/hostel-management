package com.outpass.portal.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_allocations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_allocation_email", columnNames = "student_email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Column(name = "student_roll_no", nullable = false, length = 20)
    private String studentRollNo;

    @Column(name = "student_department", nullable = false, length = 100)
    private String studentDepartment;

    @Column(name = "student_email", nullable = false)
    private String studentEmail;

    @Column(name = "allocated_at", updatable = false)
    private LocalDateTime allocatedAt;

    @PrePersist
    public void prePersist() {
        this.allocatedAt = LocalDateTime.now();
    }
}
