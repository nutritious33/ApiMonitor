CREATE TABLE pending_submissions (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    url              VARCHAR(500) NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    submitted_at     TIMESTAMP    NOT NULL,
    submission_token VARCHAR(36)  NOT NULL
);

CREATE UNIQUE INDEX ux_pending_submissions_token
    ON pending_submissions (submission_token);
