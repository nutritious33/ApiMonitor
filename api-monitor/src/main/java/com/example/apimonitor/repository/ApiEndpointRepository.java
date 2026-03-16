package com.example.apimonitor.repository;

import com.example.apimonitor.entity.ApiEndpoint;
import com.example.apimonitor.entity.ApiEndpointSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {
    List<ApiEndpoint> findByIsActiveTrue();
    long countBySource(ApiEndpointSource source);
    List<ApiEndpoint> findBySource(ApiEndpointSource source);
    Optional<ApiEndpoint> findByNameIgnoreCase(String name);

    /**
     * Sets {@code isActive = false} for every currently active endpoint in a single
     * UPDATE statement, replacing the N+1 loop previously in the controller.
     * @return the number of rows updated
     */
    @Modifying
    @Transactional
    @Query("UPDATE ApiEndpoint a SET a.isActive = false WHERE a.isActive = true")
    int deactivateAll();
}
