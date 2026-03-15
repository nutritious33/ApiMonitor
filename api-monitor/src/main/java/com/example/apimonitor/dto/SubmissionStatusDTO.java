package com.example.apimonitor.dto;

import com.example.apimonitor.entity.PendingSubmission;

/**
 * Minimal submission status payload returned to the public submitter.
 * Omits the numeric {@code id} (prevents ID enumeration) and the full URL.
 * The {@code token} is the UUID the submitter received at POST /api/submissions
 * time and uses to poll this endpoint.
 */
public record SubmissionStatusDTO(String token, String name, String status) {
    public static SubmissionStatusDTO from(PendingSubmission s) {
        return new SubmissionStatusDTO(
                s.getSubmissionToken(),
                s.getName(),
                s.getStatus().name()
        );
    }
}
