package com.mayanky943.ratelimiter.service;

import com.mayanky943.ratelimiter.config.RateLimitProperties;
import com.mayanky943.ratelimiter.model.RateLimitAlgorithm;
import com.mayanky943.ratelimiter.model.RateLimitResult;
import com.mayanky943.ratelimiter.model.RateLimitRule;
import com.mayanky943.ratelimiter.resolver.KeyResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RateLimitProperties properties;
    private final TokenBucketRateLimiter tokenBucket;
    private final SlidingWindowRateLimiter slidingWindow;
    private final List<KeyResolver> keyResolvers;
    private final MeterRegistry meterRegistry;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, Counter> allowedCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> deniedCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> ruleTimers = new ConcurrentHashMap<>();

    public RateLimitResult check(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return RateLimitResult.allowed(Long.MAX_VALUE, Long.MAX_VALUE, 0);
        }

        List<RateLimitRule> rules = properties.toRules();
        if (rules.isEmpty()) {
            return RateLimitResult.allowed(Long.MAX_VALUE, Long.MAX_VALUE, 0);
        }

        RateLimitResult mostRestrictive = null;

        for (RateLimitRule rule : rules) {
            if (!pathMatcher.match(rule.getPathPattern(), request.getRequestURI())) {
                continue;
            }

            String subject = resolveKey(rule, request);
            if (subject == null) {
                continue;
            }

            String fullKey = rule.getName() + ":" + subject + ":" + request.getRequestURI();
            Timer timer = ruleTimers.computeIfAbsent(rule.getName(),
                    n -> Timer.builder("ratelimit.evaluation")
                            .tag("rule", n)
                            .register(meterRegistry));

            RateLimitResult result = timer.record(() -> evaluate(rule, fullKey));

            recordCounter(rule, result.isAllowed());

            if (!result.isAllowed()) {
                // First denial wins — short-circuit to avoid extra Redis hops
                return result;
            }
            if (mostRestrictive == null || result.getRemaining() < mostRestrictive.getRemaining()) {
                mostRestrictive = result;
            }
        }

        return mostRestrictive != null
                ? mostRestrictive
                : RateLimitResult.allowed(Long.MAX_VALUE, Long.MAX_VALUE, 0);
    }

    private RateLimitResult evaluate(RateLimitRule rule, String key) {
        if (rule.getAlgorithm() == RateLimitAlgorithm.SLIDING_WINDOW) {
            return slidingWindow.tryAcquire(key, rule);
        }
        return tokenBucket.tryAcquire(key, rule);
    }

    private String resolveKey(RateLimitRule rule, HttpServletRequest request) {
        for (KeyResolver resolver : keyResolvers) {
            if (resolver.supports(rule.getScope())) {
                return resolver.resolve(request);
            }
        }
        log.warn("No KeyResolver registered for scope {}", rule.getScope());
        return null;
    }

    private void recordCounter(RateLimitRule rule, boolean allowed) {
        Map<String, Counter> map = allowed ? allowedCounters : deniedCounters;
        String metric = allowed ? "ratelimit.allowed" : "ratelimit.denied";
        map.computeIfAbsent(rule.getName(), n ->
                Counter.builder(metric)
                        .tag("rule", n)
                        .tag("algorithm", rule.getAlgorithm().name())
                        .register(meterRegistry)).increment();
    }
}
