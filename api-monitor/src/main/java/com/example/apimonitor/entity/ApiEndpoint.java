package com.example.apimonitor.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class ApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String url;
    private String currentStatus;
    private Long lastLatencyMs;
    private LocalDateTime lastCheckedAt;
    private Integer totalChecks = 0;
    private Integer successfulChecks = 0;
    private Boolean isActive = false;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }

    public void setUrl(String url) { this.url = url; }

    public String getCurrentStatus() { return currentStatus; }

    public void setCurrentStatus(String currentStatus) { 
        this.currentStatus = currentStatus; 
    }

    public Long getLastLatencyMs() { return lastLatencyMs; }

    public void setLastLatencyMs(Long lastLatencyMs) { 
        this.lastLatencyMs = lastLatencyMs; 
    }

    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { 
        this.lastCheckedAt = lastCheckedAt; 
    }

    public Integer getTotalChecks() {
        return totalChecks;
    }

    public void setTotalChecks(Integer totalChecks) {
        this.totalChecks = totalChecks;
    }

    public Integer getSuccessfulChecks() {
        return successfulChecks;
    }

    public void setSuccessfulChecks(Integer successfulChecks) {
        this.successfulChecks = successfulChecks;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}