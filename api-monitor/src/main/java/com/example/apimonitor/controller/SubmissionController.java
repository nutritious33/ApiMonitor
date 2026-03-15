package com.example.apimonitor.controller;

import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.dto.SubmissionDTO;
import com.example.apimonitor.dto.SubmissionStatusDTO;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import com.example.apimonitor.entity.PendingSubmission;
import com.example.apimonitor.entity.SubmissionStatus;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.repository.PendingSubmissionRepository;
import com.example.apimonitor.service.EmailNotificationService;
import com.example.apimonitor.service.HealthCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Manages the public submission queue.
 *
 * <p>Public endpoints (no auth required):
 * <ul>
 *   <li>{@code POST  /api/submissions}       — submit a URL for review</li>
 *   <li>{@code GET   /api/submissions/{token}} — poll submission status</li>
 * </ul>
 *
 * <p>Admin endpoints (X-API-Key required):
 * <ul>
 *   <li>{@code GET   /api/submissions}           — list all PENDING submissions</li>
 *   <li>{@code POST  /api/submissions/{id}/approve} — approve and start monitoring</li>
 *   <li>{@code POST  /api/submissions/{id}/deny}    — deny a submission</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/submissions")
@Tag(name = "Submissions", description = "Public endpoint submission queue and admin review")
public class SubmissionController {

    private static final Logger log = LoggerFactory.getLogger(SubmissionController.class);

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

    private final PendingSubmissionRepository submissionRepository;
    private final ApiEndpointRepository endpointRepository;
    private final HealthCheckService healthCheckService;
    private final EmailNotificationService emailService;

    public SubmissionController(PendingSubmissionRepository submissionRepository,
                                ApiEndpointRepository endpointRepository,
                                HealthCheckService healthCheckService,
                                EmailNotificationService emailService) {
        this.submissionRepository = submissionRepository;
        this.endpointRepository   = endpointRepository;
        this.healthCheckService   = healthCheckService;
        this.emailService         = emailService;
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    /**
     * Submit a URL for admin review. URL validation runs immediately so the
     * submitter gets fast feedback on invalid/private URLs. If valid, the
     * submission is saved with PENDING status and a unique token is returned
     * for status polling.
     */
    @PostMapping
    @Operation(summary = "Submit a URL for review (public)")
    public ResponseEntity<SubmissionStatusDTO> submit(
            @Valid @RequestBody SubmitRequest request) {

        // Validate URL immediately — rejects private/localhost/non-HTTPS URLs
        healthCheckService.validateUrl(request.url());

        PendingSubmission submission = new PendingSubmission();
        submission.setName(request.name().strip());
        submission.setUrl(request.url());
        submission.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        submission.setSubmissionToken(UUID.randomUUID().toString());

        PendingSubmission saved = submissionRepository.save(submission);
        log.info("New submission id={} name='{}' token={}",
                saved.getId(), sanitizeForLog(saved.getName()), saved.getSubmissionToken());

        // Fire-and-forget email notification (swallows exceptions internally)
        emailService.notifyNewSubmission(saved);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SubmissionStatusDTO.from(saved));
    }

    /**
     * Poll the status of a submission by its token. Public — the numeric id is
     * never revealed to prevent enumeration attacks.
     */
    @GetMapping("/{token}")
    @Operation(summary = "Get submission status by token (public)")
    public ResponseEntity<SubmissionStatusDTO> getStatus(@PathVariable String token) {
        return submissionRepository.findBySubmissionToken(token)
                .map(s -> ResponseEntity.ok(SubmissionStatusDTO.from(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    /**
     * List all PENDING submissions. Requires admin API key.
     */
    @GetMapping
    @Operation(
            summary = "List pending submissions (admin)",
            security = @SecurityRequirement(name = "apiKey"))
    public List<SubmissionDTO> listPending() {
        return submissionRepository
                .findByStatusOrderBySubmittedAtAsc(SubmissionStatus.PENDING)
                .stream()
                .map(SubmissionDTO::from)
                .toList();
    }

    /**
     * Approve a submission: copy it to the monitored endpoints table and kick
     * off an immediate health check. Requires admin API key.
     */
    @PostMapping("/{id}/approve")
    @Operation(
            summary = "Approve a submission (admin)",
            security = @SecurityRequirement(name = "apiKey"))
    public ResponseEntity<ApiEndpointDTO> approve(@PathVariable Long id) {
        PendingSubmission submission = findPendingOrThrow(id);

        // Create a monitored endpoint from the submission
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setName(submission.getName());
        endpoint.setUrl(submission.getUrl());
        endpoint.setIsActive(true);
        endpoint.setSource(ApiEndpointSource.CUSTOM);
        ApiEndpoint saved = endpointRepository.save(endpoint);

        // Update submission status
        submission.setStatus(SubmissionStatus.APPROVED);
        submissionRepository.save(submission);

        log.info("Approved submission id={} name='{}' → endpoint id={}",
                id, sanitizeForLog(submission.getName()), saved.getId());

        // Trigger an immediate health check (async — does not block the response)
        healthCheckService.checkSingleEndpoint(saved);

        return ResponseEntity.ok(ApiEndpointDTO.from(saved));
    }

    /**
     * Deny a submission. Requires admin API key.
     */
    @PostMapping("/{id}/deny")
    @Operation(
            summary = "Deny a submission (admin)",
            security = @SecurityRequirement(name = "apiKey"))
    public ResponseEntity<Void> deny(@PathVariable Long id) {
        PendingSubmission submission = findPendingOrThrow(id);
        submission.setStatus(SubmissionStatus.DENIED);
        submissionRepository.save(submission);
        log.info("Denied submission id={} name='{}'", id, sanitizeForLog(submission.getName()));
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PendingSubmission findPendingOrThrow(Long id) {
        PendingSubmission s = submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Submission not found: " + id));
        if (s.getStatus() != SubmissionStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Submission " + id + " has already been " + s.getStatus().name().toLowerCase());
        }
        return s;
    }

    private static String sanitizeForLog(String value) {
        if (value == null) return null;
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
