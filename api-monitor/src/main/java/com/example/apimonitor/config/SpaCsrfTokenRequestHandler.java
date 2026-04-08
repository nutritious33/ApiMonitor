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
 *   <li>{@link #handle} — calls {@code csrfToken.get()} to eagerly resolve the deferred token so
 *       {@code CookieCsrfTokenRepository} writes the {@code XSRF-TOKEN} cookie on every
 *       response (including GET requests).  Without this the SPA's first POST would get 403
 *       because the client would not yet have received the cookie.</li>
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
        csrfToken.get(); // Forces CookieCsrfTokenRepository to write XSRF-TOKEN cookie.
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // SPA sends the raw cookie value in the X-XSRF-TOKEN header → plain resolution.
        // Form submissions use the XOR-encoded _csrf parameter → XOR resolution.
        return (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName())) ? this.plain : this.xor)
                .resolveCsrfTokenValue(request, csrfToken);
    }
}
