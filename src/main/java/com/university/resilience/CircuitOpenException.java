package com.university.resilience;

/**
 * Excepción lanzada cuando se intenta ejecutar una operación
 * con el CircuitBreaker en estado OPEN.
 */
public class CircuitOpenException extends RuntimeException {

    public CircuitOpenException(String message) {
        super(message);
    }
}
