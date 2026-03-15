package com.example.apimonitor.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A user-submitted URL awaiting admin review. The submission is immutable
 * after creation; only {@code status} changes (PENDING → APPROVED or DENIED).
 */
@Entity
@Table(
    name = "pending_submissions",
    indexes = @Index(name = "ux_pending_submissions_token", columnList = "submissionToken", unique = true)
)
public class PendingSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    /** UUID assigned at submission time; shared with the submitter for status polling. */
    @Column(nullable = false, unique = true, length = 36)
    private String submissionToken;

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public SubmissionStatus getStatus() { return status; }
    public void setStatus(SubmissionStatus status) { this.status = status; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getSubmissionToken() { return submissionToken; }
    public void setSubmissionToken(String submissionToken) { this.submissionToken = submissionToken; }
}
