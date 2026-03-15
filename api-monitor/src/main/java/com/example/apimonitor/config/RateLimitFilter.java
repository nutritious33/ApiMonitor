package com.example.apimonitor.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP sliding-window rate limiter.
 *
 * <p>Limits each client to {@code rate-limit.requests-per-minute} requests within
 * any rolling 60-second window. Cloudflare Tunnel WAF rules are the primary edge
 * defence for external traffic; this filter protects direct or internal access.
 *
 * <p>IP resolution is controlled by {@code rate-limit.trust-proxy}:
 * <ul>
 *   <li>{@code false} (default) — always use the raw TCP remote address. Proxy
 *       headers ({@code CF-Connecting-IP}, {@code X-Forwarded-For}) are ignored,
 *       making header-based IP spoofing impossible.</li>
 *   <li>{@code true} — trust {@code CF-Connecting-IP} then {@code X-Forwarded-For}.
 *       Enable this in production <em>only</em> when port 8080 is NOT published on
 *       the Docker host and all traffic arrives exclusively through Cloudflare Tunnel,
 *       which is the only party able to set {@code CF-Connecting-IP}.</li>
 * </ul>
 *
 * <p>A daemon background thread evicts stale window entries once per minute to
 * prevent unbounded memory growth.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    @Value("${rate-limit.requests-per-minute:120}")
    private int maxRequestsPerWindow;

    /**
     * When {@code true}, proxy headers are trusted for client-IP resolution.
     * Must only be {@code true} when the app is not directly reachable (port 8080
     * not published on the host), so clients cannot forge these headers.
     */
    @Value("${rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();
    private record Window(AtomicInteger count, long windowStart) {}

    @PostConstruct
    void startCleanup() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(this::evictStale, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        long now = System.currentTimeMillis();
        long windowCutoff = now - WINDOW_MS;

        Window window = buckets.compute(ip, (key, existing) -> {
            if (existing == null || existing.windowStart() < windowCutoff) {
                return new Window(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (window.count().get() > maxRequestsPerWindow) {
            log.warn("Rate limit exceeded for client {}", ip);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\"," +
                    "\"message\":\"Rate limit exceeded — please slow down\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void evictStale() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        int removed = 0;
        for (Map.Entry<String, Window> entry : buckets.entrySet()) {
            if (entry.getValue().windowStart() < cutoff) {
                buckets.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Rate-limit map: evicted {} stale entries, {} remaining", removed, buckets.size());
        }
    }

    /**
     * Resolves the client IP according to the {@code rate-limit.trust-proxy} setting.
     *
     * <p>When {@code trustProxy} is {@code false} (the default), only the raw TCP
     * remote address is used — headers cannot be spoofed regardless of network
     * topology. When {@code trustProxy} is {@code true}, {@code CF-Connecting-IP}
     * is tried first (set by Cloudflare's infrastructure), then the first value of
     * {@code X-Forwarded-For}, then the remote address as a final fallback.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (!trustProxy) return request.getRemoteAddr();
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) return cfIp;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
