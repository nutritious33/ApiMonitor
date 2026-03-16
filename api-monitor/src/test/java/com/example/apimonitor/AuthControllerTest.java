package com.example.apimonitor;

import com.example.apimonitor.config.AdminSessionStore;
import com.example.apimonitor.config.SecurityConfig;
import com.example.apimonitor.controller.AuthController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link AuthController}.
 *
 * <p>Covers the three auth endpoints:
 * <ul>
 *   <li>{@code POST /api/auth/ping} — validates API key, sets session cookie</li>
 *   <li>{@code GET  /api/auth/status} — public status check</li>
 *   <li>{@code POST /api/auth/logout} — public logout, clears cookie</li>
 * </ul>
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "api.security.key=test-api-key")
class AuthControllerTest {

    static final String VALID_KEY      = "test-api-key";
    static final String API_KEY_HEADER = "X-API-Key";
    static final String SESSION_COOKIE = "admin_session";
    static final String FAKE_TOKEN     = "fake-session-token";

    @Autowired MockMvc mockMvc;

    @MockitoBean AdminSessionStore sessionStore;

    // ── POST /api/auth/ping ───────────────────────────────────────────────────

    @Test
    void ping_withValidApiKey_returns204AndSetsCookie() throws Exception {
        when(sessionStore.createSession()).thenReturn(FAKE_TOKEN);

        mockMvc.perform(post("/api/auth/ping")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("admin_session=" + FAKE_TOKEN)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("HttpOnly")));
    }

    @Test
    void ping_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/ping")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(sessionStore, never()).createSession();
    }

    @Test
    void ping_withWrongApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/ping")
                        .header(API_KEY_HEADER, "wrong-key")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(sessionStore, never()).createSession();
    }

    @Test
    void ping_withoutCsrfToken_returns403() throws Exception {
        // CSRF is enforced on /api/auth/ping — missing token returns 403, not 401
        mockMvc.perform(post("/api/auth/ping")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isForbidden());

        verify(sessionStore, never()).createSession();
    }

    // ── GET /api/auth/status ──────────────────────────────────────────────────

    @Test
    void status_withValidSessionCookie_returnsAdminTrue() throws Exception {
        when(sessionStore.isValid(FAKE_TOKEN)).thenReturn(true);

        mockMvc.perform(get("/api/auth/status")
                        .cookie(new Cookie(SESSION_COOKIE, FAKE_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));

        // extendSession is called by both ApiKeyAuthFilter (cookie auth path) and
        // AuthController.status() itself — so at least once is the correct assertion.
        verify(sessionStore, atLeastOnce()).extendSession(FAKE_TOKEN);
    }

    @Test
    void status_withNoCookie_returnsAdminFalse() throws Exception {
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(false));

        verify(sessionStore, never()).extendSession(anyString());
    }

    @Test
    void status_withInvalidSessionCookie_returnsAdminFalse() throws Exception {
        when(sessionStore.isValid(FAKE_TOKEN)).thenReturn(false);

        mockMvc.perform(get("/api/auth/status")
                        .cookie(new Cookie(SESSION_COOKIE, FAKE_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(false));
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @Test
    void logout_withSession_returns204AndClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie(SESSION_COOKIE, FAKE_TOKEN))
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(sessionStore).invalidate(FAKE_TOKEN);
    }

    @Test
    void logout_withNoSession_returns204AndStillClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(sessionStore, never()).invalidate(anyString());
    }
}
