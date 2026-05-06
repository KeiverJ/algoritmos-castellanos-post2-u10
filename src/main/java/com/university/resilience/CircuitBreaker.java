package com.university.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Implementación del patrón CircuitBreaker para resiliencia de sistemas.
 *
 * <p>Estados:
 * <ul>
 *   <li>CLOSED  – operación normal; fallos se acumulan.</li>
 *   <li>OPEN    – rechaza todas las peticiones durante el tiempo de espera.</li>
 *   <li>HALF_OPEN – permite una petición de prueba; éxito cierra, fallo reabre.</li>
 * </ul>
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final AtomicReference<State> state;
    private final AtomicInteger failures;
    private volatile long openedAt;

    /**
     * @param failureThreshold número de fallos consecutivos que abren el circuito.
     * @param resetTimeoutMs   milisegundos a esperar antes de pasar a HALF_OPEN.
     */
    public CircuitBreaker(int failureThreshold, long resetTimeoutMs) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs   = resetTimeoutMs;
        this.state            = new AtomicReference<>(State.CLOSED);
        this.failures         = new AtomicInteger(0);
    }

    /**
     * Ejecuta la operación delegada respetando el estado del circuito.
     *
     * @param operation lógica a ejecutar.
     * @param <T>       tipo de retorno.
     * @return resultado de la operación.
     * @throws CircuitOpenException si el circuito está OPEN y el timeout no expiró.
     */
    public <T> T execute(Supplier<T> operation) {
        if (state.get() == State.OPEN) {
            if (System.currentTimeMillis() - openedAt >= resetTimeoutMs) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
            } else {
                throw new CircuitOpenException("Circuit is OPEN");
            }
        }

        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    private void onSuccess() {
        if (state.get() == State.HALF_OPEN) {
            state.set(State.CLOSED);
            failures.set(0);
        }
    }

    private void onFailure() {
        int count = failures.incrementAndGet();
        if (count >= failureThreshold) {
            state.set(State.OPEN);
            openedAt = System.currentTimeMillis();
        }
    }

    public State getState()    { return state.get(); }
    public int   getFailures() { return failures.get(); }
}
