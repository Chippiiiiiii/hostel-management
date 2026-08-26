package com.outpass.portal.dto.response;

import com.outpass.portal.model.entity.Warden;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WardenSummaryResponse {
    private Long id;
    private String name;
    private String email;
    private String hostel;
    private String phone;
    private boolean enabled;
    private LocalDateTime createdAt;

    public static WardenSummaryResponse from(Warden warden) {
        return WardenSummaryResponse.builder()
                .id(warden.getId())
                .name(warden.getName())
                .email(warden.getEmail())
                .hostel(warden.getHostel())
                .phone(warden.getPhone())
                .enabled(warden.getEnabled() == null || warden.getEnabled())
                .createdAt(warden.getCreatedAt())
                .build();
    }
}
