package com.dbaccman.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RateLimiter Tests")
class RateLimiterTest {

    @BeforeEach
    fun setup() {
        // Clear any existing rate limits
        RateLimiter.clearLimit("test:client1")
        RateLimiter.clearLimit("test:client2")
    }

    @Test
    @DisplayName("Should allow requests within limit")
    fun testAllowWithinLimit() {
        val config = RateLimitConfig(
            maxRequests = 5,
            windowMs = 60_000
        )

        // First 5 requests should be allowed
        repeat(5) { i ->
            assertTrue(
                RateLimiter.checkLimit("test:client1", config),
                "Request ${i + 1} should be allowed"
            )
        }
    }

    @Test
    @DisplayName("Should block requests exceeding limit")
    fun testBlockExceedingLimit() {
        val config = RateLimitConfig(
            maxRequests = 3,
            windowMs = 60_000
        )

        // First 3 requests allowed
        repeat(3) {
            assertTrue(RateLimiter.checkLimit("test:client2", config))
        }

        // 4th request should be blocked
        assertFalse(RateLimiter.checkLimit("test:client2", config))
    }

    @Test
    @DisplayName("Should track remaining requests correctly")
    fun testRemainingRequests() {
        val config = RateLimitConfig(
            maxRequests = 5,
            windowMs = 60_000
        )

        val key = "test:remaining"
        RateLimiter.clearLimit(key)

        // Initially should have max requests available
        assertEquals(5, RateLimiter.getRemainingRequests(key, config))

        // After 2 requests
        RateLimiter.checkLimit(key, config)
        RateLimiter.checkLimit(key, config)
        assertEquals(3, RateLimiter.getRemainingRequests(key, config))

        // After all requests used
        repeat(3) { RateLimiter.checkLimit(key, config) }
        assertEquals(0, RateLimiter.getRemainingRequests(key, config))
    }

    @Test
    @DisplayName("Should handle different clients independently")
    fun testIndependentClients() {
        val config = RateLimitConfig(
            maxRequests = 2,
            windowMs = 60_000
        )

        val key1 = "test:independent1"
        val key2 = "test:independent2"
        RateLimiter.clearLimit(key1)
        RateLimiter.clearLimit(key2)

        // Client 1 uses all requests
        assertTrue(RateLimiter.checkLimit(key1, config))
        assertTrue(RateLimiter.checkLimit(key1, config))
        assertFalse(RateLimiter.checkLimit(key1, config))

        // Client 2 should still have full quota
        assertTrue(RateLimiter.checkLimit(key2, config))
        assertTrue(RateLimiter.checkLimit(key2, config))
        assertFalse(RateLimiter.checkLimit(key2, config))
    }

    @Test
    @DisplayName("Should clear limit for specific key")
    fun testClearLimit() {
        val config = RateLimitConfig(
            maxRequests = 2,
            windowMs = 60_000
        )

        val key = "test:clearable"
        RateLimiter.clearLimit(key)

        // Use up quota
        assertTrue(RateLimiter.checkLimit(key, config))
        assertTrue(RateLimiter.checkLimit(key, config))
        assertFalse(RateLimiter.checkLimit(key, config))

        // Clear and try again
        RateLimiter.clearLimit(key)
        assertTrue(RateLimiter.checkLimit(key, config))
    }

    @Test
    @DisplayName("Block duration should be tracked correctly")
    fun testBlockDuration() {
        val config = RateLimitConfig(
            maxRequests = 1,
            windowMs = 1_000,
            blockDurationMs = 5_000
        )

        val key = "test:blocked"
        RateLimiter.clearLimit(key)

        // Use quota and trigger block
        assertTrue(RateLimiter.checkLimit(key, config))
        assertFalse(RateLimiter.checkLimit(key, config))

        // Should report block time remaining
        val blockTime = RateLimiter.getBlockTimeRemaining(key)
        assertTrue(blockTime > 0, "Block time should be positive")
        assertTrue(blockTime <= 5, "Block time should be <= 5 seconds")
    }

    @Test
    @DisplayName("Predefined configurations should have reasonable values")
    fun testPredefinedConfigs() {
        // LOGIN_LIMIT
        assertEquals(5, RateLimiter.LOGIN_LIMIT.maxRequests)
        assertEquals(60_000, RateLimiter.LOGIN_LIMIT.windowMs)
        assertEquals(300_000, RateLimiter.LOGIN_LIMIT.blockDurationMs)

        // API_LIMIT
        assertEquals(100, RateLimiter.API_LIMIT.maxRequests)
        assertEquals(60_000, RateLimiter.API_LIMIT.windowMs)

        // QUERY_LIMIT
        assertEquals(30, RateLimiter.QUERY_LIMIT.maxRequests)
        assertEquals(60_000, RateLimiter.QUERY_LIMIT.windowMs)
    }
}
