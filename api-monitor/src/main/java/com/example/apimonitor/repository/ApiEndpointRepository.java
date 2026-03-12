package com.example.apimonitor.repository;

import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {
    List<ApiEndpoint> findByIsActiveTrue();
    long countBySource(ApiEndpointSource source);
    List<ApiEndpoint> findBySource(ApiEndpointSource source);
    Optional<ApiEndpoint> findByNameIgnoreCase(String name);
}