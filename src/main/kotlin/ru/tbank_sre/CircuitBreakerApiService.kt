package ru.tbank_sre

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.atomic.AtomicInteger

enum class CircuitBreakerState {
    CLOSED, OPEN, HALF_OPEN
}

@Service
class CircuitBreakerApiService(private val webClient: WebClient) {
    private val logger = LoggerFactory.getLogger(CircuitBreakerApiService::class.java)
    private var state = CircuitBreakerState.CLOSED
    private var failureCount = AtomicInteger(0)
    private var lastFailureTime = 0L
    private val failureThreshold = 3
    private val resetTimeout = 10000L // 10 секунд

    fun getDataWithCircuitBreaker(url: String): String {
        synchronized(this) {
            if (state == CircuitBreakerState.OPEN) {
                if (System.currentTimeMillis() - lastFailureTime > resetTimeout) {
                    logger.info("Switching to HALF_OPEN state")
                    state = CircuitBreakerState.HALF_OPEN
                } else {
                    throw RuntimeException("Circuit Breaker is OPEN, request blocked")
                }
            }
        }

        return try {
            val response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            synchronized(this) {
                state = CircuitBreakerState.CLOSED
                failureCount.set(0)
            }
            response ?: throw RuntimeException("Empty response body")
        } catch (ex: WebClientResponseException) {
            if (ex.statusCode in listOf(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    HttpStatus.GATEWAY_TIMEOUT
                )
            ) {
                synchronized(this) {
                    failureCount.incrementAndGet()
                    if (failureCount.get() >= failureThreshold) {
                        logger.warn("Switching to OPEN state")
                        state = CircuitBreakerState.OPEN
                        lastFailureTime = System.currentTimeMillis()
                    }
                }
            }
            throw ex
        }
    }
}