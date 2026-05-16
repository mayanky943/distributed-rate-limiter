package com.mayanky943.ratelimiter.filter;

import com.mayanky943.ratelimiter.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitFilterIntegrationTest extends AbstractRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void allowsRequestsUnderLimitAndExposesHeaders() throws Exception {
        // test-per-ip has capacity 3 in application-test.yml
        for (int i = 0; i < 3; i++) {
            MvcResult res = mockMvc.perform(get("/api/ping")
                            .header("X-Forwarded-For", "10.0.0." + (50 + i)))
                    .andReturn();
            assertThat(res.getResponse().getStatus()).isEqualTo(200);
            assertThat(res.getResponse().getHeader("X-RateLimit-Limit")).isNotNull();
            assertThat(res.getResponse().getHeader("X-RateLimit-Remaining")).isNotNull();
            assertThat(res.getResponse().getHeader("X-RateLimit-Reset")).isNotNull();
        }
    }

    @Test
    void returnsTooManyRequestsWhenLimitExceeded() throws Exception {
        String ip = "10.0.0.99";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/ping").header("X-Forwarded-For", ip)).andReturn();
        }
        MvcResult res = mockMvc.perform(get("/api/ping").header("X-Forwarded-For", ip)).andReturn();

        assertThat(res.getResponse().getStatus()).isEqualTo(429);
        assertThat(res.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(res.getResponse().getContentAsString()).contains("rate_limit_exceeded");
    }

    @Test
    void actuatorEndpointsAreNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            MvcResult res = mockMvc.perform(get("/actuator/health")).andReturn();
            assertThat(res.getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void differentIpsHaveSeparateBuckets() throws Exception {
        String ipA = "10.0.1.1";
        String ipB = "10.0.1.2";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/ping").header("X-Forwarded-For", ipA)).andReturn();
        }
        assertThat(mockMvc.perform(get("/api/ping").header("X-Forwarded-For", ipA))
                .andReturn().getResponse().getStatus()).isEqualTo(429);

        // IP B should still be allowed
        assertThat(mockMvc.perform(get("/api/ping").header("X-Forwarded-For", ipB))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void slidingWindowRuleEnforcedOnHeavyEndpoint() throws Exception {
        String ip = "10.0.2.10";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/heavy").header("X-Forwarded-For", ip)).andReturn();
        }
        MvcResult res = mockMvc.perform(get("/api/heavy").header("X-Forwarded-For", ip)).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void adminEndpointExposesCompiledRules() throws Exception {
        MvcResult res = mockMvc.perform(get("/admin/ratelimit/rules")).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getContentAsString()).contains("test-per-ip");
    }
}
