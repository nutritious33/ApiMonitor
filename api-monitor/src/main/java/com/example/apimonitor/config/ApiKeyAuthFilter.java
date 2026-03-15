package com.example.apimonitor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
import java.util.List;

/**
 * Filter that authenticates requests carrying a valid X-API-Key header.
 * Requests without a matching key are left unauthenticated; Spring Security
 * then enforces access rules declared in {@link SecurityConfig}.
 *
 * <p>The key comparison uses {@link MessageDigest#isEqual} rather than
 * {@link String#equals} to prevent timing side-channel attacks: isEqual always
 * iterates over all bytes regardless of where a mismatch occurs.
 *
 * <p>Failed authentication attempts (wrong key supplied) are logged at WARN
 * level to support brute-force detection and incident investigation. The
 * provided key value is never written to logs.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    static final String API_KEY_HEADER = "X-API-Key";
    private final String expectedApiKey;

    public ApiKeyAuthFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey != null) {
            if (constantTimeEquals(expectedApiKey, providedKey)) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "api-client", null,
                        List.of(new SimpleGrantedAuthority("ROLE_API_USER"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                // Key was provided but is incorrect — log for brute-force detection.
                // The supplied key value is intentionally omitted from the log.
                log.warn("Authentication failure: invalid API key from {} {} {}",
                        request.getRemoteAddr(),
                        request.getMethod(),
                        request.getRequestURI());
            }
        }
        // No key provided: silent pass-through. Spring Security enforces authorization
        // rules downstream (public GETs are permitted; protected endpoints return 401).
        filterChain.doFilter(request, response);
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
