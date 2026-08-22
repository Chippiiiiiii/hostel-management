package com.outpass.portal.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate(){
        this.createdAt = LocalDateTime.now();
    }
}
