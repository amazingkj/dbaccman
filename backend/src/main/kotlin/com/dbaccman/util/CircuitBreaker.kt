package com.dbaccman.util

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Circuit Breaker states
 */
enum class CircuitState {
    CLOSED,      // Normal operation, requests allowed
    OPEN,        // Failures exceeded threshold, requests blocked
    HALF_OPEN    // Testing if service recovered
}

/**
 * Circuit Breaker configuration
 */
data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,           // Number of failures before opening
    val successThreshold: Int = 2,           // Successes needed to close from half-open
    val openDurationMs: Long = 30_000,       // How long to stay open before half-open
    val halfOpenMaxRequests: Int = 3         // Max requests allowed in half-open state
)

/**
 * Circuit Breaker implementation for database connections.
 * Prevents cascading failures when database is unavailable.
 */
class CircuitBreaker(
    private val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private val logger = LoggerFactory.getLogger(CircuitBreaker::class.java)

    @Volatile
    private var state: CircuitState = CircuitState.CLOSED

    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val halfOpenRequests = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val lastStateChange = AtomicLong(System.currentTimeMillis())

    /**
     * Execute an operation with circuit breaker protection.
     *
     * @param operation The operation to execute
     * @return The result of the operation
     * @throws CircuitBreakerOpenException if circuit is open
     */
    fun <T> execute(operation: () -> T): T {
        if (!allowRequest()) {
            throw CircuitBreakerOpenException(
                "Circuit breaker '$name' is OPEN. Service unavailable.",
                remainingOpenTime()
            )
        }

        return try {
            val result = operation()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure(e)
            throw e
        }
    }

    /**
     * Check if a request should be allowed.
     */
    private fun allowRequest(): Boolean {
        return when (state) {
            CircuitState.CLOSED -> true
            CircuitState.OPEN -> {
                // Check if we should transition to half-open
                if (System.currentTimeMillis() - lastFailureTime.get() >= config.openDurationMs) {
                    transitionTo(CircuitState.HALF_OPEN)
                    true
                } else {
                    false
                }
            }
            CircuitState.HALF_OPEN -> {
                // Allow limited requests in half-open state
                halfOpenRequests.incrementAndGet() <= config.halfOpenMaxRequests
            }
        }
    }

    /**
     * Handle successful operation.
     */
    private fun onSuccess() {
        when (state) {
            CircuitState.HALF_OPEN -> {
                val successes = successCount.incrementAndGet()
                if (successes >= config.successThreshold) {
                    transitionTo(CircuitState.CLOSED)
                }
            }
            CircuitState.CLOSED -> {
                // Reset failure count on success
                failureCount.set(0)
            }
            else -> {}
        }
    }

    /**
     * Handle failed operation.
     */
    private fun onFailure(exception: Exception) {
        lastFailureTime.set(System.currentTimeMillis())

        when (state) {
            CircuitState.CLOSED -> {
                val failures = failureCount.incrementAndGet()
                if (failures >= config.failureThreshold) {
                    transitionTo(CircuitState.OPEN)
                }
            }
            CircuitState.HALF_OPEN -> {
                // Any failure in half-open goes back to open
                transitionTo(CircuitState.OPEN)
            }
            else -> {}
        }

        logger.warn("Circuit breaker '$name' recorded failure: ${exception.message}")
    }

    /**
     * Transition to a new state.
     */
    private fun transitionTo(newState: CircuitState) {
        val oldState = state
        state = newState
        lastStateChange.set(System.currentTimeMillis())

        when (newState) {
            CircuitState.CLOSED -> {
                failureCount.set(0)
                successCount.set(0)
                halfOpenRequests.set(0)
                logger.info("Circuit breaker '$name' CLOSED - service recovered")
            }
            CircuitState.OPEN -> {
                successCount.set(0)
                halfOpenRequests.set(0)
                logger.warn("Circuit breaker '$name' OPENED - blocking requests for ${config.openDurationMs}ms")
            }
            CircuitState.HALF_OPEN -> {
                successCount.set(0)
                halfOpenRequests.set(0)
                logger.info("Circuit breaker '$name' HALF-OPEN - testing service recovery")
            }
        }

        logger.debug("Circuit breaker '$name' state changed: $oldState -> $newState")
    }

    /**
     * Get remaining time in open state.
     */
    private fun remainingOpenTime(): Long {
        if (state != CircuitState.OPEN) return 0
        val elapsed = System.currentTimeMillis() - lastFailureTime.get()
        return maxOf(0, config.openDurationMs - elapsed)
    }

    /**
     * Get current circuit breaker status.
     */
    fun getStatus(): CircuitBreakerStatus {
        return CircuitBreakerStatus(
            name = name,
            state = state,
            failureCount = failureCount.get(),
            successCount = successCount.get(),
            lastFailureTime = lastFailureTime.get(),
            lastStateChange = lastStateChange.get()
        )
    }

    /**
     * Manually reset the circuit breaker.
     */
    fun reset() {
        transitionTo(CircuitState.CLOSED)
        logger.info("Circuit breaker '$name' manually reset")
    }
}

/**
 * Circuit breaker status for monitoring.
 */
data class CircuitBreakerStatus(
    val name: String,
    val state: CircuitState,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: Long,
    val lastStateChange: Long
)

/**
 * Exception thrown when circuit breaker is open.
 */
class CircuitBreakerOpenException(
    message: String,
    val retryAfterMs: Long
) : RuntimeException(message)

/**
 * Global circuit breaker registry for database connections.
 */
object CircuitBreakerRegistry {
    private val breakers = ConcurrentHashMap<String, CircuitBreaker>()
    private val defaultConfig = CircuitBreakerConfig()

    /**
     * Get or create a circuit breaker for a database connection.
     */
    fun getBreaker(key: String, config: CircuitBreakerConfig = defaultConfig): CircuitBreaker {
        return breakers.computeIfAbsent(key) { CircuitBreaker(key, config) }
    }

    /**
     * Get circuit breaker for a specific host/port/dbType combination.
     */
    fun getDatabaseBreaker(host: String, port: Int, dbType: String): CircuitBreaker {
        val key = "db:$dbType:$host:$port"
        return getBreaker(key)
    }

    /**
     * Get all circuit breaker statuses.
     */
    fun getAllStatuses(): List<CircuitBreakerStatus> {
        return breakers.values.map { it.getStatus() }
    }

    /**
     * Reset a specific circuit breaker.
     */
    fun reset(key: String) {
        breakers[key]?.reset()
    }

    /**
     * Reset all circuit breakers.
     */
    fun resetAll() {
        breakers.values.forEach { it.reset() }
    }
}
