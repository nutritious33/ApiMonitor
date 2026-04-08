package com.example.apimonitor.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * CSRF request handler for JavaScript SPA clients.
 *
 * <ul>
 *   <li>{@link #handle} — delegates to {@link XorCsrfTokenRequestAttributeHandler} to set up
 *       the deferred token in the request attributes.  The actual cookie write is performed
 *       by {@link CsrfCookieFilter}, which runs immediately after
 *       {@link org.springframework.security.web.csrf.CsrfFilter}.</li>
 *   <li>{@link #resolveCsrfTokenValue} — uses plain (non-XOR) resolution when the SPA sends
 *       the raw cookie value in the {@code X-XSRF-TOKEN} header; falls back to XOR-decoded
 *       resolution for server-side rendered form {@code _csrf} parameters.</li>
 * </ul>
 *
 * <p>Registered in {@link SecurityConfig}.
 *
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor   = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // SPA sends the raw cookie value in the X-XSRF-TOKEN header → plain resolution.
        // Form submissions use the XOR-encoded _csrf parameter → XOR resolution.
        return (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName())) ? this.plain : this.xor)
                .resolveCsrfTokenValue(request, csrfToken);
    }
}
