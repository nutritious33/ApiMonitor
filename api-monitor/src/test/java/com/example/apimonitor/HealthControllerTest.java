package com.example.apimonitor;

import com.example.apimonitor.controller.HealthController;
import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.service.HealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.apimonitor.config.SecurityConfig;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Slice test for {@link HealthController} — only the web layer is loaded.
 * Security is included so we can verify that unauthenticated POSTs are rejected.
 */
@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class HealthControllerTest {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String VALID_KEY      = "test-api-key";  // matches application-test.properties

    @Autowired MockMvc mockMvc;

    @MockitoBean ApiEndpointRepository repository;
    @MockitoBean HealthCheckService healthCheckService;

    // ── GET /api/health-metrics ────────────────────────────────────────────

    @Test
    void getHealthMetrics_returnsAllEndpoints() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                makeEndpoint(1L, "GitHub Zen", true),
                makeEndpoint(2L, "Cat Facts", false)
        ));

        mockMvc.perform(get("/api/health-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("GitHub Zen")))
                .andExpect(jsonPath("$[1].id", is(2)));
    }

    @Test
    void getHealthMetrics_returnsEmptyListWhenNoneExist() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/health-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── POST /api/health-metrics/activate/{id} ─────────────────────────────

    @Test
    void activateEndpoint_returnsUpdatedEndpoint() throws Exception {
        ApiEndpoint endpoint = makeEndpoint(1L, "GitHub Zen", false);
        when(repository.findById(1L)).thenReturn(Optional.of(endpoint));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/health-metrics/activate/1")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.isActive", is(true)));

        verify(healthCheckService).checkSingleEndpoint(any());
    }

    @Test
    void activateEndpoint_returns404WhenNotFound() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/health-metrics/activate/999")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void activateEndpoint_returns401WithoutApiKey() throws Exception {
        mockMvc.perform(post("/api/health-metrics/activate/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activateEndpoint_returns401WithWrongApiKey() throws Exception {
        mockMvc.perform(post("/api/health-metrics/activate/1")
                        .header(API_KEY_HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/health-metrics/deactivate/{id} ───────────────────────────

    @Test
    void deactivateEndpoint_returnsUpdatedEndpoint() throws Exception {
        ApiEndpoint endpoint = makeEndpoint(1L, "GitHub Zen", true);
        when(repository.findById(1L)).thenReturn(Optional.of(endpoint));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/health-metrics/deactivate/1")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(false)));
    }

    @Test
    void deactivateEndpoint_returns404WhenNotFound() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/health-metrics/deactivate/42")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/health-metrics/deactivate/all ────────────────────────────

    @Test
    void deactivateAll_deactivatesAllActiveEndpoints() throws Exception {
        ApiEndpoint e1 = makeEndpoint(1L, "A", true);
        ApiEndpoint e2 = makeEndpoint(2L, "B", true);
        when(repository.findByIsActiveTrue()).thenReturn(List.of(e1, e2));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/health-metrics/deactivate/all")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isOk());

        verify(repository, times(2)).save(any());
    }

    @Test
    void deactivateAll_isIdempotentWhenNoneActive() throws Exception {
        when(repository.findByIsActiveTrue()).thenReturn(List.of());

        mockMvc.perform(post("/api/health-metrics/deactivate/all")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isOk());

        verify(repository, never()).save(any());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private ApiEndpoint makeEndpoint(Long id, String name, boolean active) {
        ApiEndpoint e = new ApiEndpoint();
        e.setId(id);
        e.setName(name);
        e.setUrl("https://example.com/" + name.toLowerCase().replace(" ", "-"));
        e.setIsActive(active);
        e.setTotalChecks(0);
        e.setSuccessfulChecks(0);
        return e;
    }
}
