package com.example.apimonitor.repository;

import com.example.apimonitor.entity.PendingSubmission;
import com.example.apimonitor.entity.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PendingSubmissionRepository extends JpaRepository<PendingSubmission, Long> {

    Optional<PendingSubmission> findBySubmissionToken(String token);
    List<PendingSubmission> findByStatusOrderBySubmittedAtAsc(SubmissionStatus status);

    /**
     * Counts PENDING submissions from a specific IP submitted after a given timestamp.
     * Used for per-IP submission spam protection (max 5 pending per 24 h).
     */
    long countBySubmitterIpAndStatusAndSubmittedAtAfter(
            String submitterIp, SubmissionStatus status, LocalDateTime since);

    /**
     * Returns whether a PENDING submission for the given URL already exists.
     * Used to deduplicate identical submissions before they enter the review queue.
     */
    boolean existsByUrlAndStatus(String url, SubmissionStatus status);
}
