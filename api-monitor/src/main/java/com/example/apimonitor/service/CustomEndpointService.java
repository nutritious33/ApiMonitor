package com.example.apimonitor.service;

import com.example.apimonitor.dto.ApiEndpointDTO;
import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import com.example.apimonitor.exception.TooManyEndpointsException;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.util.LogUtil;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Business logic for managing custom (user-submitted) monitored endpoints.
 *
 * <p>The {@link #addCustomEndpoint} method is {@code synchronized} to eliminate the
 * TOCTOU race between the cap count-check and the save: without synchronisation,
 * concurrent requests could all read a count below the cap and all proceed to insert,
 * exceeding the {@code monitor.max-custom-endpoints} limit.
 */
@Service
public class CustomEndpointService {

    private static final Logger log = LoggerFactory.getLogger(CustomEndpointService.class);

    private final ApiEndpointRepository apiEndpointRepository;
    private final HealthCheckService healthCheckService;

    @Value("${monitor.max-custom-endpoints:10}")
    private int maxCustomEndpoints;

    public CustomEndpointService(ApiEndpointRepository apiEndpointRepository,
                                  HealthCheckService healthCheckService) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.healthCheckService    = healthCheckService;
    }

    /**
     * Adds a custom HTTPS endpoint to the monitoring pool.
     * @param name user-supplied display name (already validated by the controller)
     * @param url  target URL (already validated by the controller for non-blank)
     * @return the persisted endpoint as a DTO
     * @throws TooManyEndpointsException if the global custom-endpoint cap is reached
     * @throws IllegalArgumentException  if the URL fails SSRF validation or the name is a duplicate
     */
    public synchronized ApiEndpointDTO addCustomEndpoint(String name, String url) {
        long currentCount = apiEndpointRepository.countBySource(ApiEndpointSource.CUSTOM);
        if (currentCount >= maxCustomEndpoints) {
            throw new TooManyEndpointsException(
                    "Custom endpoint limit of " + maxCustomEndpoints + " has been reached");
        }

        String trimmedName = name.strip();

        // Uniqueness check — case-insensitive, across BUILTIN and CUSTOM alike
        if (apiEndpointRepository.findByNameIgnoreCase(trimmedName).isPresent()) {
            throw new IllegalArgumentException(
                    "An endpoint named '" + trimmedName + "' already exists");
        }

        // SSRF + URL length validation (throws IllegalArgumentException → 400)
        healthCheckService.validateUrl(url);

        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setName(trimmedName);
        endpoint.setUrl(url);
        endpoint.setIsActive(true);
        endpoint.setSource(ApiEndpointSource.CUSTOM);
        ApiEndpoint saved = apiEndpointRepository.save(endpoint);

        // Trigger an immediate health check (async — does not block the response)
        healthCheckService.checkSingleEndpoint(saved);

        log.info("Added custom endpoint id={} name='{}' url='{}'",
                saved.getId(), LogUtil.sanitize(saved.getName()), LogUtil.sanitize(saved.getUrl()));
        return ApiEndpointDTO.from(saved);
    }

    /**
     * Permanently removes a CUSTOM-source endpoint.
     * @throws EntityNotFoundException if the id does not exist or refers to a BUILTIN endpoint
     */
    public void deleteCustomEndpoint(Long id) {
        ApiEndpoint endpoint = apiEndpointRepository.findById(id)
                .filter(e -> ApiEndpointSource.CUSTOM.equals(e.getSource()))
                .orElseThrow(() -> new EntityNotFoundException("Custom endpoint not found: " + id));

        apiEndpointRepository.delete(endpoint);
        log.info("Deleted custom endpoint id={} name='{}'",
                endpoint.getId(), LogUtil.sanitize(endpoint.getName()));
    }
}
