package com.example.apimonitor.controller;

import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.service.HealthCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/health-metrics")
@Tag(name = "Health Metrics", description = "Manage and query API endpoint health monitoring")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final ApiEndpointRepository apiEndpointRepository;
    private final HealthCheckService healthCheckService;

    public HealthController(ApiEndpointRepository apiEndpointRepository, HealthCheckService healthCheckService) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.healthCheckService = healthCheckService;
    }

    @GetMapping
    @Operation(summary = "List all endpoints", description = "Returns status and metrics for every tracked API endpoint")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved endpoint list")
    public List<ApiEndpointDTO> getHealthMetrics() {
        return apiEndpointRepository.findAll()
                .stream()
                .map(ApiEndpointDTO::from)
                .toList();
    }

    @PostMapping("/activate/{id}")
    @Operation(summary = "Activate monitoring", description = "Starts monitoring for the given endpoint and triggers an immediate health check",
               security = @SecurityRequirement(name = "apiKey"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Endpoint activated"),
            @ApiResponse(responseCode = "404", description = "Endpoint not found")
    })
    public ResponseEntity<ApiEndpointDTO> activateEndpoint(
            @Parameter(description = "Endpoint ID") @PathVariable Long id) {
        ApiEndpoint endpoint = apiEndpointRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Endpoint not found: " + id));
        endpoint.setIsActive(true);
        apiEndpointRepository.save(endpoint);
        healthCheckService.checkSingleEndpoint(endpoint);
        log.info("Activated endpoint id={} name='{}'", endpoint.getId(), endpoint.getName());
        return ResponseEntity.ok(ApiEndpointDTO.from(endpoint));
    }

    @PostMapping("/deactivate/{id}")
    @Operation(summary = "Deactivate monitoring", description = "Stops monitoring for the given endpoint",
               security = @SecurityRequirement(name = "apiKey"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Endpoint deactivated"),
            @ApiResponse(responseCode = "404", description = "Endpoint not found")
    })
    public ResponseEntity<ApiEndpointDTO> deactivateEndpoint(
            @Parameter(description = "Endpoint ID") @PathVariable Long id) {
        ApiEndpoint endpoint = apiEndpointRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Endpoint not found: " + id));
        endpoint.setIsActive(false);
        apiEndpointRepository.save(endpoint);
        log.info("Deactivated endpoint id={} name='{}'", endpoint.getId(), endpoint.getName());
        return ResponseEntity.ok(ApiEndpointDTO.from(endpoint));
    }

    @PostMapping("/deactivate/all")
    @Transactional
    @Operation(summary = "Deactivate all endpoints", description = "Stops monitoring for every currently active endpoint",
               security = @SecurityRequirement(name = "apiKey"))
    @ApiResponse(responseCode = "200", description = "All endpoints deactivated")
    public ResponseEntity<Void> deactivateAllEndpoints() {
        List<ApiEndpoint> active = apiEndpointRepository.findByIsActiveTrue();
        active.forEach(endpoint -> {
            endpoint.setIsActive(false);
            apiEndpointRepository.save(endpoint);
        });
        log.info("Deactivated all {} active endpoint(s)", active.size());
        return ResponseEntity.ok().build();
    }
}
