package com.mayanky943.ratelimiter.resolver;

import com.mayanky943.ratelimiter.model.RateLimitRule;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class UserKeyResolver implements KeyResolver {

    private static final String USER_HEADER = "X-User-Id";

    @Override
    public boolean supports(RateLimitRule.Scope scope) {
        return scope == RateLimitRule.Scope.USER;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String userId = request.getHeader(USER_HEADER);
        if (StringUtils.hasText(userId)) {
            return "user:" + userId.trim();
        }
        if (request.getUserPrincipal() != null) {
            return "user:" + request.getUserPrincipal().getName();
        }
        // No identifiable user — skip this rule rather than collapsing all anon
        // traffic into one bucket
        return null;
    }
}
