package com.outpass.portal.dto.response;

import com.outpass.portal.model.entity.SecurityGuard;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SecurityGuardSummaryResponse {
    private Long id;
    private String name;
    private String email;
    private String hostel;
    private String phone;
    private boolean enabled;
    private LocalDateTime createdAt;

    public static SecurityGuardSummaryResponse from(SecurityGuard guard) {
        return SecurityGuardSummaryResponse.builder()
                .id(guard.getId())
                .name(guard.getName())
                .email(guard.getEmail())
                .hostel(guard.getHostel())
                .phone(guard.getPhone())
                .enabled(guard.getEnabled() == null || guard.getEnabled())
                .createdAt(guard.getCreatedAt())
                .build();
    }
}
