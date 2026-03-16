package com.example.apimonitor.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP rate limiter scoped exclusively to {@code POST /api/auth/ping}.
 *
 * <p>Tracks failed authentication attempts (HTTP 401 responses) in a per-IP
 * sliding window. Once a client accumulates {@value #MAX_FAILURES} failures
 * within any {@value #WINDOW_MINUTES}-minute window, subsequent requests from
 * that IP receive {@code 429 Too Many Requests} immediately — before reaching
 * the {@link ApiKeyAuthFilter} — preventing brute-force attacks from direct
 * HTTP clients that bypass the browser-side JS lockout.
 *
 * <p>IP resolution respects the same {@code rate-limit.trust-proxy} flag used
 * by the global {@link RateLimitFilter}.
 */
@Component
public class AuthPingRateLimiter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthPingRateLimiter.class);

    private static final String AUTH_PING_PATH  = "/api/auth/ping";
    private static final int    MAX_FAILURES     = 10;
    private static final int    WINDOW_MINUTES   = 10;
    private static final long   WINDOW_MS        = TimeUnit.MINUTES.toMillis(WINDOW_MINUTES);

    @Value("${rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    // Per-IP failure tracking: IP → (failureCount, windowStart ms)
    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();
    private record Window(AtomicInteger count, long windowStart) {}

    private ScheduledExecutorService cleanupExecutor;

    @PostConstruct
    void startCleanup() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auth-rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        // Evict stale windows every 10 minutes — aligned with the sliding window
        cleanupExecutor.scheduleAtFixedRate(this::evictStale, WINDOW_MINUTES, WINDOW_MINUTES, TimeUnit.MINUTES);
    }

    @PreDestroy
    void stopCleanup() {
        if (cleanupExecutor != null) { cleanupExecutor.shutdown(); }
    }

    /** Only intercept POST /api/auth/ping; all other requests pass through untouched. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !AUTH_PING_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip  = resolveClientIp(request);
        long   now = System.currentTimeMillis();

        // Pre-check: is this IP already locked out by accumulated failures?
        Window existing = buckets.get(ip);
        if (existing != null
                && existing.windowStart() >= now - WINDOW_MS
                && existing.count().get() >= MAX_FAILURES) {
            log.warn("Auth rate-limit: blocking {} — {} failures in the last {} min", ip, MAX_FAILURES, WINDOW_MINUTES);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\"," +
                    "\"message\":\"Too many failed authentication attempts — try again later\"}");
            return;
        }

        // Wrap the response to capture the status code after the chain completes
        StatusCapturingResponse wrapper = new StatusCapturingResponse(response);
        chain.doFilter(request, wrapper);

        // Record a failure if the auth filter rejected the key
        if (wrapper.getCapturedStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            recordFailure(ip, now);
        }
    }

    private void recordFailure(String ip, long now) {
        long cutoff = now - WINDOW_MS;
        buckets.compute(ip, (key, existing) -> {
            if (existing == null || existing.windowStart() < cutoff) {
                // First failure in a new window
                return new Window(new AtomicInteger(1), now);
            }
            int newCount = existing.count().incrementAndGet();
            if (newCount >= MAX_FAILURES) {
                log.warn("Auth rate-limit: {} reached {} failures in {} min — blocking",
                        ip, MAX_FAILURES, WINDOW_MINUTES);
            }
            return existing;
        });
    }

    private void evictStale() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        buckets.entrySet().removeIf(e -> e.getValue().windowStart() < cutoff);
    }

    /**
     * Resolves the client IP using the same proxy-trust logic as {@link RateLimitFilter}.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (!trustProxy) return request.getRemoteAddr();
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) return cfIp;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    /**
     * Wraps the response to capture the final HTTP status code after the
     * filter chain completes, enabling post-hoc failure recording.
     */
    private static final class StatusCapturingResponse extends HttpServletResponseWrapper {

        private int status = SC_OK;
        StatusCapturingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
            super.setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
            super.sendError(sc, msg);
        }

        int getCapturedStatus() { return status; }
    }
}
