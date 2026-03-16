package com.example.apimonitor.service;

import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.util.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    // Regex patterns for private/loopback address ranges (SSRF defense).
    // Covers: loopback (localhost, 127.x, ::1, 0.0.0.0), RFC-1918 private ranges
    // (10.x, 172.16-31.x, 192.168.x), and the full link-local range (169.254.x)
    // which includes the AWS/GCP/Azure instance-metadata service at 169.254.169.254.
    private static final List<Pattern> BLOCKED_HOST_PATTERNS = List.of(
            Pattern.compile("^localhost$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^127\\..*"),
            Pattern.compile("^0\\.0\\.0\\.0$"),                       // all-zeros maps to loopback on most OSes
            Pattern.compile("^10\\..*"),
            Pattern.compile("^192\\.168\\..*"),
            Pattern.compile("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"),
            Pattern.compile("^169\\.254\\..*"),                        // link-local / cloud metadata endpoints
            Pattern.compile("^::1$")
    );

    private final ApiEndpointRepository apiEndpointRepository;
    private final WebClient webClient;

    @Value("${monitor.check-interval-ms:60000}")
    private long checkIntervalMs;

    public HealthCheckService(ApiEndpointRepository apiEndpointRepository, WebClient.Builder webClientBuilder) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Validates that a URL is HTTPS, within length limits, and does not target
     * private/internal network ranges (including DNS rebinding defense).
     * Throws IllegalArgumentException if the URL fails validation (SSRF defense).
     */
    public void validateUrl(String url) {
        if (url == null || url.length() > 500) {
            throw new IllegalArgumentException("URL must not exceed 500 characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format: " + url);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only HTTPS URLs are permitted, got: " + url);
        }
        String rawHost = uri.getHost();
        if (rawHost == null || rawHost.isBlank()) {
            throw new IllegalArgumentException("URL is missing a host: " + url);
        }
        // Normalize IPv6 literals: URI.getHost() may return "[::1]" (with brackets) on
        // some JVM versions or "::1" (without) on others. Strip brackets to be consistent.
        String host = (rawHost.startsWith("[") && rawHost.endsWith("]"))
                ? rawHost.substring(1, rawHost.length() - 1)
                : rawHost;

        // Hostname pattern check (fast path — no network I/O)
        for (Pattern blocked : BLOCKED_HOST_PATTERNS) {
            if (blocked.matcher(host).matches()) {
                throw new IllegalArgumentException("Private/internal URLs are not permitted: " + url);
            }
        }
        // DNS rebinding defense: resolve the hostname and verify the resulting IP is not private.
        // Uses Java's built-in InetAddress classification rather than regex on the IP string so
        // that both IPv4 and IPv6 (compressed or expanded notation) are handled correctly.
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress()   // 127.x.x.x / ::1
                        || addr.isSiteLocalAddress()   // 10.x / 172.16-31.x / 192.168.x
                        || addr.isLinkLocalAddress()   // 169.254.x (cloud metadata)
                        || addr.isAnyLocalAddress()) { // 0.0.0.0
                    throw new IllegalArgumentException("URL resolves to a private/internal address: " + url);
                }
            }
        } catch (UnknownHostException e) { throw new IllegalArgumentException("URL hostname could not be resolved: " + host); }
    }

    public void checkSingleEndpoint(ApiEndpoint endpoint) {
        try {
            validateUrl(endpoint.getUrl());
        } catch (IllegalArgumentException e) {
            log.warn("Skipping endpoint id={} name='{}': {}",
                    endpoint.getId(), LogUtil.sanitize(endpoint.getName()), e.getMessage());
            return;
        }

        endpoint.setTotalChecks(endpoint.getTotalChecks() + 1);
        long startTime = System.currentTimeMillis();

        log.debug("Checking endpoint id={} name='{}' url='{}'",
                endpoint.getId(), LogUtil.sanitize(endpoint.getName()), LogUtil.sanitize(endpoint.getUrl()));

        webClient.get()
                .uri(endpoint.getUrl())
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        response -> {
                            long latency = System.currentTimeMillis() - startTime;
                            endpoint.setLastLatencyMs(latency);
                            endpoint.setLastCheckedAt(LocalDateTime.now(ZoneOffset.UTC));
                            if (response.getStatusCode().is2xxSuccessful()) {
                                endpoint.setCurrentStatus("UP");
                                endpoint.setSuccessfulChecks(endpoint.getSuccessfulChecks() + 1);
                                log.debug("Endpoint id={} name='{}' is UP ({}ms)",
                                        endpoint.getId(), LogUtil.sanitize(endpoint.getName()), latency);
                            } else {
                                endpoint.setCurrentStatus("DOWN");
                                log.warn("Endpoint id={} name='{}' is DOWN — HTTP {}",
                                        endpoint.getId(), LogUtil.sanitize(endpoint.getName()), response.getStatusCode());
                            }
                            apiEndpointRepository.save(endpoint);
                        },
                        error -> {
                            long latency = System.currentTimeMillis() - startTime;
                            endpoint.setLastLatencyMs(latency);
                            endpoint.setLastCheckedAt(LocalDateTime.now(ZoneOffset.UTC));
                            endpoint.setCurrentStatus("DOWN");
                            log.warn("Endpoint id={} name='{}' is DOWN — {}",
                                    endpoint.getId(), LogUtil.sanitize(endpoint.getName()), error.getMessage());
                            apiEndpointRepository.save(endpoint);
                        }
                );
    }

    @Scheduled(fixedRateString = "${monitor.check-interval-ms:60000}")
    public void checkAllActiveEndpoints() {
        List<ApiEndpoint> endpoints = apiEndpointRepository.findByIsActiveTrue();
        log.debug("Scheduled check: running health checks for {} active endpoint(s)", endpoints.size());
        for (ApiEndpoint endpoint : endpoints) {
            checkSingleEndpoint(endpoint);
        }
    }
}
