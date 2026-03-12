package com.example.apimonitor.dto;

import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;

import java.time.LocalDateTime;

/**
 * API response model for {@link ApiEndpoint}.
 * Decouples the persistence layer from the public API contract.
 */
public record ApiEndpointDTO(
        Long id,
        String name,
        String url,
        String currentStatus,
        Long lastLatencyMs,
        LocalDateTime lastCheckedAt,
        Integer totalChecks,
        Integer successfulChecks,
        Boolean isActive,
        ApiEndpointSource source
) {
    public static ApiEndpointDTO from(ApiEndpoint e) {
        return new ApiEndpointDTO(
                e.getId(),
                e.getName(),
                e.getUrl(),
                e.getCurrentStatus(),
                e.getLastLatencyMs(),
                e.getLastCheckedAt(),
                e.getTotalChecks(),
                e.getSuccessfulChecks(),
                e.getIsActive(),
                e.getSource()
        );
    }
}
