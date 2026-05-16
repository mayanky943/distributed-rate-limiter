package com.mayanky943.ratelimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/heavy")
    public Map<String, Object> heavy() {
        return Map.of(
                "status", "ok",
                "endpoint", "heavy",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/public")
    public Map<String, Object> publicEndpoint() {
        return Map.of(
                "status", "ok",
                "endpoint", "public",
                "timestamp", Instant.now().toString()
        );
    }
}
