package com.outpass.portal.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "students", uniqueConstraints = {@UniqueConstraint(name = "uk_student_email", columnNames = "email")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "roll_no")
    private String rollNo;

    @Column(nullable = false)
    private String department;

    // Academic year of study (1-4). Nullable because existing students predate this field
    // and are not automatically backfilled (see backend/db/backfill-student-year.sql).
    private Integer year;

    @Column(nullable = false)
    private String hostel;

    @Column(name = "room_number", nullable = false)
    private String roomNumber;

    @Column(name = "contact_number", nullable = false)
    private String contactNumber;

    @Column(name = "parent_number", nullable = false)
    private String parentNumber;

    @Column(name = "profile_picture", columnDefinition = "LONGTEXT")
    private String profilePicture;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String gender = "BOY";

    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate(){
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    }
}
