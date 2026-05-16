package com.mayanky943.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RateLimiterApplicationTest extends AbstractRedisIntegrationTest {

    @Test
    void contextLoads() {
        // Sanity check — full Spring context wires up against a real Redis.
    }
}
