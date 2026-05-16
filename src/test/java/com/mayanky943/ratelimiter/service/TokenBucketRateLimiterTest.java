package com.mayanky943.ratelimiter.service;

import com.mayanky943.ratelimiter.AbstractRedisIntegrationTest;
import com.mayanky943.ratelimiter.model.RateLimitAlgorithm;
import com.mayanky943.ratelimiter.model.RateLimitResult;
import com.mayanky943.ratelimiter.model.RateLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TokenBucketRateLimiterTest extends AbstractRedisIntegrationTest {

    @Autowired
    private TokenBucketRateLimiter limiter;

    @Autowired
    private StringRedisTemplate redis;

    private String key;
    private RateLimitRule rule;

    @BeforeEach
    void setUp() {
        key = "test:" + UUID.randomUUID();
        rule = RateLimitRule.builder()
                .name("test-tb")
                .scope(RateLimitRule.Scope.IP)
                .algorithm(RateLimitAlgorithm.TOKEN_BUCKET)
                .capacity(5)
                .refillTokens(5)
                .refillPeriodSeconds(60)
                .build();
    }

    @Test
    void allowsRequestsUpToCapacity() {
        for (int i = 0; i < 5; i++) {
            RateLimitResult r = limiter.tryAcquire(key, rule);
            assertThat(r.isAllowed()).as("request %d", i).isTrue();
            assertThat(r.getRemaining()).isEqualTo(5L - (i + 1));
        }
    }

    @Test
    void rejectsRequestsBeyondCapacity() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(key, rule);
        }
        RateLimitResult r = limiter.tryAcquire(key, rule);
        assertThat(r.isAllowed()).isFalse();
        assertThat(r.getRemaining()).isEqualTo(0);
        assertThat(r.getRetryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void refillsTokensOverTime() throws InterruptedException {
        RateLimitRule fastRefill = RateLimitRule.builder()
                .name("fast")
                .scope(RateLimitRule.Scope.IP)
                .algorithm(RateLimitAlgorithm.TOKEN_BUCKET)
                .capacity(2)
                .refillTokens(2)
                .refillPeriodSeconds(1)
                .build();

        // Drain the bucket
        limiter.tryAcquire(key, fastRefill);
        limiter.tryAcquire(key, fastRefill);
        assertThat(limiter.tryAcquire(key, fastRefill).isAllowed()).isFalse();

        TimeUnit.MILLISECONDS.sleep(1200);

        RateLimitResult r = limiter.tryAcquire(key, fastRefill);
        assertThat(r.isAllowed()).isTrue();
    }

    @Test
    void independentKeysHaveIndependentBuckets() {
        String otherKey = "test:" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(key, rule);
        }
        assertThat(limiter.tryAcquire(key, rule).isAllowed()).isFalse();
        assertThat(limiter.tryAcquire(otherKey, rule).isAllowed()).isTrue();
    }

    @Test
    void concurrentRequestsRespectCapacity() throws Exception {
        int totalRequests = 50;
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            for (int i = 0; i < totalRequests; i++) {
                pool.submit(() -> {
                    if (limiter.tryAcquire(key, rule).isAllowed()) {
                        allowed.incrementAndGet();
                    }
                });
            }
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(allowed.get()).isEqualTo(5);
    }
}
