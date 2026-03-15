package com.example.apimonitor.repository;

import com.example.apimonitor.entity.PendingSubmission;
import com.example.apimonitor.entity.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PendingSubmissionRepository extends JpaRepository<PendingSubmission, Long> {

    Optional<PendingSubmission> findBySubmissionToken(String token);
    List<PendingSubmission> findByStatusOrderBySubmittedAtAsc(SubmissionStatus status);
}
