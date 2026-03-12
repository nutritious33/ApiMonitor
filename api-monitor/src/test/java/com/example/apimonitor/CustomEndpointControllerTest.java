package com.example.apimonitor;

import com.example.apimonitor.config.SecurityConfig;
import com.example.apimonitor.controller.CustomEndpointController;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.service.HealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link CustomEndpointController}.
 */
@WebMvcTest(CustomEndpointController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
// Explicitly supply api.security.key so SecurityConfig can inject it regardless
// of how the @WebMvcTest slice context processes profile-specific properties.
@TestPropertySource(properties = "api.security.key=test-api-key")
class CustomEndpointControllerTest {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String VALID_KEY      = "test-api-key";

    @Autowired MockMvc mockMvc;

    @MockitoBean ApiEndpointRepository repository;
    @MockitoBean HealthCheckService healthCheckService;

    // ── POST /api/custom-endpoints ─────────────────────────────────────────

    @Test
    void addCustomEndpoint_returns201WhenValid() throws Exception {
        when(repository.countBySource(ApiEndpointSource.CUSTOM)).thenReturn(0L);
        when(repository.save(any())).thenAnswer(inv -> {
            ApiEndpoint e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });
        doNothing().when(healthCheckService).validateUrl(any());

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My API","url":"https://example.com/health"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.source").value("CUSTOM"));

        verify(healthCheckService).validateUrl("https://example.com/health");
        verify(healthCheckService).checkSingleEndpoint(any());
    }

    @Test
    void addCustomEndpoint_returns401WithoutApiKey() throws Exception {
        mockMvc.perform(post("/api/custom-endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My API","url":"https://example.com/health"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addCustomEndpoint_returns400WhenNameBlank() throws Exception {
        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","url":"https://example.com/health"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addCustomEndpoint_returns400WhenUrlBlank() throws Exception {
        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My API","url":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addCustomEndpoint_returns429WhenCapReached() throws Exception {
        when(repository.countBySource(ApiEndpointSource.CUSTOM)).thenReturn(10L);

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My API","url":"https://example.com/health"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").exists());

        verify(repository, never()).save(any());
    }

    @Test
    void addCustomEndpoint_returns400WhenUrlFailsValidation() throws Exception {
        when(repository.countBySource(ApiEndpointSource.CUSTOM)).thenReturn(0L);
        doThrow(new IllegalArgumentException("Only HTTPS URLs are permitted"))
                .when(healthCheckService).validateUrl(any());

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bad","url":"http://example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only HTTPS URLs are permitted"));
    }

    // ── DELETE /api/custom-endpoints/{id} ──────────────────────────────────

    @Test
    void deleteCustomEndpoint_returns204WhenFound() throws Exception {
        ApiEndpoint e = makeCustomEndpoint(5L, "My API");
        when(repository.findById(5L)).thenReturn(Optional.of(e));

        mockMvc.perform(delete("/api/custom-endpoints/5")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNoContent());

        verify(repository).delete(e);
    }

    @Test
    void deleteCustomEndpoint_returns404WhenNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/custom-endpoints/99")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCustomEndpoint_returns404WhenBuiltin() throws Exception {
        ApiEndpoint builtin = makeCustomEndpoint(1L, "GitHub Zen");
        builtin.setSource(ApiEndpointSource.BUILTIN);
        when(repository.findById(1L)).thenReturn(Optional.of(builtin));

        mockMvc.perform(delete("/api/custom-endpoints/1")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNotFound());

        verify(repository, never()).delete(any());
    }

    @Test
    void deleteCustomEndpoint_returns401WithoutApiKey() throws Exception {
        mockMvc.perform(delete("/api/custom-endpoints/5"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private ApiEndpoint makeCustomEndpoint(Long id, String name) {
        ApiEndpoint e = new ApiEndpoint();
        e.setId(id);
        e.setName(name);
        e.setUrl("https://example.com/" + name.toLowerCase().replace(" ", "-"));
        e.setIsActive(true);
        e.setSource(ApiEndpointSource.CUSTOM);
        e.setTotalChecks(0);
        e.setSuccessfulChecks(0);
        return e;
    }
}
