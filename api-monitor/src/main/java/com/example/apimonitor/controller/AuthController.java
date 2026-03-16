package com.example.apimonitor.controller;

import com.example.apimonitor.config.AdminSessionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication endpoints: login ping, session status, and logout.
 * <h3>Session lifecycle</h3>
 * <ol>
 *   <li>Client POSTs to {@code /api/auth/ping} with an {@code X-API-Key} header.</li>
 *   <li>The request is pre-authenticated by {@link com.example.apimonitor.config.ApiKeyAuthFilter};
 *       if the key is invalid, a 401 is returned before this controller is reached.</li>
 *   <li>On success, this controller creates a session token via {@link AdminSessionStore},
 *       stores it with an 8-hour expiry, and sets an {@code admin_session} httpOnly cookie.</li>
 *   <li>Subsequent admin requests are authenticated by the cookie via
 *       {@link com.example.apimonitor.config.ApiKeyAuthFilter}; the session expiry is
 *       automatically extended on each valid cookie request.</li>
 *   <li>Client may call {@code GET /api/auth/status} to check session validity (used by the
 *       frontend on page load to restore admin mode without storing the key in localStorage).</li>
 *   <li>Client calls {@code POST /api/auth/logout} to explicitly end the session.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Admin authentication and session management")
public class AuthController {

    static final String SESSION_COOKIE = "admin_session";
    static final long   MAX_AGE        = 8 * 60 * 60; // 8 hours

    private final AdminSessionStore sessionStore;

    /**
     * When {@code true}, the {@code Secure} attribute is added to the cookie.
     * Set to {@code false} in dev/test (HTTP) and {@code true} in prod (HTTPS).
     */
    @Value("${admin.session.secure-cookie:false}")
    private boolean secureCookie;

    public AuthController(AdminSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * Key-validation ping used by the admin login modal.
     *
     * <p>The {@code X-API-Key} header is validated by
     * {@link com.example.apimonitor.config.ApiKeyAuthFilter} before this method is reached.
     * Reaching this method guarantees the key is valid.
     *
     * <p>On success: creates a server-side session, sets an {@code admin_session} httpOnly
     * cookie, and returns {@code 204 No Content}.
     */
    @PostMapping("/ping")
    @Operation(summary = "Validate API key and establish a session cookie (admin)")
    public ResponseEntity<Void> ping(HttpServletResponse response) {
        String token = sessionStore.createSession();
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, MAX_AGE).toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the admin status of the current request.
     *
     * <p>Reads the {@code admin_session} cookie and checks it against
     * {@link AdminSessionStore}. This endpoint is public — no credential required.
     * It is called by the frontend on every page load to restore the admin UI without
     * keeping the API key in browser-accessible storage.
     *
     * @return {@code {"admin": true}} if the session is valid, {@code {"admin": false}} otherwise
     */
    @GetMapping("/status")
    @Operation(summary = "Check if the current request has a valid admin session (public)")
    public ResponseEntity<Map<String, Boolean>> status(
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionToken) {
        boolean isAdmin = sessionStore.isValid(sessionToken);
        if (isAdmin) {
            sessionStore.extendSession(sessionToken);
        }
        return ResponseEntity.ok(Map.of("admin", isAdmin));
    }

    /**
     * Ends the admin session.
     *
     * <p>Removes the session token from {@link AdminSessionStore} and clears the
     * {@code admin_session} cookie. This endpoint is public — it can be called even
     * without a valid session to ensure the cookie is cleared client-side.
     *
     * @return {@code 204 No Content}
     */
    @PostMapping("/logout")
    @Operation(summary = "Invalidate the admin session and clear the cookie (public)")
    public ResponseEntity<Void> logout(
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionToken,
            HttpServletResponse response) {
        if (sessionToken != null) {
            sessionStore.invalidate(sessionToken);
        }
        // Clear the cookie by setting Max-Age=0
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
        return ResponseEntity.noContent().build();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseCookie buildCookie(String value, long maxAge) {
        return ResponseCookie.from(SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
