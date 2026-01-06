package com.dbaccman.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class JwtConfigTest {

    companion object {
        // Use same values as application.conf to avoid conflicts with other tests
        // The algorithm and verifier are lazy vals that get cached on first access
        private const val TEST_SECRET = "dbaccman-jwt-secret-key-change-in-production"
        private const val TEST_ISSUER = "dbaccman"
        private const val TEST_AUDIENCE = "dbaccman-users"
        private const val TEST_REALM = "DBAccMan"
        private const val TEST_EXPIRATION_MS = 86400000L
    }

    @BeforeEach
    fun setUp() {
        // Initialize JwtConfig with same values as application.conf
        // This ensures no conflict with other tests that use JwtConfig
        JwtConfig.init(
            secret = TEST_SECRET,
            issuer = TEST_ISSUER,
            audience = TEST_AUDIENCE,
            realm = TEST_REALM,
            expirationMs = TEST_EXPIRATION_MS
        )
    }

    // ==================== Initialization Tests ====================

    @Nested
    @DisplayName("Initialization Tests")
    inner class InitializationTests {

        @Test
        @DisplayName("init should set secret correctly")
        fun testInitSecret() {
            assertEquals(TEST_SECRET, JwtConfig.secret)
        }

        @Test
        @DisplayName("init should set issuer correctly")
        fun testInitIssuer() {
            assertEquals(TEST_ISSUER, JwtConfig.issuer)
        }

        @Test
        @DisplayName("init should set audience correctly")
        fun testInitAudience() {
            assertEquals(TEST_AUDIENCE, JwtConfig.audience)
        }

        @Test
        @DisplayName("init should set realm correctly")
        fun testInitRealm() {
            assertEquals(TEST_REALM, JwtConfig.realm)
        }

        @Test
        @DisplayName("init should set expirationMs correctly")
        fun testInitExpirationMs() {
            assertEquals(TEST_EXPIRATION_MS, JwtConfig.expirationMs)
        }

        @Test
        @DisplayName("re-init should update simple values (not lazy vals)")
        fun testReInit() {
            // Note: algorithm and verifier are lazy vals that cannot be updated
            // But secret, issuer, audience, realm, expirationMs can be updated
            JwtConfig.init(
                secret = "new-secret",
                issuer = "new-issuer",
                audience = "new-audience",
                realm = "new-realm",
                expirationMs = 7200000L
            )

            assertEquals("new-secret", JwtConfig.secret)
            assertEquals("new-issuer", JwtConfig.issuer)
            assertEquals("new-audience", JwtConfig.audience)
            assertEquals("new-realm", JwtConfig.realm)
            assertEquals(7200000L, JwtConfig.expirationMs)

            // Restore original values for other tests
            JwtConfig.init(
                secret = TEST_SECRET,
                issuer = TEST_ISSUER,
                audience = TEST_AUDIENCE,
                realm = TEST_REALM,
                expirationMs = TEST_EXPIRATION_MS
            )
        }
    }

    // ==================== Algorithm Tests ====================

    @Nested
    @DisplayName("Algorithm Tests")
    inner class AlgorithmTests {

        @Test
        @DisplayName("algorithm should be HMAC256")
        fun testAlgorithmType() {
            val algorithm = JwtConfig.algorithm
            assertNotNull(algorithm)
            assertEquals("HS256", algorithm.name)
        }

        @Test
        @DisplayName("algorithm should be consistent")
        fun testAlgorithmConsistency() {
            val algorithm1 = JwtConfig.algorithm
            val algorithm2 = JwtConfig.algorithm
            assertSame(algorithm1, algorithm2)
        }
    }

    // ==================== Verifier Tests ====================

    @Nested
    @DisplayName("Verifier Tests")
    inner class VerifierTests {

        @Test
        @DisplayName("verifier should be created successfully")
        fun testVerifierCreation() {
            val verifier = JwtConfig.verifier
            assertNotNull(verifier)
        }

        @Test
        @DisplayName("verifier should be consistent (lazy initialized)")
        fun testVerifierConsistency() {
            val verifier1 = JwtConfig.verifier
            val verifier2 = JwtConfig.verifier
            assertSame(verifier1, verifier2)
        }

        @Test
        @DisplayName("verifier should verify valid token")
        fun testVerifierWithValidToken() {
            // Create a valid token
            val token = JWT.create()
                .withAudience(JwtConfig.audience)
                .withIssuer(JwtConfig.issuer)
                .withClaim("username", "testuser")
                .sign(JwtConfig.algorithm)

            // Verify the token
            val decodedJwt = JwtConfig.verifier.verify(token)
            assertNotNull(decodedJwt)
            assertEquals("testuser", decodedJwt.getClaim("username").asString())
        }

        @Test
        @DisplayName("verifier should reject token with wrong issuer")
        fun testVerifierRejectsWrongIssuer() {
            val token = JWT.create()
                .withAudience(JwtConfig.audience)
                .withIssuer("wrong-issuer")
                .withClaim("username", "testuser")
                .sign(JwtConfig.algorithm)

            assertThrows(com.auth0.jwt.exceptions.JWTVerificationException::class.java) {
                JwtConfig.verifier.verify(token)
            }
        }

        @Test
        @DisplayName("verifier should reject token with wrong audience")
        fun testVerifierRejectsWrongAudience() {
            val token = JWT.create()
                .withAudience("wrong-audience")
                .withIssuer(JwtConfig.issuer)
                .withClaim("username", "testuser")
                .sign(JwtConfig.algorithm)

            assertThrows(com.auth0.jwt.exceptions.JWTVerificationException::class.java) {
                JwtConfig.verifier.verify(token)
            }
        }

        @Test
        @DisplayName("verifier should reject token signed with wrong secret")
        fun testVerifierRejectsWrongSecret() {
            val wrongAlgorithm = Algorithm.HMAC256("wrong-secret")
            val token = JWT.create()
                .withAudience(JwtConfig.audience)
                .withIssuer(JwtConfig.issuer)
                .withClaim("username", "testuser")
                .sign(wrongAlgorithm)

            assertThrows(com.auth0.jwt.exceptions.JWTVerificationException::class.java) {
                JwtConfig.verifier.verify(token)
            }
        }
    }

    // ==================== Token Creation Tests ====================

    @Nested
    @DisplayName("Token Creation Tests")
    inner class TokenCreationTests {

        @Test
        @DisplayName("should create token with multiple claims")
        fun testTokenWithMultipleClaims() {
            val token = JWT.create()
                .withAudience(JwtConfig.audience)
                .withIssuer(JwtConfig.issuer)
                .withClaim("username", "admin")
                .withClaim("role", "administrator")
                .withClaim("sessionId", "abc123")
                .sign(JwtConfig.algorithm)

            val decoded = JwtConfig.verifier.verify(token)
            assertEquals("admin", decoded.getClaim("username").asString())
            assertEquals("administrator", decoded.getClaim("role").asString())
            assertEquals("abc123", decoded.getClaim("sessionId").asString())
        }

        @Test
        @DisplayName("should create token with numeric claims")
        fun testTokenWithNumericClaims() {
            val token = JWT.create()
                .withAudience(JwtConfig.audience)
                .withIssuer(JwtConfig.issuer)
                .withClaim("userId", 12345)
                .withClaim("dbPort", 3306)
                .sign(JwtConfig.algorithm)

            val decoded = JwtConfig.verifier.verify(token)
            assertEquals(12345, decoded.getClaim("userId").asInt())
            assertEquals(3306, decoded.getClaim("dbPort").asInt())
        }

        @Test
        @DisplayName("should create token with boolean claims")
        fun testTokenWithBooleanClaims() {
            val token = JWT.create()
                .withAudience(JwtConfig.audience)
                .withIssuer(JwtConfig.issuer)
                .withClaim("isAdmin", true)
                .withClaim("isActive", false)
                .sign(JwtConfig.algorithm)

            val decoded = JwtConfig.verifier.verify(token)
            assertTrue(decoded.getClaim("isAdmin").asBoolean())
            assertFalse(decoded.getClaim("isActive").asBoolean())
        }
    }

    // ==================== Default Value Tests ====================

    @Nested
    @DisplayName("Default Value Tests")
    inner class DefaultValueTests {

        @Test
        @DisplayName("expirationMs should be 24 hours (86400000ms)")
        fun testDefaultExpiration() {
            // This tests the value set by our setUp init (same as application.conf)
            assertEquals(86400000L, JwtConfig.expirationMs)
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Case Tests")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty string claims")
        fun testEmptyStringClaim() {
            val token = JWT.create()
                .withAudience(JwtConfig.audience)
                .withIssuer(JwtConfig.issuer)
                .withClaim("username", "")
                .sign(JwtConfig.algorithm)

            val decoded = JwtConfig.verifier.verify(token)
            assertEquals("", decoded.getClaim("username").asString())
        }

        @Test
        @DisplayName("should handle special characters in claims")
        fun testSpecialCharactersClaim() {
            val token = JWT.create()
                .withAudience(JwtConfig.audience)
                .withIssuer(JwtConfig.issuer)
                .withClaim("username", "user@domain.com")
                .withClaim("dbHost", "192.168.1.100:3306")
                .sign(JwtConfig.algorithm)

            val decoded = JwtConfig.verifier.verify(token)
            assertEquals("user@domain.com", decoded.getClaim("username").asString())
            assertEquals("192.168.1.100:3306", decoded.getClaim("dbHost").asString())
        }

        @Test
        @DisplayName("should handle unicode characters in claims")
        fun testUnicodeCharactersClaim() {
            val token = JWT.create()
                .withAudience(JwtConfig.audience)
                .withIssuer(JwtConfig.issuer)
                .withClaim("username", "사용자")
                .withClaim("message", "こんにちは")
                .sign(JwtConfig.algorithm)

            val decoded = JwtConfig.verifier.verify(token)
            assertEquals("사용자", decoded.getClaim("username").asString())
            assertEquals("こんにちは", decoded.getClaim("message").asString())
        }
    }
}
