package com.example.apimonitor;

import com.example.apimonitor.config.SecurityConfig;
import com.example.apimonitor.controller.CustomEndpointController;
import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import com.example.apimonitor.exception.TooManyEndpointsException;
import com.example.apimonitor.service.CustomEndpointService;
import jakarta.persistence.EntityNotFoundException;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link CustomEndpointController}.
 * Business logic is owned by {@link CustomEndpointService}, which is mocked here.
 */
@WebMvcTest(CustomEndpointController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "api.security.key=test-api-key")
class CustomEndpointControllerTest {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String VALID_KEY      = "test-api-key";

    @Autowired MockMvc mockMvc;

    @MockitoBean CustomEndpointService customEndpointService;

    // ── POST /api/custom-endpoints ─────────────────────────────────────────

    @Test
    void addCustomEndpoint_returns201WhenValid() throws Exception {
        ApiEndpointDTO dto = ApiEndpointDTO.from(makeCustomEndpoint(99L, "My API"));
        when(customEndpointService.addCustomEndpoint(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My API","url":"https://example.com/health"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.source").value("CUSTOM"));

        verify(customEndpointService).addCustomEndpoint("My API", "https://example.com/health");
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
        when(customEndpointService.addCustomEndpoint(any(), any()))
                .thenThrow(new TooManyEndpointsException("Custom endpoint limit of 10 has been reached"));

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My API","url":"https://example.com/health"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").exists());

        verify(customEndpointService).addCustomEndpoint(any(), any());
    }

    @Test
    void addCustomEndpoint_returns400WhenUrlFailsValidation() throws Exception {
        when(customEndpointService.addCustomEndpoint(any(), any()))
                .thenThrow(new IllegalArgumentException("Only HTTPS URLs are permitted"));

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
        doNothing().when(customEndpointService).deleteCustomEndpoint(5L);

        mockMvc.perform(delete("/api/custom-endpoints/5")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNoContent());

        verify(customEndpointService).deleteCustomEndpoint(5L);
    }

    @Test
    void deleteCustomEndpoint_returns404WhenNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Custom endpoint not found: 99"))
                .when(customEndpointService).deleteCustomEndpoint(99L);

        mockMvc.perform(delete("/api/custom-endpoints/99")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCustomEndpoint_returns404WhenBuiltin() throws Exception {
        doThrow(new EntityNotFoundException("Custom endpoint not found: 1"))
                .when(customEndpointService).deleteCustomEndpoint(1L);

        mockMvc.perform(delete("/api/custom-endpoints/1")
                        .header(API_KEY_HEADER, VALID_KEY))
                .andExpect(status().isNotFound());
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
