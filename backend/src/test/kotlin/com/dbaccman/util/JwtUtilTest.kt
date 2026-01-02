package com.dbaccman.util

import com.dbaccman.dialect.DatabaseType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class JwtUtilTest {

    @Test
    @DisplayName("JwtUtil should be an object singleton")
    fun testJwtUtilIsSingleton() {
        assertNotNull(JwtUtil)
    }

    @Test
    @DisplayName("generateToken should create a non-empty token")
    fun testGenerateTokenNotEmpty() {
        val token = JwtUtil.generateToken(
            username = "testuser",
            role = "admin",
            sessionId = "session123",
            host = "localhost",
            port = 3306,
            dbType = DatabaseType.MYSQL
        )

        assertNotNull(token)
        assertTrue(token.isNotBlank())
    }

    @Test
    @DisplayName("generateToken should create valid JWT format")
    fun testGenerateTokenFormat() {
        val token = JwtUtil.generateToken(
            username = "testuser",
            sessionId = "session123",
            host = "localhost",
            port = 3306
        )

        // JWT has 3 parts separated by dots
        val parts = token.split(".")
        assertEquals(3, parts.size)
    }

    @Test
    @DisplayName("generateToken with default role should be user")
    fun testGenerateTokenDefaultRole() {
        val token = JwtUtil.generateToken(
            username = "normaluser",
            sessionId = "session456",
            host = "localhost",
            port = 3306
        )

        val role = JwtUtil.getRoleFromToken(token)
        assertEquals("user", role)
    }

    @Test
    @DisplayName("generateToken with default dbType should be MYSQL")
    fun testGenerateTokenDefaultDbType() {
        val token = JwtUtil.generateToken(
            username = "testuser",
            sessionId = "session789",
            host = "localhost",
            port = 3306
        )

        val dbType = JwtUtil.getDbTypeFromToken(token)
        assertEquals(DatabaseType.MYSQL, dbType)
    }

    @Test
    @DisplayName("validateToken should return true for valid token")
    fun testValidateTokenValid() {
        val token = JwtUtil.generateToken(
            username = "testuser",
            sessionId = "session123",
            host = "localhost",
            port = 3306
        )

        assertTrue(JwtUtil.validateToken(token))
    }

    @Test
    @DisplayName("validateToken should return false for invalid token")
    fun testValidateTokenInvalid() {
        assertFalse(JwtUtil.validateToken("invalid.token.here"))
    }

    @Test
    @DisplayName("validateToken should return false for empty token")
    fun testValidateTokenEmpty() {
        assertFalse(JwtUtil.validateToken(""))
    }

    @Test
    @DisplayName("validateToken should return false for malformed token")
    fun testValidateTokenMalformed() {
        assertFalse(JwtUtil.validateToken("not-a-jwt"))
    }

    @Test
    @DisplayName("getUsernameFromToken should return username")
    fun testGetUsernameFromToken() {
        val token = JwtUtil.generateToken(
            username = "extractuser",
            sessionId = "session123",
            host = "localhost",
            port = 3306
        )

        assertEquals("extractuser", JwtUtil.getUsernameFromToken(token))
    }

    @Test
    @DisplayName("getUsernameFromToken should return null for invalid token")
    fun testGetUsernameFromTokenInvalid() {
        assertNull(JwtUtil.getUsernameFromToken("invalid.token"))
    }

    @Test
    @DisplayName("getRoleFromToken should return role")
    fun testGetRoleFromToken() {
        val token = JwtUtil.generateToken(
            username = "admin",
            role = "admin",
            sessionId = "session123",
            host = "localhost",
            port = 3306
        )

        assertEquals("admin", JwtUtil.getRoleFromToken(token))
    }

    @Test
    @DisplayName("getRoleFromToken should return null for invalid token")
    fun testGetRoleFromTokenInvalid() {
        assertNull(JwtUtil.getRoleFromToken("invalid.token"))
    }

    @Test
    @DisplayName("getSessionIdFromToken should return sessionId")
    fun testGetSessionIdFromToken() {
        val token = JwtUtil.generateToken(
            username = "testuser",
            sessionId = "mysession123",
            host = "localhost",
            port = 3306
        )

        assertEquals("mysession123", JwtUtil.getSessionIdFromToken(token))
    }

    @Test
    @DisplayName("getSessionIdFromToken should return null for invalid token")
    fun testGetSessionIdFromTokenInvalid() {
        assertNull(JwtUtil.getSessionIdFromToken("invalid.token"))
    }

    @Test
    @DisplayName("getHostFromToken should return host")
    fun testGetHostFromToken() {
        val token = JwtUtil.generateToken(
            username = "testuser",
            sessionId = "session123",
            host = "db.example.com",
            port = 3306
        )

        assertEquals("db.example.com", JwtUtil.getHostFromToken(token))
    }

    @Test
    @DisplayName("getHostFromToken should return null for invalid token")
    fun testGetHostFromTokenInvalid() {
        assertNull(JwtUtil.getHostFromToken("invalid.token"))
    }

    @Test
    @DisplayName("getPortFromToken should return port")
    fun testGetPortFromToken() {
        val token = JwtUtil.generateToken(
            username = "testuser",
            sessionId = "session123",
            host = "localhost",
            port = 5432
        )

        assertEquals(5432, JwtUtil.getPortFromToken(token))
    }

    @Test
    @DisplayName("getPortFromToken should return null for invalid token")
    fun testGetPortFromTokenInvalid() {
        assertNull(JwtUtil.getPortFromToken("invalid.token"))
    }

    @Test
    @DisplayName("getDbTypeFromToken should return database type")
    fun testGetDbTypeFromToken() {
        val token = JwtUtil.generateToken(
            username = "testuser",
            sessionId = "session123",
            host = "localhost",
            port = 1521,
            dbType = DatabaseType.ORACLE
        )

        assertEquals(DatabaseType.ORACLE, JwtUtil.getDbTypeFromToken(token))
    }

    @Test
    @DisplayName("getDbTypeFromToken should return null for invalid token")
    fun testGetDbTypeFromTokenInvalid() {
        assertNull(JwtUtil.getDbTypeFromToken("invalid.token"))
    }

    @Test
    @DisplayName("Token should contain all expected claims")
    fun testTokenContainsAllClaims() {
        val token = JwtUtil.generateToken(
            username = "fulluser",
            role = "superadmin",
            sessionId = "fullsession",
            host = "oracle.example.com",
            port = 1521,
            dbType = DatabaseType.ORACLE
        )

        assertEquals("fulluser", JwtUtil.getUsernameFromToken(token))
        assertEquals("superadmin", JwtUtil.getRoleFromToken(token))
        assertEquals("fullsession", JwtUtil.getSessionIdFromToken(token))
        assertEquals("oracle.example.com", JwtUtil.getHostFromToken(token))
        assertEquals(1521, JwtUtil.getPortFromToken(token))
        assertEquals(DatabaseType.ORACLE, JwtUtil.getDbTypeFromToken(token))
    }

    @Test
    @DisplayName("Different tokens should have different values")
    fun testDifferentTokens() {
        val token1 = JwtUtil.generateToken(
            username = "user1",
            sessionId = "session1",
            host = "host1",
            port = 3306
        )

        val token2 = JwtUtil.generateToken(
            username = "user2",
            sessionId = "session2",
            host = "host2",
            port = 5432
        )

        assertNotEquals(token1, token2)
        assertNotEquals(JwtUtil.getUsernameFromToken(token1), JwtUtil.getUsernameFromToken(token2))
    }

    @Test
    @DisplayName("Token with PostgreSQL type")
    fun testTokenWithPostgreSQL() {
        val token = JwtUtil.generateToken(
            username = "pguser",
            sessionId = "pgsession",
            host = "pg.example.com",
            port = 5432,
            dbType = DatabaseType.POSTGRESQL
        )

        assertEquals(DatabaseType.POSTGRESQL, JwtUtil.getDbTypeFromToken(token))
        assertEquals(5432, JwtUtil.getPortFromToken(token))
    }
}
