package com.dbaccman.util

import com.dbaccman.exception.RateLimitException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Rate limiter configuration for different endpoints.
 */
data class RateLimitConfig(
    val maxRequests: Int,        // Maximum requests allowed
    val windowMs: Long,          // Time window in milliseconds
    val blockDurationMs: Long = 0  // How long to block after limit exceeded (0 = just reject)
)

/**
 * Tracks request counts for a single client/endpoint combination.
 */
private data class RateLimitBucket(
    val count: AtomicInteger = AtomicInteger(0),
    @Volatile var windowStart: Long = System.currentTimeMillis(),
    @Volatile var blockedUntil: Long = 0
)

/**
 * In-memory rate limiter with sliding window algorithm.
 */
object RateLimiter {
    private val logger = LoggerFactory.getLogger(RateLimiter::class.java)
    private val buckets = ConcurrentHashMap<String, RateLimitBucket>()
    private const val CLEANUP_INTERVAL_MS = 60_000L  // Cleanup every minute
    private const val BUCKET_EXPIRY_MS = 300_000L   // Remove buckets older than 5 minutes

    // Default configurations for different endpoint types
    val LOGIN_LIMIT = RateLimitConfig(
        maxRequests = 5,
        windowMs = 60_000,        // 5 attempts per minute
        blockDurationMs = 300_000  // Block for 5 minutes after exceeded
    )

    val API_LIMIT = RateLimitConfig(
        maxRequests = 100,
        windowMs = 60_000          // 100 requests per minute
    )

    val QUERY_LIMIT = RateLimitConfig(
        maxRequests = 30,
        windowMs = 60_000          // 30 queries per minute
    )

    init {
        startCleanupThread()
    }

    private fun startCleanupThread() {
        thread(isDaemon = true, name = "rate-limit-cleanup") {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(CLEANUP_INTERVAL_MS)
                    cleanupExpiredBuckets()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    logger.error("Error during rate limit cleanup", e)
                }
            }
        }
    }

    private fun cleanupExpiredBuckets() {
        val expireThreshold = System.currentTimeMillis() - BUCKET_EXPIRY_MS
        val expiredKeys = buckets.entries
            .filter { it.value.windowStart < expireThreshold && it.value.blockedUntil < System.currentTimeMillis() }
            .map { it.key }

        expiredKeys.forEach { buckets.remove(it) }

        if (expiredKeys.isNotEmpty()) {
            logger.debug("Cleaned up ${expiredKeys.size} expired rate limit buckets")
        }
    }

    /**
     * Checks if a request should be rate limited.
     *
     * @param key Unique identifier (e.g., IP address or user ID)
     * @param config Rate limit configuration
     * @return true if request is allowed, false if rate limited
     */
    fun checkLimit(key: String, config: RateLimitConfig): Boolean {
        val now = System.currentTimeMillis()
        val bucket = buckets.computeIfAbsent(key) { RateLimitBucket() }

        // Check if blocked
        if (bucket.blockedUntil > now) {
            return false
        }

        // Reset window if expired
        if (now - bucket.windowStart > config.windowMs) {
            bucket.windowStart = now
            bucket.count.set(0)
        }

        // Increment and check
        val count = bucket.count.incrementAndGet()
        if (count > config.maxRequests) {
            if (config.blockDurationMs > 0) {
                bucket.blockedUntil = now + config.blockDurationMs
                logger.warn("Rate limit exceeded for $key, blocked for ${config.blockDurationMs / 1000}s")
            }
            return false
        }

        return true
    }

    /**
     * Gets remaining requests for a key.
     */
    fun getRemainingRequests(key: String, config: RateLimitConfig): Int {
        val bucket = buckets[key] ?: return config.maxRequests
        val now = System.currentTimeMillis()

        if (now - bucket.windowStart > config.windowMs) {
            return config.maxRequests
        }

        return maxOf(0, config.maxRequests - bucket.count.get())
    }

    /**
     * Gets time until block expires (in seconds), or 0 if not blocked.
     */
    fun getBlockTimeRemaining(key: String): Int {
        val bucket = buckets[key] ?: return 0
        val remaining = bucket.blockedUntil - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }

    /**
     * Clears rate limit for a key (e.g., after successful login).
     */
    fun clearLimit(key: String) {
        buckets.remove(key)
    }
}

/**
 * Ktor plugin for rate limiting.
 */
fun Application.configureRateLimiting() {
    val logger = LoggerFactory.getLogger("RateLimiting")

    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        val clientIp = call.request.origin.remoteHost

        val (config, keyPrefix) = when {
            path.endsWith("/auth/login") -> RateLimiter.LOGIN_LIMIT to "login"
            path.contains("/query") -> RateLimiter.QUERY_LIMIT to "query"
            path.startsWith("/api/") -> RateLimiter.API_LIMIT to "api"
            else -> null to null
        }

        if (config != null && keyPrefix != null) {
            val key = "$keyPrefix:$clientIp"

            if (!RateLimiter.checkLimit(key, config)) {
                val blockTime = RateLimiter.getBlockTimeRemaining(key)
                logger.warn("Rate limit exceeded for $clientIp on $path")

                call.response.header("Retry-After", blockTime.toString())
                call.response.header("X-RateLimit-Limit", config.maxRequests.toString())
                call.response.header("X-RateLimit-Remaining", "0")

                throw RateLimitException(
                    message = "Too many requests. Please try again later.",
                    retryAfterSeconds = if (blockTime > 0) blockTime else (config.windowMs / 1000).toInt()
                )
            }

            // Add rate limit headers
            call.response.header("X-RateLimit-Limit", config.maxRequests.toString())
            call.response.header("X-RateLimit-Remaining", RateLimiter.getRemainingRequests(key, config).toString())
        }
    }
}
