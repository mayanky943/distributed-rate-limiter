package com.mayanky943.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class RateLimitResult {
    private final boolean allowed;
    private final long limit;
    private final long remaining;
    private final long resetSeconds;
    private final long retryAfterSeconds;

    public static RateLimitResult allowed(long limit, long remaining, long resetSeconds) {
        return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .remaining(remaining)
                .resetSeconds(resetSeconds)
                .retryAfterSeconds(0)
                .build();
    }

    public static RateLimitResult denied(long limit, long resetSeconds, long retryAfterSeconds) {
        return RateLimitResult.builder()
                .allowed(false)
                .limit(limit)
                .remaining(0)
                .resetSeconds(resetSeconds)
                .retryAfterSeconds(retryAfterSeconds)
                .build();
    }
}
