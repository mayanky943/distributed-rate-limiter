package com.mayanky943.ratelimiter.service;

import com.mayanky943.ratelimiter.AbstractRedisIntegrationTest;
import com.mayanky943.ratelimiter.model.RateLimitAlgorithm;
import com.mayanky943.ratelimiter.model.RateLimitResult;
import com.mayanky943.ratelimiter.model.RateLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SlidingWindowRateLimiterTest extends AbstractRedisIntegrationTest {

    @Autowired
    private SlidingWindowRateLimiter limiter;

    private String key;
    private RateLimitRule rule;

    @BeforeEach
    void setUp() {
        key = "test:" + UUID.randomUUID();
        rule = RateLimitRule.builder()
                .name("test-sw")
                .scope(RateLimitRule.Scope.IP)
                .algorithm(RateLimitAlgorithm.SLIDING_WINDOW)
                .maxRequests(3)
                .windowSizeSeconds(2)
                .build();
    }

    @Test
    void allowsRequestsUpToMaxInWindow() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(key, rule).isAllowed()).as("req %d", i).isTrue();
        }
    }

    @Test
    void rejectsRequestsBeyondMaxInWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(key, rule);
        }
        RateLimitResult r = limiter.tryAcquire(key, rule);
        assertThat(r.isAllowed()).isFalse();
        assertThat(r.getRetryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void allowsRequestsAfterWindowSlides() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(key, rule);
        }
        assertThat(limiter.tryAcquire(key, rule).isAllowed()).isFalse();

        TimeUnit.MILLISECONDS.sleep(2200);

        assertThat(limiter.tryAcquire(key, rule).isAllowed()).isTrue();
    }

    @Test
    void remainingDecrementsCorrectly() {
        RateLimitResult r1 = limiter.tryAcquire(key, rule);
        assertThat(r1.getRemaining()).isEqualTo(2);

        RateLimitResult r2 = limiter.tryAcquire(key, rule);
        assertThat(r2.getRemaining()).isEqualTo(1);

        RateLimitResult r3 = limiter.tryAcquire(key, rule);
        assertThat(r3.getRemaining()).isEqualTo(0);
    }
}
