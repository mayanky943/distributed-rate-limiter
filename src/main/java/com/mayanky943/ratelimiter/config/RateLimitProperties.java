package com.mayanky943.ratelimiter.config;

import com.mayanky943.ratelimiter.model.RateLimitAlgorithm;
import com.mayanky943.ratelimiter.model.RateLimitRule;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private RateLimitAlgorithm defaultAlgorithm = RateLimitAlgorithm.TOKEN_BUCKET;
    private List<RuleConfig> rules = new ArrayList<>();

    @Data
    public static class RuleConfig {
        private String name;
        private RateLimitRule.Scope scope = RateLimitRule.Scope.IP;
        private RateLimitAlgorithm algorithm;
        private long capacity = 100;
        private long refillTokens = 10;
        private long refillPeriodSeconds = 1;
        private long windowSizeSeconds = 60;
        private long maxRequests = 100;
        private String pathPattern = "/**";

        public RateLimitRule toRule(RateLimitAlgorithm fallbackAlgo) {
            return RateLimitRule.builder()
                    .name(name)
                    .scope(scope)
                    .algorithm(algorithm == null ? fallbackAlgo : algorithm)
                    .capacity(capacity)
                    .refillTokens(refillTokens)
                    .refillPeriodSeconds(refillPeriodSeconds)
                    .windowSizeSeconds(windowSizeSeconds)
                    .maxRequests(maxRequests)
                    .pathPattern(pathPattern)
                    .build();
        }
    }

    public List<RateLimitRule> toRules() {
        List<RateLimitRule> compiled = new ArrayList<>(rules.size());
        for (RuleConfig rc : rules) {
            compiled.add(rc.toRule(defaultAlgorithm));
        }
        return compiled;
    }
}
