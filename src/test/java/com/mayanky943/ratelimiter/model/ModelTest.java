package com.mayanky943.ratelimiter.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void rateLimitResultAllowedFactory() {
        RateLimitResult r = RateLimitResult.allowed(100, 99, 60);
        assertThat(r.isAllowed()).isTrue();
        assertThat(r.getLimit()).isEqualTo(100);
        assertThat(r.getRemaining()).isEqualTo(99);
        assertThat(r.getResetSeconds()).isEqualTo(60);
        assertThat(r.getRetryAfterSeconds()).isZero();
    }

    @Test
    void rateLimitResultDeniedFactory() {
        RateLimitResult r = RateLimitResult.denied(100, 30, 5);
        assertThat(r.isAllowed()).isFalse();
        assertThat(r.getLimit()).isEqualTo(100);
        assertThat(r.getRemaining()).isZero();
        assertThat(r.getResetSeconds()).isEqualTo(30);
        assertThat(r.getRetryAfterSeconds()).isEqualTo(5);
    }

    @Test
    void effectiveLimitFollowsAlgorithm() {
        RateLimitRule tb = RateLimitRule.builder()
                .algorithm(RateLimitAlgorithm.TOKEN_BUCKET)
                .capacity(50)
                .maxRequests(999)
                .build();
        assertThat(tb.getEffectiveLimit()).isEqualTo(50);

        RateLimitRule sw = RateLimitRule.builder()
                .algorithm(RateLimitAlgorithm.SLIDING_WINDOW)
                .capacity(999)
                .maxRequests(50)
                .build();
        assertThat(sw.getEffectiveLimit()).isEqualTo(50);
    }

    @Test
    void algorithmEnumHasExpectedValues() {
        assertThat(RateLimitAlgorithm.values()).hasSize(2);
        assertThat(RateLimitAlgorithm.valueOf("TOKEN_BUCKET")).isEqualTo(RateLimitAlgorithm.TOKEN_BUCKET);
    }
}
