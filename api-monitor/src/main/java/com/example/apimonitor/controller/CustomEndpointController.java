package com.example.apimonitor.controller;

import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import com.example.apimonitor.exception.TooManyEndpointsException;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.service.HealthCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/custom-endpoints")
@Tag(name = "Custom Endpoints", description = "User-submitted API endpoints for monitoring")
public class CustomEndpointController {

    private static final Logger log = LoggerFactory.getLogger(CustomEndpointController.class);

    record AddCustomEndpointRequest(
            @NotBlank(message = "name must not be blank")
            @Size(max = 100, message = "name must not exceed 100 characters")
            // Allowlist: letters, digits, spaces, and common punctuation used in API names.
            // Blocks HTML/script chars (<>), SQL-significant chars (' " ; -- =), and null bytes.
            // SQL injection is also independently neutralised by JPA's parameterised queries.
            @Pattern(
                regexp = "^[A-Za-z0-9 .\\-_/:(),]+$",
                message = "name may only contain letters, numbers, spaces, and . - _ / : ( ) ,"
            )
            String name,

            @NotBlank(message = "url must not be blank")
            String url
    ) {}

    private final ApiEndpointRepository apiEndpointRepository;
    private final HealthCheckService healthCheckService;

    @Value("${monitor.max-custom-endpoints:10}")
    private int maxCustomEndpoints;

    public CustomEndpointController(ApiEndpointRepository apiEndpointRepository,
                                    HealthCheckService healthCheckService) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.healthCheckService = healthCheckService;
    }

    @PostMapping
    @Operation(summary = "Add a custom endpoint",
               description = "Submits a user-defined HTTPS URL for monitoring. Global cap of max-custom-endpoints applies.",
               security = @SecurityRequirement(name = "apiKey"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Custom endpoint created"),
            @ApiResponse(responseCode = "400", description = "Invalid URL or request body"),
            @ApiResponse(responseCode = "429", description = "Global custom endpoint cap reached")
    })
    public ResponseEntity<ApiEndpointDTO> addCustomEndpoint(
            @Valid @RequestBody AddCustomEndpointRequest request) {

        long currentCount = apiEndpointRepository.countBySource(ApiEndpointSource.CUSTOM);
        if (currentCount >= maxCustomEndpoints) {
            throw new TooManyEndpointsException(
                    "Custom endpoint limit of " + maxCustomEndpoints + " has been reached");
        }

        // Normalise whitespace before the uniqueness check
        String trimmedName = request.name().strip();

        // Uniqueness check — case-insensitive, across BUILTIN and CUSTOM alike
        if (apiEndpointRepository.findByNameIgnoreCase(trimmedName).isPresent()) {
            throw new IllegalArgumentException(
                    "An endpoint named '" + trimmedName + "' already exists");
        }

        // SSRF + URL length validation (throws IllegalArgumentException → 400)
        healthCheckService.validateUrl(request.url());

        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setName(trimmedName);
        endpoint.setUrl(request.url());
        endpoint.setIsActive(true);
        endpoint.setSource(ApiEndpointSource.CUSTOM);
        ApiEndpoint saved = apiEndpointRepository.save(endpoint);

        // Trigger an immediate health check (async — does not block the response)
        healthCheckService.checkSingleEndpoint(saved);

        log.info("Added custom endpoint id={} name='{}' url='{}'",
                saved.getId(), saved.getName(), saved.getUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEndpointDTO.from(saved));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a custom endpoint",
               description = "Permanently removes a user-submitted endpoint. Only CUSTOM-source endpoints may be deleted.",
               security = @SecurityRequirement(name = "apiKey"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Custom endpoint deleted"),
            @ApiResponse(responseCode = "404", description = "Endpoint not found or is not a custom endpoint")
    })
    public ResponseEntity<Void> deleteCustomEndpoint(
            @Parameter(description = "Endpoint ID") @PathVariable Long id) {

        ApiEndpoint endpoint = apiEndpointRepository.findById(id)
                .filter(e -> ApiEndpointSource.CUSTOM.equals(e.getSource()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Custom endpoint not found: " + id));

        apiEndpointRepository.delete(endpoint);
        log.info("Deleted custom endpoint id={} name='{}'", endpoint.getId(), endpoint.getName());
        return ResponseEntity.noContent().build();
    }
}
