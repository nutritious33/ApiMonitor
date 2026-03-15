package com.example.apimonitor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Key-validation ping used by the admin login modal.
     *
     * The X-API-Key header is validated by {@link com.example.apimonitor.config.ApiKeyAuthFilter}
     * before this method is reached. If the key is missing or wrong the filter short-circuits
     * with 401; reaching this method therefore guarantees the key is valid.
     *
     * Returns 204 No Content — the presence of the response is the signal, not its body.
     */
    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent().build();
    }
}
