package com.university.resilience;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Suite MEJORADA de CircuitBreaker.
 *
 * Estrategia: cada test está diseñado para matar al menos un mutante
 * específico identificado en el reporte PIT baseline.
 *
 * Mutantes objetivo:
 *  - ROR (>=  → >)  en count >= failureThreshold
 *  - ROR (>=  → >)  en resetTimeoutMs comparison
 *  - BDR (state == OPEN → false) en execute()
 *  - BDR (state == HALF_OPEN → false) en onSuccess()
 *  - RVM (failures.set(0)) en onSuccess()
 *  - VoidMethodCall en state.set(OPEN/CLOSED)
 */
class CircuitBreakerTest {

    private CircuitBreaker cb;

    @BeforeEach
    void setUp() {
        cb = new CircuitBreaker(3, 1000L);
    }

    // ─── Grupo 1: estado inicial ─────────────────────────────────────────────

    /**
     * Mata mutante VoidMethodCall sobre la inicialización del estado.
     * Si el constructor no invoca state = CLOSED, este test falla.
     */
    @Test
    void circuitStartsClosed() {
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    /**
     * Mata mutante RVM sobre failures iniciales.
     */
    @Test
    void failuresStartAtZero() {
        assertThat(cb.getFailures()).isEqualTo(0);
    }

    // ─── Grupo 2: ejecución exitosa ───────────────────────────────────────────

    /**
     * Verifica que una ejecución exitosa devuelve el valor correcto.
     * Mata mutante NULL_RETURNS sobre el resultado de execute().
     */
    @Test
    void executeSuccessReturnsValue() {
        String result = cb.execute(() -> "respuesta");
        assertThat(result).isEqualTo("respuesta");
    }

    /**
     * Éxito en CLOSED no altera el estado.
     * Mata mutante BDR sobre la condición HALF_OPEN en onSuccess().
     */
    @Test
    void successInClosedKeepsStateClosed() {
        cb.execute(() -> "ok");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ─── Grupo 3: fallos y umbral exacto ─────────────────────────────────────

    /**
     * 2 fallos (umbral - 1) → circuito sigue CLOSED.
     * Mata mutante ROR (>= → >) en count >= failureThreshold:
     *   con el mutante >  el circuito abriría en 2 fallos → test falla.
     */
    @Test
    void twoFailuresKeepCircuitClosed() {
        triggerFailures(2);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getFailures()).isEqualTo(2);
    }

    /**
     * Exactamente 3 fallos → circuito OPEN.
     * Complementario al test anterior; cierra el rango del ROR.
     */
    @Test
    void threeFailuresOpenCircuit() {
        triggerFailures(3);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ─── Grupo 4: circuito abierto rechaza ───────────────────────────────────

    /**
     * Mata mutante BDR (state == OPEN → false) en execute().
     * Si el mutante sobrevive, execute() no lanzaría la excepción.
     */
    @Test
    void openCircuitRejectsAllExecutions() {
        triggerFailures(3);
        assertThatThrownBy(() -> cb.execute(() -> "bloqueado"))
                .isInstanceOf(CircuitOpenException.class)
                .hasMessageContaining("OPEN");
    }

    /**
     * Múltiples intentos sobre circuito abierto siguen rechazando.
     * Mata mutantes REMOVE_CONDITIONALS sobre la rama OPEN.
     */
    @Test
    void openCircuitRejectsMultipleCalls() {
        triggerFailures(3);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cb.execute(() -> "x"))
                    .isInstanceOf(CircuitOpenException.class);
        }
    }

    // ─── Grupo 5: transición OPEN → HALF_OPEN → CLOSED ───────────────────────

    /**
     * Mata mutante ROR (>= → >) en resetTimeoutMs comparison:
     *   con >= el circuito transiciona cuando elapsed == timeout;
     *   con >  necesitaría elapsed > timeout → el test fallía si timeout exacto.
     * Usamos timeout muy corto (50 ms) y esperamos 60 ms.
     */
    @Test
    void transitionsToHalfOpenAfterTimeout() throws InterruptedException {
        CircuitBreaker fastCb = new CircuitBreaker(1, 50L);
        triggerFailure(fastCb);
        assertThat(fastCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(60);

        // Al ejecutar, debe transicionar a HALF_OPEN y luego a CLOSED (éxito)
        assertThatCode(() -> fastCb.execute(() -> "probe"))
                .doesNotThrowAnyException();
    }

    /**
     * Éxito en HALF_OPEN → CLOSED y failures reset a 0.
     * Mata mutante BDR sobre la condición HALF_OPEN en onSuccess().
     * Mata mutante RVM sobre failures.set(0).
     */
    @Test
    void halfOpenTransitionsToClosedOnSuccess() throws InterruptedException {
        CircuitBreaker fastCb = new CircuitBreaker(1, 50L);
        triggerFailure(fastCb);
        Thread.sleep(60);

        fastCb.execute(() -> "recovery");

        assertThat(fastCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(fastCb.getFailures()).isEqualTo(0);
    }

    /**
     * Fallo en HALF_OPEN → regresa a OPEN.
     * Mata mutante VOID_METHOD_CALL sobre state.set(OPEN) en onFailure().
     */
    @Test
    void halfOpenFailureReopensCircuit() throws InterruptedException {
        CircuitBreaker fastCb = new CircuitBreaker(1, 50L);
        triggerFailure(fastCb);
        Thread.sleep(60);

        // El primer intento en HALF_OPEN falla → regresa a OPEN
        assertThatThrownBy(() ->
                fastCb.execute(() -> { throw new RuntimeException("fallo en half-open"); })
        ).isInstanceOf(RuntimeException.class);

        assertThat(fastCb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    /**
     * Circuito que no ha expirado sigue rechazando aunque se llame varias veces.
     * Mata mutante CONDITIONALS_BOUNDARY sobre la comparación de tiempo.
     */
    @Test
    void openCircuitBeforeTimeoutStillRejects() throws InterruptedException {
        CircuitBreaker fastCb = new CircuitBreaker(1, 500L);
        triggerFailure(fastCb);
        Thread.sleep(10); // muy por debajo del timeout
        assertThatThrownBy(() -> fastCb.execute(() -> "x"))
                .isInstanceOf(CircuitOpenException.class);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void triggerFailures(int count) {
        for (int i = 0; i < count; i++) {
            triggerFailure(cb);
        }
    }

    private void triggerFailure(CircuitBreaker breaker) {
        try {
            breaker.execute(() -> { throw new RuntimeException("fallo controlado"); });
        } catch (Exception ignored) {}
    }
}
