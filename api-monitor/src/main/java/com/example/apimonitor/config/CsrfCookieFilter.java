package com.example.apimonitor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Eagerly resolves the deferred CSRF token on every request so that
 * {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository}
 * writes the {@code XSRF-TOKEN} cookie before the response is committed.
 *
 * <p>Registered after {@link org.springframework.security.web.csrf.CsrfFilter}
 * in the security filter chain. This is the pattern recommended by the Spring
 * Security SPA docs for Spring Security 6.5+.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Calling getToken() forces the deferred token to initialise,
            // causing CookieCsrfTokenRepository to write the XSRF-TOKEN cookie.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
