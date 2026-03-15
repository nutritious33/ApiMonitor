package com.example.apimonitor.dto;

import com.example.apimonitor.entity.PendingSubmission;

import java.time.LocalDateTime;

/**
 * Full submission record returned to admin endpoints.
 * The {@link #id} enables approve/deny actions; it is never sent to the public
 * submitter (they only receive {@link SubmissionStatusDTO}).
 */
public record SubmissionDTO(
        Long id,
        String name,
        String url,
        String status,
        LocalDateTime submittedAt,
        String submissionToken
) {
    public static SubmissionDTO from(PendingSubmission s) {
        return new SubmissionDTO(
                s.getId(),
                s.getName(),
                s.getUrl(),
                s.getStatus().name(),
                s.getSubmittedAt(),
                s.getSubmissionToken()
        );
    }
}
