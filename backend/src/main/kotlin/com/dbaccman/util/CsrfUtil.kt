package com.dbaccman.util

import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * CSRF (Cross-Site Request Forgery) protection utility.
 * Generates and validates CSRF tokens to prevent unauthorized state-changing requests.
 */
object CsrfUtil {
    private val random = SecureRandom()
    private val tokenStore = ConcurrentHashMap<String, Long>() // token -> expiry timestamp
    private const val TOKEN_VALIDITY_MS = 3600000L // 1 hour
    private const val MAX_TOKENS = 10000 // Maximum tokens to prevent memory issues

    /**
     * Generates a new CSRF token.
     * @return A secure random token string.
     */
    fun generateToken(): String {
        cleanExpiredTokens()

        // Limit token store size
        if (tokenStore.size >= MAX_TOKENS) {
            // Remove oldest 10% of tokens
            val tokensToRemove = tokenStore.entries
                .sortedBy { it.value }
                .take(MAX_TOKENS / 10)
                .map { it.key }
            tokensToRemove.forEach { tokenStore.remove(it) }
        }

        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        tokenStore[token] = System.currentTimeMillis() + TOKEN_VALIDITY_MS
        return token
    }

    /**
     * Validates a CSRF token.
     * @param token The token to validate.
     * @return true if the token is valid and not expired, false otherwise.
     */
    fun validateToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false

        val expiry = tokenStore[token] ?: return false

        if (System.currentTimeMillis() > expiry) {
            tokenStore.remove(token)
            return false
        }

        return true
    }

    /**
     * Invalidates a specific token (e.g., after use for one-time tokens).
     * @param token The token to invalidate.
     */
    fun invalidateToken(token: String) {
        tokenStore.remove(token)
    }

    /**
     * Removes expired tokens from the store.
     */
    private fun cleanExpiredTokens() {
        val now = System.currentTimeMillis()
        tokenStore.entries.removeIf { it.value < now }
    }

    /**
     * Gets the current token count (for monitoring).
     */
    fun getTokenCount(): Int = tokenStore.size

    /**
     * Clears all tokens (for testing or shutdown).
     */
    fun clearAllTokens() {
        tokenStore.clear()
    }
}
