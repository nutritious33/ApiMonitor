package com.example.apimonitor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * Filter that authenticates requests via two paths:
 *
 * <ol>
 *   <li><strong>X-API-Key header</strong> — the original stateless path. The provided
 *       key is compared in constant time against the configured expected key.</li>
 *   <li><strong>{@code admin_session} cookie</strong> — the cookie-based session path.
 *       The token is validated against {@link AdminSessionStore}; if valid the session
 *       expiry is automatically extended (sliding window).</li>
 * </ol>
 *
 * <p>Requests without any matching credential are left unauthenticated; Spring Security
 * then enforces access rules declared in {@link SecurityConfig}.
 *
 * <p>The X-API-Key comparison uses {@link MessageDigest#isEqual} 
 * to prevent timing side-channel attacks.
 *
 * <p>Failed authentication attempts (wrong key supplied) are logged at WARN
 * level to support brute-force detection. The provided key value is never logged.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    static final String API_KEY_HEADER = "X-API-Key";
    static final String SESSION_COOKIE = "admin_session";
    private final String expectedApiKey;

    /**
     * Optional — may be {@code null} in {@code @WebMvcTest} slices that import
     * {@link SecurityConfig} without a full application context.
     * When {@code null} the cookie auth path is silently skipped.
     */
    private final AdminSessionStore sessionStore;

    public ApiKeyAuthFilter(String expectedApiKey, AdminSessionStore sessionStore) {
        this.expectedApiKey = expectedApiKey;
        this.sessionStore   = sessionStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedKey = request.getHeader(API_KEY_HEADER);

        if (providedKey != null) {
            // ── Path 1: X-API-Key header ──────────────────────────────────────
            if (constantTimeEquals(expectedApiKey, providedKey)) {
                setAuthenticated();
            } else {
                // Key was provided but is incorrect — log for brute-force detection.
                // The supplied key value is intentionally omitted from the log.
                log.warn("Authentication failure: invalid API key from {} {} {}",
                        request.getRemoteAddr(),
                        request.getMethod(),
                        request.getRequestURI());
            }

        } else if (sessionStore != null) {
            // ── Path 2: admin_session cookie ──────────────────────────────────
            String token = extractCookie(request, SESSION_COOKIE);
            if (token != null && sessionStore.isValid(token)) {
                sessionStore.extendSession(token);
                setAuthenticated();
            }
            // Token absent or invalid: silent pass-through.
        }
        filterChain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void setAuthenticated() {
        var auth = new UsernamePasswordAuthenticationToken(
                "api-client", null,
                List.of(new SimpleGrantedAuthority("ROLE_API_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** Extracts the value of the named cookie, or {@code null} if absent. */
    static String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Constant-time string comparison using {@link MessageDigest#isEqual}.
     * Prevents timing attacks where an attacker could deduce the correct key
     * by measuring response-time differences for partially-matching keys.
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
