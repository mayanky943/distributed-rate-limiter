package com.mayanky943.ratelimiter.resolver;

import com.mayanky943.ratelimiter.model.RateLimitRule;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ApiKeyResolver implements KeyResolver {

    private static final String API_KEY_HEADER = "X-Api-Key";

    @Override
    public boolean supports(RateLimitRule.Scope scope) {
        return scope == RateLimitRule.Scope.API_KEY;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(apiKey)) {
            return "apikey:" + apiKey.trim();
        }
        return null;
    }
}
