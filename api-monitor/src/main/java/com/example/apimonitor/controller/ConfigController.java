package com.example.apimonitor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Provides frontend configuration so the UI can authenticate POST requests
 * without hardcoding the API key in the static HTML.
 */
@RestController
@RequestMapping("/api/config")
@Tag(name = "Config", description = "Frontend configuration")
public class ConfigController {

    @Value("${api.security.key}")
    private String apiKey;

    @GetMapping
    @Operation(summary = "Get frontend config", description = "Returns the API key the frontend needs to send with mutating requests")
    public Map<String, String> getFrontendConfig() {
        return Map.of("apiKey", apiKey);
    }
}
