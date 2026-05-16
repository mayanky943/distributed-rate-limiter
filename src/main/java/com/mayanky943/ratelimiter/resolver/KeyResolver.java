package com.mayanky943.ratelimiter.resolver;

import com.mayanky943.ratelimiter.model.RateLimitRule;

import jakarta.servlet.http.HttpServletRequest;

public interface KeyResolver {
    boolean supports(RateLimitRule.Scope scope);
    String resolve(HttpServletRequest request);
}
