package com.example.apimonitor.repository;

import com.example.apimonitor.entity.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {
    List<ApiEndpoint> findByIsActiveTrue();
}