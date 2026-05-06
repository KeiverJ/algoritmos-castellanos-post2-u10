package com.university.resilience;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Suite INICIAL deliberadamente incompleta para TokenBucketRateLimiter.
 * Baseline PIT esperado: menor al 50%.
 */
class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new TokenBucketRateLimiter(10, 5);
    }

    @Test
    void limiterIsCreated() {
        // Assertion débil — solo verifica que el objeto existe
        assertThat(limiter).isNotNull();
    }

    @Test
    void consumeDoesNotThrow() {
        // No verifica el resultado del consumo
        limiter.tryConsume(1);
    }
}
