CREATE TABLE IF NOT EXISTS api_endpoint (
    id                BIGSERIAL    PRIMARY KEY,
    name              VARCHAR(255),
    url               VARCHAR(255),
    current_status    VARCHAR(50),
    last_latency_ms   BIGINT,
    last_checked_at   TIMESTAMP,
    total_checks      INTEGER      NOT NULL DEFAULT 0,
    successful_checks INTEGER      NOT NULL DEFAULT 0,
    is_active         BOOLEAN      NOT NULL DEFAULT FALSE
);
