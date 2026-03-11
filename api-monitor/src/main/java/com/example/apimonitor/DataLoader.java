package com.example.apimonitor;

import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.service.HealthCheckService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    /** Lightweight projection for deserialising endpoints.json */
    record EndpointConfig(String name, String url, boolean active) {}

    private final ApiEndpointRepository apiEndpointRepository;
    private final HealthCheckService healthCheckService;

    public DataLoader(ApiEndpointRepository apiEndpointRepository, HealthCheckService healthCheckService) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.healthCheckService = healthCheckService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (apiEndpointRepository.count() > 0) {
            log.info("Database already seeded — skipping DataLoader");
            return;
        }

        List<EndpointConfig> configs = new ObjectMapper().readValue(
                new ClassPathResource("endpoints.json").getInputStream(),
                new TypeReference<List<EndpointConfig>>() {}
        );

        for (EndpointConfig config : configs) {
            ApiEndpoint saved = saveEndpoint(config.name(), config.url(), config.active());
            if (config.active()) {
                healthCheckService.checkSingleEndpoint(saved);
            }
        }

        long activeCount = configs.stream().filter(EndpointConfig::active).count();
        log.info("DataLoader seeded {} endpoint(s), {} active", configs.size(), activeCount);
    }

    private ApiEndpoint saveEndpoint(String name, String url, boolean isActive) {
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setName(name);
        endpoint.setUrl(url);
        endpoint.setIsActive(isActive);
        return apiEndpointRepository.save(endpoint);
    }
}
