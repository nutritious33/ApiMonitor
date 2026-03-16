package com.example.apimonitor.service;

import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.dto.SubmissionDTO;
import com.example.apimonitor.dto.SubmissionStatusDTO;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import com.example.apimonitor.entity.PendingSubmission;
import com.example.apimonitor.entity.SubmissionStatus;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.repository.PendingSubmissionRepository;
import com.example.apimonitor.util.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for the public endpoint-submission queue.
 * <p>Public operations: {@link #submit} and {@link #getStatus}.
 * Admin operations: {@link #listPending}, {@link #approve}, {@link #deny}.
 */
@Service
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    /** Maximum number of PENDING submissions allowed per IP within the rolling window. */
    private static final int  MAX_PENDING_PER_IP = 5;
    /** Rolling window for per-IP submission spam protection (hours). */
    private static final long IP_WINDOW_HOURS    = 24;

    private final PendingSubmissionRepository submissionRepository;
    private final ApiEndpointRepository       endpointRepository;
    private final HealthCheckService          healthCheckService;
    private final EmailNotificationService    emailService;

    public SubmissionService(PendingSubmissionRepository submissionRepository,
                             ApiEndpointRepository endpointRepository,
                             HealthCheckService healthCheckService,
                             EmailNotificationService emailService) {
        this.submissionRepository = submissionRepository;
        this.endpointRepository   = endpointRepository;
        this.healthCheckService   = healthCheckService;
        this.emailService         = emailService;
    }

    // ── Public operations ─────────────────────────────────────────────────────

    /**
     * Validates and stores a new URL submission for admin review.
     * <p>Three pre-save checks run in order:
     * <ol>
     *   <li>URL structure / SSRF validation — rejects private-network / non-HTTPS URLs.</li>
     *   <li>Deduplication — returns 409 if a PENDING submission for the same URL already
     *       exists, preventing duplicate review-queue entries.</li>
     *   <li>Per-IP rate limiting — returns 429 if the submitter's IP already has
     *       {@value #MAX_PENDING_PER_IP} PENDING submissions within the last
     *       {@value #IP_WINDOW_HOURS} hours, preventing spam.</li>
     * </ol>
     * @param name        display name supplied by the submitter
     * @param url         URL to be monitored (must pass SSRF validation)
     * @param submitterIp client IP address for spam-rate-limiting
     */
    public SubmissionStatusDTO submit(String name, String url, String submitterIp) {
        // URL validation — rejects private/localhost/non-HTTPS URLs
        healthCheckService.validateUrl(url);

        // Deduplication — reject if an identical PENDING submission already exists
        if (submissionRepository.existsByUrlAndStatus(url, SubmissionStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending submission for this URL already exists");
        }

        // Per-IP spam protection — max MAX_PENDING_PER_IP submissions per IP_WINDOW_HOURS
        LocalDateTime windowStart = LocalDateTime.now(ZoneOffset.UTC)
                .minusHours(IP_WINDOW_HOURS);
        long pendingFromIp = submissionRepository.countBySubmitterIpAndStatusAndSubmittedAtAfter(
                submitterIp, SubmissionStatus.PENDING, windowStart);
        if (pendingFromIp >= MAX_PENDING_PER_IP) {
            log.warn("Submission rate-limit: {} has {} pending submissions in the last {} h",
                    LogUtil.sanitize(submitterIp), pendingFromIp, IP_WINDOW_HOURS);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many pending submissions — please wait for your existing submissions to be reviewed");
        }

        PendingSubmission submission = new PendingSubmission();
        submission.setName(name.strip());
        submission.setUrl(url);
        submission.setSubmitterIp(submitterIp);
        submission.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        submission.setSubmissionToken(UUID.randomUUID().toString());

        PendingSubmission saved = submissionRepository.save(submission);
        log.info("New submission id={} name='{}' token={} ip={}",
                saved.getId(), LogUtil.sanitize(saved.getName()),
                saved.getSubmissionToken(), LogUtil.sanitize(submitterIp));

        // Fire-and-forget email notification (swallows exceptions internally)
        emailService.notifyNewSubmission(saved);

        return SubmissionStatusDTO.from(saved);
    }

    /**
     * Returns the status of a submission by its opaque UUID token.
     * Returns empty when the token is unknown (controller renders 404).
     */
    public Optional<SubmissionStatusDTO> getStatus(String token) {
        return submissionRepository.findBySubmissionToken(token)
                .map(SubmissionStatusDTO::from);
    }

    // ── Admin operations ──────────────────────────────────────────────────────

    /** Returns all PENDING submissions in ascending submission-time order. */
    public List<SubmissionDTO> listPending() {
        return submissionRepository
                .findByStatusOrderBySubmittedAtAsc(SubmissionStatus.PENDING)
                .stream()
                .map(SubmissionDTO::from)
                .toList();
    }

    /**
     * Approves a submission: re-validates the submitted URL, creates a monitored
     * endpoint, and marks the submission APPROVED. All writes are in a single
     * transaction so they commit atomically or roll back together.
     *
     * <p>If the URL no longer passes validation (e.g. it resolved to a private
     * address when first submitted via a public IP that has since changed), the
     * submission is marked DENIED and a 422 Unprocessable Entity is returned.
     * The DENIED status is committed inside the same transaction.
     */
    @Transactional
    public ApiEndpointDTO approve(Long id) {
        PendingSubmission submission = findPendingOrThrow(id);

        // Re-validate URL at approval time to catch DNS-rebinding or
        // stale submissions that now point to private infrastructure.
        try {
            healthCheckService.validateUrl(submission.getUrl());
        } catch (IllegalArgumentException ex) {
            submission.setStatus(SubmissionStatus.DENIED);
            submissionRepository.save(submission);
            log.warn("Approve id={}: URL '{}' failed re-validation — auto-denied: {}",
                    id, LogUtil.sanitize(submission.getUrl()), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Submission URL is no longer valid and has been denied: " + ex.getMessage());
        }

        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setName(submission.getName());
        endpoint.setUrl(submission.getUrl());
        endpoint.setIsActive(true);
        endpoint.setSource(ApiEndpointSource.CUSTOM);
        ApiEndpoint saved = endpointRepository.save(endpoint);

        submission.setStatus(SubmissionStatus.APPROVED);
        submissionRepository.save(submission);

        log.info("Approved submission id={} name='{}' → endpoint id={}",
                id, LogUtil.sanitize(submission.getName()), saved.getId());

        // Trigger an immediate health check (async — does not block the response)
        healthCheckService.checkSingleEndpoint(saved);

        return ApiEndpointDTO.from(saved);
    }

    /** Denies a submission and marks it DENIED. */
    @Transactional
    public void deny(Long id) {
        PendingSubmission submission = findPendingOrThrow(id);
        submission.setStatus(SubmissionStatus.DENIED);
        submissionRepository.save(submission);
        log.info("Denied submission id={} name='{}'", id, LogUtil.sanitize(submission.getName()));
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
}
