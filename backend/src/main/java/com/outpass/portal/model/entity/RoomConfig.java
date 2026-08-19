package com.outpass.portal.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "room_config", uniqueConstraints = {
    @UniqueConstraint(name = "uk_config_key", columnNames = "config_key")
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
}
