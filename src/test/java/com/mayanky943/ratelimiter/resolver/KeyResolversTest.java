package com.mayanky943.ratelimiter.resolver;

import com.mayanky943.ratelimiter.model.RateLimitRule;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeyResolversTest {

    @Test
    void ipResolverPrefersXForwardedFor() {
        IpKeyResolver resolver = new IpKeyResolver();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1");

        assertThat(resolver.supports(RateLimitRule.Scope.IP)).isTrue();
        assertThat(resolver.supports(RateLimitRule.Scope.USER)).isFalse();
        assertThat(resolver.resolve(req)).isEqualTo("ip:203.0.113.7");
    }

    @Test
    void ipResolverFallsBackToXRealIp() {
        IpKeyResolver resolver = new IpKeyResolver();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Forwarded-For")).thenReturn(null);
        when(req.getHeader("X-Real-IP")).thenReturn("198.51.100.42");

        assertThat(resolver.resolve(req)).isEqualTo("ip:198.51.100.42");
    }

    @Test
    void ipResolverFallsBackToRemoteAddr() {
        IpKeyResolver resolver = new IpKeyResolver();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(resolver.resolve(req)).isEqualTo("ip:127.0.0.1");
    }

    @Test
    void userResolverReadsHeader() {
        UserKeyResolver resolver = new UserKeyResolver();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-User-Id")).thenReturn("user-42");

        assertThat(resolver.supports(RateLimitRule.Scope.USER)).isTrue();
        assertThat(resolver.supports(RateLimitRule.Scope.IP)).isFalse();
        assertThat(resolver.resolve(req)).isEqualTo("user:user-42");
    }

    @Test
    void userResolverReturnsNullWhenAnonymous() {
        UserKeyResolver resolver = new UserKeyResolver();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-User-Id")).thenReturn(null);
        when(req.getUserPrincipal()).thenReturn(null);

        assertThat(resolver.resolve(req)).isNull();
    }

    @Test
    void apiKeyResolverReadsHeader() {
        ApiKeyResolver resolver = new ApiKeyResolver();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Api-Key")).thenReturn("k_live_abcd");

        assertThat(resolver.supports(RateLimitRule.Scope.API_KEY)).isTrue();
        assertThat(resolver.resolve(req)).isEqualTo("apikey:k_live_abcd");
    }

    @Test
    void apiKeyResolverReturnsNullWhenMissing() {
        ApiKeyResolver resolver = new ApiKeyResolver();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Api-Key")).thenReturn(null);

        assertThat(resolver.resolve(req)).isNull();
    }
}
