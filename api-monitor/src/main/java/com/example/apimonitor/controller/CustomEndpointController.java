package com.example.apimonitor.controller;

import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.service.CustomEndpointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    private final CustomEndpointService customEndpointService;

    public CustomEndpointController(CustomEndpointService customEndpointService) {
        this.customEndpointService = customEndpointService;
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customEndpointService.addCustomEndpoint(request.name(), request.url()));
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
        customEndpointService.deleteCustomEndpoint(id);
        return ResponseEntity.noContent().build();
    }
}
