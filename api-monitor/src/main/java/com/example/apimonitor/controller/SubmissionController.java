package com.example.apimonitor.controller;

import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.dto.SubmissionDTO;
import com.example.apimonitor.dto.SubmissionStatusDTO;
import com.example.apimonitor.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Manages the public submission queue.
 *
 * <p>Public endpoints (no auth required):
 * <ul>
 *   <li>{@code POST  /api/submissions}        — submit a URL for review</li>
 *   <li>{@code GET   /api/submissions/{token}} — poll submission status</li>
 * </ul>
 *
 * <p>Admin endpoints (X-API-Key required):
 * <ul>
 *   <li>{@code GET   /api/submissions}              — list all PENDING submissions</li>
 *   <li>{@code POST  /api/submissions/{id}/approve} — approve and start monitoring</li>
 *   <li>{@code POST  /api/submissions/{id}/deny}    — deny a submission</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/api/submissions")
@Tag(name = "Submissions", description = "Public endpoint submission queue and admin review")
public class SubmissionController {

    record SubmitRequest(
            @NotBlank(message = "name must not be blank")
            @Size(max = 100, message = "name must not exceed 100 characters")
            @Pattern(
                regexp = "^[A-Za-z0-9 .\\-_/:(),]+$",
                message = "name may only contain letters, numbers, spaces, and . - _ / : ( ) ,"
            )
            String name,
            @NotBlank(message = "url must not be blank")
            String url
    ) {}

    @Value("${rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /**
     * Resolves the submitter's IP using the same proxy-trust logic as the rate-limit filters.
     * When {@code rate-limit.trust-proxy=true}, the {@code CF-Connecting-IP} header (set by
     * Cloudflare) is checked first, then the leftmost value of {@code X-Forwarded-For}.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (!trustProxy) return request.getRemoteAddr();
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) return cfIp;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Submit a URL for review (public)")
    public ResponseEntity<SubmissionStatusDTO> submit(
            @Valid @RequestBody SubmitRequest request,
            HttpServletRequest httpRequest) {
        String submitterIp = resolveClientIp(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissionService.submit(request.name(), request.url(), submitterIp));
    }

    /**
     * Poll the status of a submission by its token. Public — the numeric id is
     * never revealed to prevent enumeration attacks. The {@code @Size} annotation
     * rejects tokens longer than a UUID (36 chars) with 400 before hitting the DB.
     */
    @GetMapping("/{token}")
    @Operation(summary = "Get submission status by token (public)")
    public ResponseEntity<SubmissionStatusDTO> getStatus(
            @PathVariable @Size(max = 36) String token) {
        return submissionService.getStatus(token)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @GetMapping
    @Operation(
            summary = "List pending submissions (admin)",
            security = @SecurityRequirement(name = "apiKey"))
    public List<SubmissionDTO> listPending() {
        return submissionService.listPending();
    }

    @PostMapping("/{id}/approve")
    @Operation(
            summary = "Approve a submission (admin)",
            security = @SecurityRequirement(name = "apiKey"))
    public ResponseEntity<ApiEndpointDTO> approve(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.approve(id));
    }

    @PostMapping("/{id}/deny")
    @Operation(
            summary = "Deny a submission (admin)",
            security = @SecurityRequirement(name = "apiKey"))
    public ResponseEntity<Void> deny(@PathVariable Long id) {
        submissionService.deny(id);
        return ResponseEntity.noContent().build();
    }
}
