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

@Slf4j
@Component
public class TokenBucketRateLimiter {

    private static final String KEY_PREFIX = "rl:tb:";

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;

    public TokenBucketRateLimiter(
            StringRedisTemplate redis,
            @Qualifier("tokenBucketScript") RedisScript<List> script) {
        this.redis = redis;
        this.script = script;
    }

    public RateLimitResult tryAcquire(String key, RateLimitRule rule) {
        String redisKey = KEY_PREFIX + key;
        long nowMs = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        List<Long> result = redis.execute(
                script,
                Collections.singletonList(redisKey),
                String.valueOf(rule.getCapacity()),
                String.valueOf(rule.getRefillTokens()),
                String.valueOf(rule.getRefillPeriodSeconds()),
                String.valueOf(nowMs),
                "1"
        );

        if (result == null || result.size() < 4) {
            log.error("Token bucket script returned unexpected result for key {}", redisKey);
            // Fail-open: if Redis misbehaves, don't block traffic
            return RateLimitResult.allowed(rule.getCapacity(), rule.getCapacity(), 0);
        }

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        long resetSeconds = result.get(2);
        long retryAfterSeconds = result.get(3);

        return allowed
                ? RateLimitResult.allowed(rule.getCapacity(), remaining, resetSeconds)
                : RateLimitResult.denied(rule.getCapacity(), resetSeconds, retryAfterSeconds);
    }
}
