package com.outpass.portal.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "tokens", indexes = {
        @Index(name = "idx_revoked_jid", columnList = "jid", unique = true),
        @Index(name = "idx_revoked_expires", columnList = "expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Token {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String jid;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
