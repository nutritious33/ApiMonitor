package com.example.apimonitor;

import com.example.apimonitor.config.AdminSessionStore;
import com.example.apimonitor.config.SecurityConfig;
import com.example.apimonitor.controller.CustomEndpointController;
import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import com.example.apimonitor.service.CustomEndpointService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that the {@code admin_session} cookie path in {@link com.example.apimonitor.config.ApiKeyAuthFilter}
 * correctly authenticates requests against a mocked {@link AdminSessionStore}.
 *
 * <p>Uses the {@link CustomEndpointController} slice (admin-only POST) as the protected
 * endpoint under test — if the cookie auth works, the request is authenticated and the
 * controller returns 201; if not, Spring Security returns 401.
 */
@WebMvcTest(CustomEndpointController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "api.security.key=test-api-key")
class ApiKeyAuthFilterCookieTest {

    static final String SESSION_COOKIE = "admin_session";
    static final String VALID_TOKEN    = "valid-session-token";
    static final String INVALID_TOKEN  = "expired-session-token";

    @Autowired MockMvc mockMvc;

    @MockitoBean AdminSessionStore sessionStore;
    @MockitoBean CustomEndpointService customEndpointService;

    // ── Cookie authenticates a protected endpoint ─────────────────────────────

    @Test
    void validSessionCookie_authenticatesProtectedEndpoint() throws Exception {
        when(sessionStore.isValid(VALID_TOKEN)).thenReturn(true);
        ApiEndpointDTO dto = ApiEndpointDTO.from(makeEndpoint(1L, "My API"));
        when(customEndpointService.addCustomEndpoint(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/custom-endpoints")
                        .cookie(new Cookie(SESSION_COOKIE, VALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My API","url":"https://example.com"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidSessionCookie_returns401() throws Exception {
        when(sessionStore.isValid(INVALID_TOKEN)).thenReturn(false);

        mockMvc.perform(post("/api/custom-endpoints")
                        .cookie(new Cookie(SESSION_COOKIE, INVALID_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My API","url":"https://example.com"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/custom-endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My API","url":"https://example.com"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validApiKeyHeader_stillAuthenticatesWhenCookieAbsent() throws Exception {
        ApiEndpointDTO dto = ApiEndpointDTO.from(makeEndpoint(2L, "Header API"));
        when(customEndpointService.addCustomEndpoint(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/custom-endpoints")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Header API","url":"https://example.com"}
                                """))
                .andExpect(status().isCreated());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ApiEndpoint makeEndpoint(Long id, String name) {
        ApiEndpoint e = new ApiEndpoint();
        e.setId(id);
        e.setName(name);
        e.setUrl("https://example.com");
        e.setSource(ApiEndpointSource.CUSTOM);
        return e;
    }
}
