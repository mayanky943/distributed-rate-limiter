package com.mayanky943.ratelimiter.controller;

import com.mayanky943.ratelimiter.config.RateLimitProperties;
import com.mayanky943.ratelimiter.model.RateLimitRule;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/ratelimit")
@RequiredArgsConstructor
public class RateLimitAdminController {

    private final RateLimitProperties properties;

    @GetMapping("/rules")
    public Map<String, Object> rules() {
        List<RateLimitRule> compiled = properties.toRules();
        return Map.of(
                "enabled", properties.isEnabled(),
                "defaultAlgorithm", properties.getDefaultAlgorithm(),
                "rules", compiled
        );
    }
}
