package com.mayanky943.ratelimiter.resolver;

import com.mayanky943.ratelimiter.model.RateLimitRule;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class IpKeyResolver implements KeyResolver {

    @Override
    public boolean supports(RateLimitRule.Scope scope) {
        return scope == RateLimitRule.Scope.IP;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            // First entry is the original client when behind a trusted proxy
            int comma = xff.indexOf(',');
            return "ip:" + (comma > 0 ? xff.substring(0, comma).trim() : xff.trim());
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return "ip:" + realIp.trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
