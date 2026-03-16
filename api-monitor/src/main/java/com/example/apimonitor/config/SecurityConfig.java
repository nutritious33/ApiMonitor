package com.example.apimonitor.config;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
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

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerEnabled;

    @Value("#{'${cors.allowed-origins:http://localhost:8080,http://localhost:5173}'.split(',')}")
    private List<String> allowedOrigins;

    /**
     * Optional: injected when the full application context is loaded.
     * Null in @WebMvcTest slices that do not declare @MockitoBean AdminSessionStore.
     * When null, the cookie auth path in ApiKeyAuthFilter is silently disabled.
     */
    @Autowired(required = false)
    private AdminSessionStore adminSessionStore;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF enabled with cookie-based token repository (XSRF-TOKEN readable by JS).
            // X-API-Key endpoints are CSRF-exempt — custom headers are inherently CSRF-safe.
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers(
                            "/api/health-metrics/**",
                            "/api/submissions/**",
                            "/api/custom-endpoints/**"
                    )
            )
            // CORS — allowCredentials(true) required for the admin_session cookie.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Stateless server-side HTTP sessions
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Authenticate via X-API-Key header or admin_session cookie
            .addFilterBefore(new ApiKeyAuthFilter(apiKey, adminSessionStore),
                    UsernamePasswordAuthenticationFilter.class)
            // Eagerly resolve the deferred CSRF token so XSRF-TOKEN cookie is set on every
            // response (not just on validated POSTs). Required for SPA login flow to work
            // on first attempt — without this the first POST /api/auth/ping gets 403.
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.GET, "/api/health-metrics").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/health-metrics/activate/*").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/health-metrics/deactivate/*").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/health-metrics/deactivate/all").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/submissions").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/submissions/*").permitAll();
                // Auth status — public so the frontend can restore admin mode on page load
                auth.requestMatchers(HttpMethod.GET, "/api/auth/status").permitAll();
                // Logout — public so unauthenticated calls still clear the cookie
                auth.requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll();
                auth.requestMatchers("/", "/index.html", "/assets/**", "/*.js", "/*.css").permitAll();
                if (swaggerEnabled) {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll();
                }
                if (h2ConsoleEnabled) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .headers(headers -> {
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
                headers.httpStrictTransportSecurity(hsts ->
                        hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000));
                headers.referrerPolicy(referrer ->
                        referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
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
        config.setAllowedHeaders(List.of("X-API-Key", "Content-Type", "X-XSRF-TOKEN"));
        // Required so the browser includes the admin_session cookie on cross-origin requests
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
