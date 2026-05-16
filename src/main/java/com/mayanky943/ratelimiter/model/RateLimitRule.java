package com.mayanky943.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {

    public enum Scope {
        IP, USER, API_KEY
    }

    private String name;
    private Scope scope;
    private RateLimitAlgorithm algorithm;
    private long capacity;
    private long refillTokens;
    private long refillPeriodSeconds;
    private long windowSizeSeconds;
    private long maxRequests;
    private String pathPattern;

    public long getEffectiveLimit() {
        return algorithm == RateLimitAlgorithm.TOKEN_BUCKET ? capacity : maxRequests;
    }
}
