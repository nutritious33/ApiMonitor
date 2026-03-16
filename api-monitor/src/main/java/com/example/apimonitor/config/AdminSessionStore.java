package com.example.apimonitor.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory store for admin session tokens.
 *
 * <p>Each token is a random UUID mapped to an expiry {@link Instant}.
 * The session lifetime is 8 hours from the last activity (sliding window),
 * matching the cookie Max-Age set by {@link AuthPingRateLimiter}.
 *
 * <p><strong>Important:</strong> This is an in-memory store. Sessions are
 * lost on container restart — this is intentional and acceptable. The
 * admin simply logs in again. Persistent sessions would require a database
 * or cache (Redis) which adds operational complexity beyond this project's scope.
 */
@Component
public class AdminSessionStore {

    private static final Logger log = LoggerFactory.getLogger(AdminSessionStore.class);
    /** Session lifetime in seconds, matching the cookie Max-Age. */
    static final long SESSION_DURATION_SECONDS = 8 * 60 * 60; // 8 hours
    /** token → expiry instant */
    private final ConcurrentHashMap<String, Instant> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    @PostConstruct
    void startCleanup() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-store-cleanup");
            t.setDaemon(true);
            return t;
        });
        // Evict expired sessions every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpired, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    void stopCleanup() {
        if (cleanupExecutor != null) { cleanupExecutor.shutdown(); }
    }

    /**
     * Creates a new session and returns its token.
     * The token is a random UUID; the expiry is {@value #SESSION_DURATION_SECONDS} seconds from now.
     */
    public String createSession() {
        String token = UUID.randomUUID().toString();
        sessions.put(token, Instant.now().plusSeconds(SESSION_DURATION_SECONDS));
        log.debug("Admin session created; active sessions: {}", sessions.size());
        return token;
    }

    /**
     * Returns {@code true} if the token exists and has not yet expired.
     */
    public boolean isValid(String token) {
        if (token == null) return false;
        Instant expiry = sessions.get(token);
        return expiry != null && Instant.now().isBefore(expiry);
    }

    /**
     * Removes the token from the store, ending the session immediately.
     * A no-op if the token is not present or already expired.
     */
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
            log.debug("Admin session invalidated; active sessions: {}", sessions.size());
        }
    }

    /**
     * Resets the expiry for an existing valid session to {@value #SESSION_DURATION_SECONDS}
     * seconds from now. A no-op if the token is not present (expired or unknown).
     */
    public void extendSession(String token) {
        if (token != null) {
            sessions.computeIfPresent(token, (k, v) -> Instant.now().plusSeconds(SESSION_DURATION_SECONDS));
        }
    }

    /** Removes all expired entries. Called every 5 minutes by the cleanup executor. */
    void cleanupExpired() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> now.isAfter(e.getValue()));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.debug("Session cleanup: removed {} expired session(s); {} remaining", removed, sessions.size());
        }
    }
}
