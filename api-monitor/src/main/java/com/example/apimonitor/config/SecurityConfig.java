package com.example.apimonitor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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
            // REST API — no CSRF tokens needed
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
                // All other requests (POST mutations) require a valid API key
                auth.anyRequest().authenticated();
            })
            // Return 401 (not 403) for missing/invalid API key — cleaner REST semantics
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            // Allow H2 console to render in an iframe (dev only — no-op in prod)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

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
