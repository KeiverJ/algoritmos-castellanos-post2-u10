package com.university.resilience;

/**
 * Implementación del algoritmo Token Bucket para limitación de tasa de peticiones.
 *
 * <p>El bucket comienza lleno ({@code maxTokens}). Cada petición consume
 * {@code tokens} unidades. El bucket se recarga a razón de {@code refillRate}
 * tokens por segundo de forma lazy (al momento de cada {@code tryConsume}).
 */
public class TokenBucketRateLimiter {

    private final int maxTokens;
    private final int refillRate;   // tokens por segundo
    private int currentTokens;
    private long lastRefillTime;

    /**
     * @param maxTokens  capacidad máxima del bucket.
     * @param refillRate tokens que se agregan por segundo.
     */
    public TokenBucketRateLimiter(int maxTokens, int refillRate) {
        this.maxTokens      = maxTokens;
        this.refillRate     = refillRate;
        this.currentTokens  = maxTokens;
        this.lastRefillTime = System.currentTimeMillis();
    }

    /**
     * Intenta consumir {@code tokens} del bucket.
     *
     * @param tokens cantidad a consumir.
     * @return {@code true} si hay suficientes tokens; {@code false} si la petición es rechazada.
     */
    public synchronized boolean tryConsume(int tokens) {
        refill();
        if (currentTokens >= tokens) {
            currentTokens -= tokens;
            return true;
        }
        return false;
    }

    /** Recarga tokens según el tiempo transcurrido desde la última recarga. */
    private void refill() {
        long now      = System.currentTimeMillis();
        long elapsed  = now - lastRefillTime;
        int newTokens = (int) (elapsed * refillRate / 1000);
        if (newTokens > 0) {
            currentTokens   = Math.min(maxTokens, currentTokens + newTokens);
            lastRefillTime  = now;
        }
    }

    public int getTokens() { return currentTokens; }
}
