package com.example.apimonitor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the CSRF token to be resolved on every request so that the
 * {@code XSRF-TOKEN} cookie is written to the response before any
 * JavaScript SPA code tries to read it.
 *
 * <p>Without this filter, {@code CookieCsrfTokenRepository} writes the cookie
 * lazily — only when the token is first accessed during CSRF validation (i.e.
 * on POST requests). That means a browser visiting a fresh session would never
 * receive the cookie before its first POST, causing a silent 403.
 *
 * <p>This is the approach recommended by the Spring Security documentation for
 * JavaScript SPA applications:
 * <a href="https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa">
 * CSRF Integration — JavaScript/SPA</a>
 *
 * <p>Registered in {@link SecurityConfig} after {@code BasicAuthenticationFilter}.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        // Accessing the token forces the deferred supplier to resolve,
        // which causes CookieCsrfTokenRepository to write the XSRF-TOKEN cookie.
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
