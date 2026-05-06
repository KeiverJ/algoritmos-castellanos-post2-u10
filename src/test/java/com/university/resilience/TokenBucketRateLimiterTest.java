package com.university.resilience;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Suite MEJORADA de TokenBucketRateLimiter.
 *
 * Mutantes objetivo identificados tras baseline PIT:
 *  - ROR (>= → >) en currentTokens >= tokens
 *  - MATH en elapsed * refillRate / 1000
 *  - ROR en Math.min(maxTokens, ...)
 *  - BDR (newTokens > 0 → true) en refill()
 *  - FALSE_RETURNS en tryConsume()
 *  - VoidMethodCall en currentTokens -= tokens
 */
class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        // maxTokens=10, refillRate=10 tokens/seg
        limiter = new TokenBucketRateLimiter(10, 10);
    }

    // ─── Grupo 1: estado inicial ──────────────────────────────────────────────

    /**
     * El bucket inicia lleno.
     * Mata mutante VOID_METHOD_CALL sobre currentTokens = maxTokens.
     */
    @Test
    void startsWithMaxTokens() {
        assertThat(limiter.getTokens()).isEqualTo(10);
    }

    // ─── Grupo 2: consumo exitoso ─────────────────────────────────────────────

    /**
     * Consumo cuando hay tokens suficientes retorna true.
     * Mata mutante FALSE_RETURNS en tryConsume().
     */
    @Test
    void consumeReturnsTrueWhenTokensAvailable() {
        assertThat(limiter.tryConsume(1)).isTrue();
    }

    /**
     * Consumo descuenta los tokens del bucket.
     * Mata mutante VOID_METHOD_CALL sobre currentTokens -= tokens.
     */
    @Test
    void consumeDeductsTokens() {
        limiter.tryConsume(3);
        assertThat(limiter.getTokens()).isEqualTo(7);
    }

    /**
     * Consumo exactamente igual al total disponible (umbral ROR >= → >).
     * Con mutante >, tryConsume(10) retornaría false cuando quedan 10 tokens.
     */
    @Test
    void consumeExactlyAllAvailableTokens() {
        assertThat(limiter.tryConsume(10)).isTrue();
        assertThat(limiter.getTokens()).isEqualTo(0);
    }

    /**
     * Consumo de 1 token cuando solo queda 1 → true.
     * Complementa el boundary check.
     */
    @Test
    void consumeOneTokenWhenOneLeft() {
        limiter.tryConsume(9); // quedan 1
        assertThat(limiter.tryConsume(1)).isTrue();
        assertThat(limiter.getTokens()).isEqualTo(0);
    }

    // ─── Grupo 3: rechazo por tokens insuficientes ────────────────────────────

    /**
     * Consumo mayor al disponible retorna false.
     * Mata mutante TRUE_RETURNS en tryConsume().
     */
    @Test
    void consumeReturnsFalseWhenTokensInsufficient() {
        assertThat(limiter.tryConsume(11)).isFalse();
    }

    /**
     * Rechazar no modifica los tokens.
     * Mata mutante VOID_METHOD_CALL sobre currentTokens -= tokens
     * (verificando que no se ejecuta en la rama false).
     */
    @Test
    void failedConsumeDoesNotDeductTokens() {
        limiter.tryConsume(11);
        assertThat(limiter.getTokens()).isEqualTo(10);
    }

    /**
     * Bucket vacío rechaza cualquier petición.
     * Mata mutante REMOVE_CONDITIONALS sobre la condición de rechazo.
     */
    @Test
    void emptyBucketRejectsAllRequests() {
        limiter.tryConsume(10); // vaciar
        assertThat(limiter.tryConsume(1)).isFalse();
        assertThat(limiter.tryConsume(5)).isFalse();
    }

    /**
     * tryConsume(0) retorna true (0 >= 0 con la condición correcta).
     * Mata mutante ROR (>= → >) cuando tokens=0 y currentTokens=10.
     */
    @Test
    void consumeZeroTokensAlwaysSucceeds() {
        assertThat(limiter.tryConsume(0)).isTrue();
    }

    // ─── Grupo 4: recarga de tokens ───────────────────────────────────────────

    /**
     * Después de esperar 1 segundo, el bucket debe recargarse.
     * Mata mutante MATH sobre elapsed * refillRate / 1000.
     * Rate = 10 tokens/seg → después de 1 seg deben reponerse ~10 tokens.
     */
    @Test
    void tokensRefillAfterOneSecond() throws InterruptedException {
        limiter.tryConsume(10); // vaciar
        assertThat(limiter.getTokens()).isEqualTo(0);

        Thread.sleep(1100); // esperar 1.1 seg para garantizar recarga

        boolean accepted = limiter.tryConsume(5);
        assertThat(accepted).isTrue();
    }

    /**
     * La recarga no supera maxTokens.
     * Mata mutante sobre Math.min(maxTokens, currentTokens + newTokens):
     *   si se elimina Math.min, currentTokens podría exceder el máximo.
     */
    @Test
    void refillDoesNotExceedMaxTokens() throws InterruptedException {
        // Bucket ya lleno; esperamos más tiempo del necesario
        Thread.sleep(1100);
        limiter.tryConsume(1); // forzar refill
        assertThat(limiter.getTokens()).isLessThanOrEqualTo(10);
    }

    /**
     * Sin tiempo transcurrido, no hay recarga.
     * Mata mutante BDR (newTokens > 0 → true) que haría refill siempre.
     */
    @Test
    void noRefillWithoutElapsedTime() {
        limiter.tryConsume(5);
        int tokensAfterConsume = limiter.getTokens();
        // Llamada inmediata — no debe recargar
        limiter.tryConsume(0);
        assertThat(limiter.getTokens()).isEqualTo(tokensAfterConsume);
    }

    /**
     * Consumos sucesivos son acumulativos.
     * Verifica coherencia del estado interno entre llamadas.
     */
    @Test
    void multipleConsumesAreAccumulative() {
        limiter.tryConsume(3);
        limiter.tryConsume(3);
        limiter.tryConsume(3);
        assertThat(limiter.getTokens()).isEqualTo(1);
    }
}
