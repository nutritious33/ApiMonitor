package com.example.apimonitor;

import com.example.apimonitor.repository.ApiEndpointRepository;
import com.example.apimonitor.service.HealthCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * Integration test verifying that DataLoader seeds the database correctly on startup.
 * HealthCheckService is mocked to prevent real outbound HTTP calls during seeding.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DataLoaderTest {

    @Autowired
    private ApiEndpointRepository repository;

    @MockBean
    private HealthCheckService healthCheckService;

    @Test
    void onStartup_allEndpointsFromJsonAreLoaded() {
        long count = repository.count();
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void onStartup_exactlyThreeEndpointsAreActiveByDefault() {
        long activeCount = repository.findByIsActiveTrue().size();
        assertThat(activeCount).isEqualTo(3);
    }

    @Test
    void onStartup_remainingEndpointsAreInactive() {
        long total = repository.count();
        long active = repository.findByIsActiveTrue().size();
        assertThat(total - active).isGreaterThan(0);
    }

    @Test
    void onStartup_checksAreTriggeredForActiveEndpoints() {
        // HealthCheckService.checkSingleEndpoint should be called once per active endpoint
        verify(healthCheckService, atLeast(3)).checkSingleEndpoint(any());
    }

    @Test
    void onStartup_activeEndpointsHaveNamesAndUrls() {
        repository.findByIsActiveTrue().forEach(endpoint -> {
            assertThat(endpoint.getName()).isNotBlank();
            assertThat(endpoint.getUrl()).isNotBlank().startsWith("https://");
        });
    }
}
