package com.example.apimonitor.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AdminSessionStore}.
 * No Spring context — exercises the store directly.
 */
class AdminSessionStoreTest {

    private AdminSessionStore store;

    @BeforeEach
    void setUp() {
        store = new AdminSessionStore();
        store.startCleanup();   // initialise the executor (normally done by @PostConstruct)
    }

    // ── createSession ─────────────────────────────────────────────────────────

    @Test
    void createSession_returnsNonNullToken() {
        String token = store.createSession();
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void createSession_tokenIsImmediatelyValid() {
        String token = store.createSession();
        assertThat(store.isValid(token)).isTrue();
    }

    @Test
    void createSession_eachCallReturnsUniqueToken() {
        String t1 = store.createSession();
        String t2 = store.createSession();
        assertThat(t1).isNotEqualTo(t2);
    }

    // ── isValid ───────────────────────────────────────────────────────────────

    @Test
    void isValid_returnsFalseForNull() {
        assertThat(store.isValid(null)).isFalse();
    }

    @Test
    void isValid_returnsFalseForUnknownToken() {
        assertThat(store.isValid("nonexistent-token")).isFalse();
    }

    @Test
    void isValid_returnsFalseAfterInvalidate() {
        String token = store.createSession();
        store.invalidate(token);
        assertThat(store.isValid(token)).isFalse();
    }

    // ── invalidate ────────────────────────────────────────────────────────────

    @Test
    void invalidate_nullTokenIsNoOp() {
        // Should not throw
        store.invalidate(null);
    }

    @Test
    void invalidate_unknownTokenIsNoOp() {
        // Should not throw
        store.invalidate("no-such-token");
    }

    // ── extendSession ─────────────────────────────────────────────────────────

    @Test
    void extendSession_keepsValidTokenAlive() {
        String token = store.createSession();
        store.extendSession(token)   ;
        assertThat(store.isValid(token)).isTrue();
    }

    @Test
    void extendSession_nullTokenIsNoOp() {
        // Should not throw
        store.extendSession(null);
    }

    @Test
    void extendSession_unknownTokenIsNoOp() {
        // Should not throw; map must not grow
        store.extendSession("ghost-token");
        assertThat(store.isValid("ghost-token")).isFalse();
    }

    // ── cleanupExpired ────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void cleanupExpired_removesExpiredSessions() throws Exception {
        String token = store.createSession();
        assertThat(store.isValid(token)).isTrue();

        // Manually backdate the expiry so the session is already expired
        Field sessionsField = AdminSessionStore.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        ConcurrentHashMap<String, Instant> sessions =
                (ConcurrentHashMap<String, Instant>) sessionsField.get(store);
        sessions.put(token, Instant.now().minusSeconds(1));

        store.cleanupExpired();

        assertThat(store.isValid(token)).isFalse();
    }

    @Test
    void cleanupExpired_doesNotRemoveLiveSessions() {
        String token = store.createSession();
        store.cleanupExpired();
        assertThat(store.isValid(token)).isTrue();
    }
}
