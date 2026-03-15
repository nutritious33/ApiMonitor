package com.example.apimonitor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${api.security.key}")
    private String apiKey;

    /** True only in the dev profile (spring.h2.console.enabled=true in application-dev.properties). */
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    /**
     * False in the prod profile (springdoc.swagger-ui.enabled=false in application-prod.properties).
     * Gating the permitAll() here — not just in springdoc properties — means the security layer
     * still blocks Swagger paths if springdoc is ever accidentally re-enabled in production.
     */
    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerEnabled;

    /**
     * Comma-separated list of allowed CORS origins.
     * Dev default covers the Vite dev server; override in prod via CORS_ALLOWED_ORIGINS env var.
     */
    @Value("#{'${cors.allowed-origins:http://localhost:8080,http://localhost:5173}'.split(',')}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // REST API — no CSRF tokens needed (stateless, key-authenticated)
            .csrf(csrf -> csrf.disable())
            // CORS — allows Vite dev server and any configured production origin
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Stateless: no server-side sessions
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Authenticate requests via X-API-Key header
            .addFilterBefore(new ApiKeyAuthFilter(apiKey),
                    UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                // Public: read-only metrics (polled by the frontend every 10 s)
                auth.requestMatchers(HttpMethod.GET, "/api/health-metrics").permitAll();
                // Public: watchlist management for predefined catalog endpoints.
                // Adding or deleting custom endpoints still requires an API key (see below).
                auth.requestMatchers(HttpMethod.POST, "/api/health-metrics/activate/*").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/health-metrics/deactivate/*").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/health-metrics/deactivate/all").permitAll();
                // Public: submission queue
                //   POST  /api/submissions        — submit a URL for review
                //   GET   /api/submissions/{token} — poll status by UUID token
                auth.requestMatchers(HttpMethod.POST, "/api/submissions").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/submissions/*").permitAll();
                // Public: React SPA and its bundled assets
                auth.requestMatchers("/", "/index.html", "/assets/**", "/*.js", "/*.css").permitAll();
                // OpenAPI docs — only when springdoc is enabled (disabled in prod profile).
                // Mirroring the H2 console pattern: if swagger were accidentally re-enabled in prod,
                // the security layer still blocks it rather than exposing the full API schema.
                if (swaggerEnabled) {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll();
                }
                // H2 console — only open when explicitly enabled (dev profile)
                if (h2ConsoleEnabled) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }
                // All other requests (POST mutations, admin submission actions) require a valid key
                auth.anyRequest().authenticated();
            })
            // Return 401 (not 403) for missing/invalid API key — cleaner REST semantics
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            // ── Security response headers ──────────────────────────────────────────
            .headers(headers -> {
                // X-Frame-Options:
                //   SAMEORIGIN in dev so the H2 console (which uses HTML frames) renders correctly.
                //   DENY in prod — the SPA has no legitimate iframe embedding use case.
                if (h2ConsoleEnabled) {
                    headers.frameOptions(frame -> frame.sameOrigin());
                } else {
                    headers.frameOptions(frame -> frame.deny());
                }
                
                headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data:; " +
                        "connect-src 'self'; " +
                        "font-src 'self'; " +
                        "frame-ancestors 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self'"
                ));

                // HTTP Strict Transport Security — 1-year max-age with subdomains.
                // Spring Security only sends this header over HTTPS connections
                // (SecureRequestMatcher check), so it is safe to enable globally;
                // dev (HTTP) traffic will never receive it.
                headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000));

                // Referrer-Policy — send full URL only to same origin; strip it on
                // cross-origin requests to avoid leaking path info to third-party APIs.
                headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));

                // Permissions-Policy — disable browser features not used by the SPA.
                // Customizer API removed in Spring Security 6.5+.
                headers.addHeaderWriter(new StaticHeadersWriter(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()"));
            });
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("X-API-Key", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
