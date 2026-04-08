package com.example.apimonitor.service;

import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.repository.ApiEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

    @Mock private ApiEndpointRepository repository;
    @Mock private WebClient.Builder webClientBuilder;
    @Mock private WebClient webClient;
    @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private HealthCheckService service;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.build()).thenReturn(webClient);
        service = new HealthCheckService(repository, webClientBuilder);
    }

    // ── validateUrl ────────────────────────────────────────────────────────

    @Test
    void validateUrl_acceptsValidHttpsUrl() {
        service.validateUrl("https://api.github.com/zen");  // must not throw
    }

    @Test
    void validateUrl_rejectsHttpScheme() {
        assertThatThrownBy(() -> service.validateUrl("http://api.github.com/zen"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void validateUrl_rejectsLocalhost() {
        assertThatThrownBy(() -> service.validateUrl("https://localhost/api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private");
    }

    @Test
    void validateUrl_rejects192_168Range() {
        assertThatThrownBy(() -> service.validateUrl("https://192.168.1.100/internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private");
    }

    @Test
    void validateUrl_rejects10xRange() {
        assertThatThrownBy(() -> service.validateUrl("https://10.0.0.1/secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private");
    }

    @Test
    void validateUrl_rejects127xLoopback() {
        assertThatThrownBy(() -> service.validateUrl("https://127.0.0.1/api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Private");
    }

    @Test
    void validateUrl_rejectsMalformedUrl() {
        assertThatThrownBy(() -> service.validateUrl("not-a-url-at-all"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── checkSingleEndpoint ────────────────────────────────────────────────
    // Uses a mocked WebClient so no real HTTP calls are made.
    // Mono.just() / Mono.error() execute the subscribe callbacks synchronously,
    // so assertions can be made immediately after the call.

    @Test
    void checkSingleEndpoint_setsStatusUpOn2xxResponse() {
        stubWebClient(Mono.just(ResponseEntity.ok().<Void>build()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiEndpoint endpoint = validEndpoint();
        service.checkSingleEndpoint(endpoint);

        assertThat(endpoint.getCurrentStatus()).isEqualTo("UP");
        assertThat(endpoint.getSuccessfulChecks()).isEqualTo(1);
        assertThat(endpoint.getTotalChecks()).isEqualTo(1);
        assertThat(endpoint.getLastLatencyMs()).isNotNull();
        verify(repository).save(endpoint);
    }

    @Test
    void checkSingleEndpoint_setsStatusDownOn5xxResponse() {
        ResponseEntity<Void> serverError = ResponseEntity.internalServerError().build();
        stubWebClient(Mono.just(serverError));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiEndpoint endpoint = validEndpoint();
        service.checkSingleEndpoint(endpoint);

        assertThat(endpoint.getCurrentStatus()).isEqualTo("DOWN");
        assertThat(endpoint.getSuccessfulChecks()).isEqualTo(0);
        assertThat(endpoint.getTotalChecks()).isEqualTo(1);
        verify(repository).save(endpoint);
    }

    @Test
    void checkSingleEndpoint_setsStatusDownOnConnectionError() {
        stubWebClient(Mono.error(new RuntimeException("Connection refused")));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiEndpoint endpoint = validEndpoint();
        service.checkSingleEndpoint(endpoint);

        assertThat(endpoint.getCurrentStatus()).isEqualTo("DOWN");
        assertThat(endpoint.getTotalChecks()).isEqualTo(1);
        verify(repository).save(endpoint);
    }

    @Test
    void checkSingleEndpoint_incrementsTotalChecksFromPreviousValue() {
        stubWebClient(Mono.just(ResponseEntity.ok().<Void>build()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiEndpoint endpoint = validEndpoint();
        endpoint.setTotalChecks(9);
        endpoint.setSuccessfulChecks(7);

        service.checkSingleEndpoint(endpoint);

        assertThat(endpoint.getTotalChecks()).isEqualTo(10);
        assertThat(endpoint.getSuccessfulChecks()).isEqualTo(8);
    }

    @Test
    void checkSingleEndpoint_skipsPrivateUrlWithoutMakingRequest() {
        // No WebClient stubbing — if an HTTP call were made the test would fail
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setId(99L);
        endpoint.setName("Internal");
        endpoint.setUrl("https://192.168.0.1/admin");
        endpoint.setTotalChecks(0);
        endpoint.setSuccessfulChecks(0);

        service.checkSingleEndpoint(endpoint);

        // Validation rejected the URL — total checks must remain 0
        assertThat(endpoint.getTotalChecks()).isEqualTo(0);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void stubWebClient(Mono<ResponseEntity<Void>> mono) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(mono);
    }

    private ApiEndpoint validEndpoint() {
        ApiEndpoint e = new ApiEndpoint();
        e.setId(1L);
        e.setName("Test API");
        e.setUrl("https://api.github.com/zen");  // passes SSRF validation
        e.setTotalChecks(0);
        e.setSuccessfulChecks(0);
        return e;
    }
}
