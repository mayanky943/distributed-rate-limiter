package com.mayanky943.ratelimiter.service;

import com.mayanky943.ratelimiter.model.RateLimitResult;
import com.mayanky943.ratelimiter.model.RateLimitRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class SlidingWindowRateLimiter {

    private static final String KEY_PREFIX = "rl:sw:";

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;

    public SlidingWindowRateLimiter(
            StringRedisTemplate redis,
            @Qualifier("slidingWindowScript") RedisScript<List> script) {
        this.redis = redis;
        this.script = script;
    }

    public RateLimitResult tryAcquire(String key, RateLimitRule rule) {
        String redisKey = KEY_PREFIX + key;
        long nowMs = System.currentTimeMillis();
        String requestId = nowMs + ":" + UUID.randomUUID();

        @SuppressWarnings("unchecked")
        List<Long> result = redis.execute(
                script,
                Collections.singletonList(redisKey),
                String.valueOf(rule.getMaxRequests()),
                String.valueOf(rule.getWindowSizeSeconds()),
                String.valueOf(nowMs),
                requestId
        );

        if (result == null || result.size() < 4) {
            log.error("Sliding window script returned unexpected result for key {}", redisKey);
            return RateLimitResult.allowed(rule.getMaxRequests(), rule.getMaxRequests(), 0);
        }

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        long resetSeconds = result.get(2);
        long retryAfterSeconds = result.get(3);

        return allowed
                ? RateLimitResult.allowed(rule.getMaxRequests(), remaining, resetSeconds)
                : RateLimitResult.denied(rule.getMaxRequests(), resetSeconds, retryAfterSeconds);
    }
}
