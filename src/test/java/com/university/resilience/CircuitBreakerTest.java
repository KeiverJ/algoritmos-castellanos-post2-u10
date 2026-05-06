package com.university.resilience;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Suite INICIAL deliberadamente incompleta.
 * Objetivo: demostrar mutation score bajo (~35–45%) como baseline.
 * Se documenta en el README como "Ronda 1 - Baseline".
 */
class CircuitBreakerTest {

    private CircuitBreaker cb;

    @BeforeEach
    void setUp() {
        cb = new CircuitBreaker(3, 1000L);
    }

    @Test
    void circuitStartsClosed() {
        // Cubre línea del constructor — assertion débil
        assertThat(cb).isNotNull(); // no verifica el estado real
    }

    @Test
    void executeSucceeds() {
        // Ejecuta el código pero no verifica estado post-éxito
        cb.execute(() -> "ok");
    }

    @Test
    void failuresIncrement() {
        // Provoca fallos pero no verifica el umbral exacto
        for (int i = 0; i < 3; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("fallo"); });
            } catch (Exception ignored) {}
        }
        assertThat(cb.getState()).isNotNull(); // assertion trivial
    }
}
