-- Add submitter_ip to track per-IP submission rate for spam protection.
ALTER TABLE pending_submissions
    ADD COLUMN submitter_ip VARCHAR(45) NOT NULL DEFAULT '';
