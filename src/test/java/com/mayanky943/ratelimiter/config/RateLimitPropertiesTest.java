package com.mayanky943.ratelimiter.config;

import com.mayanky943.ratelimiter.model.RateLimitAlgorithm;
import com.mayanky943.ratelimiter.model.RateLimitRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

    @Test
    void compilesRulesWithFallbackAlgorithm() {
        RateLimitProperties props = new RateLimitProperties();
        props.setDefaultAlgorithm(RateLimitAlgorithm.SLIDING_WINDOW);

        RateLimitProperties.RuleConfig rc = new RateLimitProperties.RuleConfig();
        rc.setName("r1");
        rc.setScope(RateLimitRule.Scope.IP);
        rc.setAlgorithm(null);
        rc.setPathPattern("/api/**");
        rc.setMaxRequests(7);

        props.setRules(List.of(rc));

        List<RateLimitRule> compiled = props.toRules();
        assertThat(compiled).hasSize(1);
        assertThat(compiled.get(0).getAlgorithm()).isEqualTo(RateLimitAlgorithm.SLIDING_WINDOW);
        assertThat(compiled.get(0).getMaxRequests()).isEqualTo(7);
    }

    @Test
    void honorsExplicitAlgorithmPerRule() {
        RateLimitProperties props = new RateLimitProperties();
        props.setDefaultAlgorithm(RateLimitAlgorithm.SLIDING_WINDOW);

        RateLimitProperties.RuleConfig rc = new RateLimitProperties.RuleConfig();
        rc.setName("r2");
        rc.setAlgorithm(RateLimitAlgorithm.TOKEN_BUCKET);
        rc.setCapacity(50);

        props.setRules(List.of(rc));

        assertThat(props.toRules().get(0).getAlgorithm()).isEqualTo(RateLimitAlgorithm.TOKEN_BUCKET);
        assertThat(props.toRules().get(0).getCapacity()).isEqualTo(50);
    }

    @Test
    void emptyRulesProducesEmptyList() {
        RateLimitProperties props = new RateLimitProperties();
        assertThat(props.toRules()).isEmpty();
    }
}
