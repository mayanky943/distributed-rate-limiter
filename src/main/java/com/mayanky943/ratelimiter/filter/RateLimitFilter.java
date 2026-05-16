package com.mayanky943.ratelimiter.filter;

import com.mayanky943.ratelimiter.model.RateLimitResult;
import com.mayanky943.ratelimiter.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    static final String HEADER_LIMIT = "X-RateLimit-Limit";
    static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    static final String HEADER_RESET = "X-RateLimit-Reset";
    static final String HEADER_RETRY_AFTER = "Retry-After";

    private final RateLimiterService rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Skip actuator/healthcheck endpoints so probes never get 429'd
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitResult result = rateLimiter.check(request);
        writeHeaders(response, result);

        if (!result.isAllowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.getRetryAfterSeconds()));
            response.getWriter().write(String.format(
                    "{\"error\":\"rate_limit_exceeded\",\"retry_after_seconds\":%d}",
                    result.getRetryAfterSeconds()
            ));
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeHeaders(HttpServletResponse response, RateLimitResult result) {
        // Don't pollute headers when limiter is effectively off
        if (result.getLimit() == Long.MAX_VALUE) {
            return;
        }
        response.setHeader(HEADER_LIMIT, String.valueOf(result.getLimit()));
        response.setHeader(HEADER_REMAINING, String.valueOf(result.getRemaining()));
        response.setHeader(HEADER_RESET, String.valueOf(result.getResetSeconds()));
    }
}
