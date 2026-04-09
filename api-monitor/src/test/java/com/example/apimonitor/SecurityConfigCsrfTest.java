package com.example.apimonitor;

import com.example.apimonitor.config.AdminSessionStore;
import com.example.apimonitor.config.SecurityConfig;
import com.example.apimonitor.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies CSRF behaviour introduced in the httpOnly cookie session feature:
 * <ul>
 *   <li>GET responses on CSRF-protected paths include the {@code XSRF-TOKEN} cookie
 *       (set by {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository}).</li>
 *   <li>POST requests to CSRF-protected paths ({@code /api/auth/**}) are rejected
 *       with 403 when the CSRF token is absent.</li>
 *   <li>POST requests to CSRF-exempt paths ({@code /api/custom-endpoints/**},
 *       {@code /api/health-metrics/**}, {@code /api/submissions/**}) are NOT
 *       rejected for missing CSRF — they return 401 (missing auth) instead of 403.</li>
 * </ul>
 *
 * <p>Uses the {@link AuthController} slice because its paths ({@code /api/auth/**}) are
 * NOT in the {@code ignoringRequestMatchers} list, so {@code CsrfFilter} is active for them.
 * Paths in the ignore list ({@code /api/health-metrics/**} etc.) bypass {@code CsrfFilter}
 * entirely — XSRF-TOKEN would never be set on those responses.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "api.security.key=test-api-key",
    "test.csrf-cookie-isolation=true"
})
class SecurityConfigCsrfTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AdminSessionStore sessionStore;

    // ── XSRF-TOKEN cookie on GET responses ────────────────────────────────────

    @Test
    void getAuthStatus_responseIncludesXsrfTokenCookie() throws Exception {
        // GET /api/auth/status is public and NOT in the CSRF-ignore list.
        // CsrfFilter runs and CookieCsrfTokenRepository sets XSRF-TOKEN.
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    // ── CSRF protection on /api/auth/** ───────────────────────────────────────

    @Test
    void postToAuthPing_withoutCsrfToken_returns403() throws Exception {
        // /api/auth/ping is CSRF-protected — missing token returns 403.
        mockMvc.perform(post("/api/auth/ping")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postToAuthLogout_withoutCsrfToken_returns403() throws Exception {
        // /api/auth/logout is also CSRF-protected — missing token returns 403.
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isForbidden());
    }

    // ── CSRF exemption verification ────────────────────────────────────────────

    @Test
    void postToCustomEndpoints_withoutCsrfToken_returns401NotForbidden() throws Exception {
        // /api/custom-endpoints/** is in ignoringRequestMatchers — missing CSRF is fine.
        // No auth credential supplied → 401 Unauthorized (NOT 403 Forbidden).
        mockMvc.perform(post("/api/custom-endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test","url":"https://example.com"}
                                """))
                .andExpect(status().isUnauthorized());
    }

}
