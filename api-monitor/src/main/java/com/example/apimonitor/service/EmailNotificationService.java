package com.example.apimonitor.service;

import com.example.apimonitor.entity.PendingSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends email notifications when a new public submission arrives.
 * Entirely optional — if {@code MAIL_TO} is not configured (empty or missing),
 * all methods return immediately without error. If {@code MAIL_HOST} is not
 * configured, Spring Boot will not create a {@link JavaMailSender} bean and
 * the injected field will be {@code null}; both cases are handled gracefully.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${monitor.mail.to:}")
    private String mailTo;

    @Value("${monitor.mail.from:noreply@apimonitor.local}")
    private String mailFrom;

    /**
     * Sends a notification email for a newly created pending submission.
     * Silently no-ops when mail is not configured or {@code MAIL_TO} is blank.
     * Exceptions during sending are caught and logged at ERROR level so that a
     * transient SMTP failure never causes the HTTP request to fail.
     */
    public void notifyNewSubmission(PendingSubmission submission) {
        if (mailSender == null || mailTo.isBlank()) {
            log.debug("Email notification skipped (mail not configured)");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
            msg.setTo(mailTo);
            msg.setSubject("New API Monitor submission: " + submission.getName());
            msg.setText(String.format(
                    "A new endpoint has been submitted for review.%n%n" +
                    "Name:         %s%n" +
                    "URL:          %s%n" +
                    "Submitted at: %s%n%n" +
                    "Open the admin panel to approve or deny this submission.",
                    submission.getName(),
                    submission.getUrl(),
                    submission.getSubmittedAt()
            ));
            mailSender.send(msg);
            log.info("Submission notification email sent to {}", mailTo);
        } catch (Exception e) {
            // Don't let email failure bubble up and fail the HTTP request
            log.error("Failed to send submission notification email: {}", e.getMessage());
        }
    }
}
