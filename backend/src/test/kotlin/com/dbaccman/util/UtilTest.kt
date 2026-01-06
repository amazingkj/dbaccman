package com.dbaccman.util

import com.dbaccman.config.JwtConfig
import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.DatabaseType
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.dialect.PostgreSQLDialect
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.slf4j.Logger
import java.sql.Connection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UtilTest {

    @BeforeAll
    fun setUp() {
        // Initialize JwtConfig with same values as application.conf
        JwtConfig.init(
            secret = "dbaccman-jwt-secret-key-change-in-production",
            issuer = "dbaccman",
            audience = "dbaccman-users",
            realm = "DBAccMan",
            expirationMs = 86400000L
        )
    }

    @Nested
    @DisplayName("AuditLogger Tests")
    inner class AuditLoggerTests {

        @BeforeEach
        fun clearLogs() {
            AuditLogger.clearLogs()
        }

        @Test
        @DisplayName("log should store entry in memory")
        fun testLogStoresEntry() {
            AuditLogger.log("LOGIN", "User logged in", "testuser", "192.168.1.1")

            val logs = AuditLogger.getRecentLogs(limit = 10)
            assertEquals(1, logs.size)

            val logEntry = logs[0]
            assertEquals("LOGIN", logEntry.action)
            assertEquals("User logged in", logEntry.message)
            assertEquals("testuser", logEntry.user)
            assertEquals("192.168.1.1", logEntry.ipAddress)
            assertNotNull(logEntry.timestamp)
        }

        @Test
        @DisplayName("log should work with null user and ipAddress")
        fun testLogWithNullValues() {
            AuditLogger.log("SYSTEM_EVENT", "System started")

            val logs = AuditLogger.getRecentLogs()
            assertEquals(1, logs.size)

            val logEntry = logs[0]
            assertEquals("SYSTEM_EVENT", logEntry.action)
            assertEquals("System started", logEntry.message)
            assertNull(logEntry.user)
            assertNull(logEntry.ipAddress)
        }

        @Test
        @DisplayName("logWithTarget should include target and details")
        fun testLogWithTarget() {
            AuditLogger.logWithTarget(
                action = "USER_CREATE",
                user = "admin",
                target = "newuser",
                details = "Created with role: user",
                ipAddress = "10.0.0.1"
            )

            val logs = AuditLogger.getRecentLogs()
            assertEquals(1, logs.size)

            val logEntry = logs[0]
            assertEquals("USER_CREATE", logEntry.action)
            assertEquals("admin", logEntry.user)
            assertEquals("newuser", logEntry.target)
            assertEquals("Created with role: user", logEntry.details)
            assertEquals("10.0.0.1", logEntry.ipAddress)
        }

        @Test
        @DisplayName("logWithTarget should work with null details and ipAddress")
        fun testLogWithTargetNullValues() {
            AuditLogger.logWithTarget(
                action = "DELETE",
                user = "admin",
                target = "olduser"
            )

            val logs = AuditLogger.getRecentLogs()
            assertEquals(1, logs.size)

            val logEntry = logs[0]
            assertEquals("DELETE", logEntry.action)
            assertEquals("olduser", logEntry.target)
            assertNull(logEntry.details)
            assertNull(logEntry.ipAddress)
        }

        @Test
        @DisplayName("logQuery should log successful query execution")
        fun testLogQuerySuccess() {
            AuditLogger.logQuery(
                user = "dbuser",
                query = "SELECT * FROM users WHERE id = 1",
                database = "testdb",
                ipAddress = "172.16.0.1",
                success = true
            )

            val logs = AuditLogger.getRecentLogs()
            assertEquals(1, logs.size)

            val logEntry = logs[0]
            assertEquals("QUERY_EXECUTE", logEntry.action)
            assertEquals("dbuser", logEntry.user)
            assertEquals("testdb", logEntry.target)
            assertEquals("SELECT * FROM users WHERE id = 1", logEntry.message)
            assertEquals("SUCCESS", logEntry.details)
        }

        @Test
        @DisplayName("logQuery should log failed query execution")
        fun testLogQueryFailure() {
            AuditLogger.logQuery(
                user = "dbuser",
                query = "INVALID SQL STATEMENT",
                database = "testdb",
                ipAddress = "172.16.0.1",
                success = false,
                error = "Syntax error"
            )

            val logs = AuditLogger.getRecentLogs()
            assertEquals(1, logs.size)

            val logEntry = logs[0]
            assertEquals("QUERY_EXECUTE", logEntry.action)
            assertEquals("FAILED: Syntax error", logEntry.details)
        }

        @Test
        @DisplayName("logQuery should truncate long queries to 200 characters")
        fun testLogQueryTruncation() {
            val longQuery = "SELECT * FROM users WHERE name = 'test' AND " + "x".repeat(200)

            AuditLogger.logQuery(
                user = "dbuser",
                query = longQuery,
                database = "testdb",
                ipAddress = "172.16.0.1",
                success = true
            )

            val logs = AuditLogger.getRecentLogs()
            assertEquals(1, logs.size)

            val logEntry = logs[0]
            assertEquals(200, logEntry.message.length)
            assertTrue(logEntry.message.startsWith("SELECT * FROM users"))
        }

        @Test
        @DisplayName("logQuery should work with null database and ipAddress")
        fun testLogQueryNullValues() {
            AuditLogger.logQuery(
                user = "dbuser",
                query = "SELECT 1",
                database = null,
                ipAddress = null,
                success = true
            )

            val logs = AuditLogger.getRecentLogs()
            assertEquals(1, logs.size)

            val logEntry = logs[0]
            assertNull(logEntry.target)
            assertNull(logEntry.ipAddress)
        }

        @Test
        @DisplayName("getRecentLogs should limit returned logs")
        fun testGetRecentLogsLimit() {
            // Add 50 logs
            repeat(50) { i ->
                AuditLogger.log("ACTION_$i", "Message $i")
            }

            val logs = AuditLogger.getRecentLogs(limit = 10)
            assertEquals(10, logs.size)
        }

        @Test
        @DisplayName("getRecentLogs should filter by action")
        fun testGetRecentLogsFilterByAction() {
            AuditLogger.log("LOGIN", "User logged in")
            AuditLogger.log("LOGOUT", "User logged out")
            AuditLogger.log("LOGIN", "Another login")

            val loginLogs = AuditLogger.getRecentLogs(action = "LOGIN")
            assertEquals(2, loginLogs.size)
            assertTrue(loginLogs.all { it.action == "LOGIN" })
        }

        @Test
        @DisplayName("getRecentLogs should filter by user case-insensitively")
        fun testGetRecentLogsFilterByUser() {
            AuditLogger.log("LOGIN", "Message 1", user = "TestUser")
            AuditLogger.log("LOGIN", "Message 2", user = "OtherUser")
            AuditLogger.log("LOGIN", "Message 3", user = "testuser")

            val userLogs = AuditLogger.getRecentLogs(user = "test")
            assertEquals(2, userLogs.size)
        }

        @Test
        @DisplayName("getRecentLogs should filter by both action and user")
        fun testGetRecentLogsFilterByActionAndUser() {
            AuditLogger.log("LOGIN", "Message 1", user = "admin")
            AuditLogger.log("LOGOUT", "Message 2", user = "admin")
            AuditLogger.log("LOGIN", "Message 3", user = "user")

            val logs = AuditLogger.getRecentLogs(action = "LOGIN", user = "admin")
            assertEquals(1, logs.size)
            assertEquals("LOGIN", logs[0].action)
            assertEquals("admin", logs[0].user)
        }

        @Test
        @DisplayName("getRecentLogs should return logs in reverse chronological order")
        fun testGetRecentLogsOrder() {
            AuditLogger.log("FIRST", "First message")
            Thread.sleep(10) // Ensure different timestamps
            AuditLogger.log("SECOND", "Second message")
            Thread.sleep(10)
            AuditLogger.log("THIRD", "Third message")

            val logs = AuditLogger.getRecentLogs()
            assertEquals(3, logs.size)
            assertEquals("THIRD", logs[0].action)
            assertEquals("SECOND", logs[1].action)
            assertEquals("FIRST", logs[2].action)
        }

        @Test
        @DisplayName("clearLogs should remove all entries")
        fun testClearLogs() {
            repeat(10) { i ->
                AuditLogger.log("ACTION_$i", "Message $i")
            }

            assertEquals(10, AuditLogger.getRecentLogs().size)

            AuditLogger.clearLogs()

            assertEquals(0, AuditLogger.getRecentLogs().size)
        }

        @Test
        @DisplayName("AuditLogger should maintain max 1000 entries")
        fun testMaxLogEntries() {
            // Add 1500 entries
            repeat(1500) { i ->
                AuditLogger.log("ACTION_$i", "Message $i")
            }

            val logs = AuditLogger.getRecentLogs(limit = 2000)
            assertEquals(1000, logs.size)

            // Most recent should be ACTION_1499
            assertEquals("ACTION_1499", logs[0].action)
        }

        @Test
        @DisplayName("timestamp should be formatted correctly")
        fun testTimestampFormat() {
            AuditLogger.log("TEST", "Test message")

            val logs = AuditLogger.getRecentLogs()
            val timestamp = logs[0].timestamp

            // Verify format yyyy-MM-dd HH:mm:ss
            assertDoesNotThrow {
                LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }
        }
    }

    @Nested
    @DisplayName("RouteUtils Tests")
    inner class RouteUtilsTests {

        @BeforeEach
        fun setUpRouteUtilsTests() {
            mockkStatic(ApplicationCall::isAdmin)
        }

        @AfterEach
        fun tearDownRouteUtilsTests() {
            unmockkStatic(ApplicationCall::isAdmin)
        }

        @Test
        @DisplayName("AdminAccessRequiredException should be throwable")
        fun testAdminAccessRequiredException() {
            val exception = AdminAccessRequiredException()
            assertEquals("Admin access required", exception.message)
        }

        @Test
        @DisplayName("SessionExpiredException should be throwable")
        fun testSessionExpiredException() {
            val exception = SessionExpiredException()
            assertEquals("Session expired", exception.message)
        }

        @Test
        @DisplayName("requireAdmin should throw when not admin")
        fun testRequireAdminThrowsForNonAdmin() = runBlocking {
            val call = mockk<ApplicationCall>()
            every { call.isAdmin() } returns false

            assertThrows<AdminAccessRequiredException> {
                call.requireAdmin()
            }
        }

        @Test
        @DisplayName("requireAdmin should not throw when admin")
        fun testRequireAdminPassesForAdmin() = runBlocking {
            val call = mockk<ApplicationCall>()
            every { call.isAdmin() } returns true

            assertDoesNotThrow {
                call.requireAdmin()
            }
        }

        @Test
        @DisplayName("handleAdminRoute should execute block when admin")
        fun testHandleAdminRouteSuccess() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.isAdmin() } returns true

            var blockExecuted = false

            call.handleAdminRoute {
                blockExecuted = true
            }

            assertTrue(blockExecuted)
            coVerify(exactly = 0) { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) }
        }

        @Test
        @DisplayName("handleAdminRoute should return 403 when not admin")
        fun testHandleAdminRouteNotAdmin() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.isAdmin() } returns false
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAdminRoute {
                fail("Block should not execute")
            }

            coVerify { call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required")) }
        }

        @Test
        @DisplayName("handleAdminRoute should return 401 when session expired")
        fun testHandleAdminRouteSessionExpired() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.isAdmin() } throws SessionExpiredException()
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAdminRoute {
                fail("Block should not execute")
            }

            coVerify { call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Session expired")) }
        }

        @Test
        @DisplayName("handleAdminRoute should return 401 for IllegalStateException")
        fun testHandleAdminRouteIllegalState() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.isAdmin() } returns true
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAdminRoute {
                throw IllegalStateException("Auth error")
            }

            coVerify { call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Auth error")) }
        }

        @Test
        @DisplayName("handleAdminRoute should return 500 for general exceptions")
        fun testHandleAdminRouteGeneralException() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.isAdmin() } returns true
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAdminRoute {
                throw RuntimeException("Something went wrong")
            }

            coVerify { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Something went wrong")) }
        }

        @Test
        @DisplayName("handleAdminRoute should use custom error message")
        fun testHandleAdminRouteCustomErrorMessage() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.isAdmin() } returns true
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAdminRoute(errorMessage = "Custom error") {
                throw RuntimeException()
            }

            coVerify { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Custom error")) }
        }

        @Test
        @DisplayName("handleAdminRoute should log errors when logger provided")
        fun testHandleAdminRouteWithLogger() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            val logger = mockk<Logger>(relaxed = true)
            every { call.isAdmin() } returns true
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAdminRoute(logger = logger, errorMessage = "Test error") {
                throw RuntimeException("Test exception")
            }

            verify { logger.error("Test error", any<RuntimeException>()) }
        }

        @Test
        @DisplayName("handleAuthenticatedRoute should execute block successfully")
        fun testHandleAuthenticatedRouteSuccess() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            var blockExecuted = false

            call.handleAuthenticatedRoute {
                blockExecuted = true
            }

            assertTrue(blockExecuted)
            coVerify(exactly = 0) { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) }
        }

        @Test
        @DisplayName("handleAuthenticatedRoute should return 401 when session expired")
        fun testHandleAuthenticatedRouteSessionExpired() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAuthenticatedRoute {
                throw SessionExpiredException()
            }

            coVerify { call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Session expired")) }
        }

        @Test
        @DisplayName("handleAuthenticatedRoute should return 401 for IllegalStateException")
        fun testHandleAuthenticatedRouteIllegalState() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAuthenticatedRoute {
                throw IllegalStateException("Auth failed")
            }

            coVerify { call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Auth failed")) }
        }

        @Test
        @DisplayName("handleAuthenticatedRoute should return 500 for general exceptions")
        fun testHandleAuthenticatedRouteGeneralException() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAuthenticatedRoute {
                throw RuntimeException("General error")
            }

            coVerify { call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "General error")) }
        }

        @Test
        @DisplayName("handleAdminMutationRoute should execute block when admin")
        fun testHandleAdminMutationRouteSuccess() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.isAdmin() } returns true
            var blockExecuted = false

            call.handleAdminMutationRoute {
                blockExecuted = true
            }

            assertTrue(blockExecuted)
        }

        @Test
        @DisplayName("handleAdminMutationRoute should return 400 for general exceptions")
        fun testHandleAdminMutationRouteBadRequest() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.isAdmin() } returns true
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAdminMutationRoute {
                throw RuntimeException("Invalid input")
            }

            coVerify { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid input")) }
        }

        @Test
        @DisplayName("handleAdminMutationRoute should return 403 when not admin")
        fun testHandleAdminMutationRouteNotAdmin() = runBlocking {
            val call = mockk<ApplicationCall>(relaxed = true)
            every { call.isAdmin() } returns false
            coEvery { call.respond(any<HttpStatusCode>(), any<Map<String, String>>()) } just Runs

            call.handleAdminMutationRoute {
                fail("Block should not execute")
            }

            coVerify { call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required")) }
        }
    }

    @Nested
    @DisplayName("RequestContext Tests")
    inner class RequestContextTests {

        @Test
        @DisplayName("getSessionId should extract session ID from JWT")
        fun testGetSessionId() {
            val token = JwtUtil.generateToken(
                username = "testuser",
                sessionId = "session123",
                host = "localhost",
                port = 3306
            )

            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("sessionId").asString() } returns "session123"

            assertEquals("session123", call.getSessionId())
        }

        @Test
        @DisplayName("getSessionId should throw when no principal")
        fun testGetSessionIdNoPrincipal() {
            val call = mockk<ApplicationCall>()
            every { call.principal<JWTPrincipal>() } returns null

            val exception = assertThrows<IllegalStateException> {
                call.getSessionId()
            }
            assertEquals("No JWT principal found", exception.message)
        }

        @Test
        @DisplayName("getSessionId should throw when no session ID in token")
        fun testGetSessionIdNoSessionId() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("sessionId").asString() } returns null

            val exception = assertThrows<IllegalStateException> {
                call.getSessionId()
            }
            assertEquals("No session ID in token", exception.message)
        }

        @Test
        @DisplayName("getUsername should extract username from JWT")
        fun testGetUsername() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("username").asString() } returns "testuser"

            assertEquals("testuser", call.getUsername())
        }

        @Test
        @DisplayName("getUsername should throw when no principal")
        fun testGetUsernameNoPrincipal() {
            val call = mockk<ApplicationCall>()
            every { call.principal<JWTPrincipal>() } returns null

            assertThrows<IllegalStateException> {
                call.getUsername()
            }
        }

        @Test
        @DisplayName("getUsername should throw when no username in token")
        fun testGetUsernameNoUsername() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("username").asString() } returns null

            assertThrows<IllegalStateException> {
                call.getUsername()
            }
        }

        @Test
        @DisplayName("getRole should extract role from JWT")
        fun testGetRole() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("role").asString() } returns "admin"

            assertEquals("admin", call.getRole())
        }

        @Test
        @DisplayName("getRole should throw when no role in token")
        fun testGetRoleNoRole() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("role").asString() } returns null

            assertThrows<IllegalStateException> {
                call.getRole()
            }
        }

        @Test
        @DisplayName("getDbHost should extract database host from JWT")
        fun testGetDbHost() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("dbHost").asString() } returns "db.example.com"

            assertEquals("db.example.com", call.getDbHost())
        }

        @Test
        @DisplayName("getDbHost should throw when no dbHost in token")
        fun testGetDbHostNoHost() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("dbHost").asString() } returns null

            assertThrows<IllegalStateException> {
                call.getDbHost()
            }
        }

        @Test
        @DisplayName("getDbPort should extract database port from JWT")
        fun testGetDbPort() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("dbPort").asInt() } returns 5432

            assertEquals(5432, call.getDbPort())
        }

        @Test
        @DisplayName("getDbPort should throw when no dbPort in token")
        fun testGetDbPortNoPort() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("dbPort").asInt() } returns null

            assertThrows<IllegalStateException> {
                call.getDbPort()
            }
        }

        @Test
        @DisplayName("getDbType should extract database type from JWT")
        fun testGetDbType() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("dbType").asString() } returns "ORACLE"

            assertEquals(DatabaseType.ORACLE, call.getDbType())
        }

        @Test
        @DisplayName("getDbType should default to MYSQL when no dbType in token")
        fun testGetDbTypeDefaultMySQL() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("dbType").asString() } returns null

            assertEquals(DatabaseType.MYSQL, call.getDbType())
        }

        @Test
        @DisplayName("getDbType should parse POSTGRESQL")
        fun testGetDbTypePostgreSQL() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("dbType").asString() } returns "POSTGRESQL"

            assertEquals(DatabaseType.POSTGRESQL, call.getDbType())
        }

        @Test
        @DisplayName("isAdmin should return true for admin role")
        fun testIsAdminTrue() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("role").asString() } returns "admin"

            assertTrue(call.isAdmin())
        }

        @Test
        @DisplayName("isAdmin should return false for non-admin role")
        fun testIsAdminFalse() {
            val principal = mockk<JWTPrincipal>()
            val call = mockk<ApplicationCall>()

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("role").asString() } returns "user"

            assertFalse(call.isAdmin())
        }

        @Test
        @DisplayName("getClientIp should extract X-Forwarded-For header")
        fun testGetClientIpForwardedFor() {
            val call = mockk<ApplicationCall>()
            val request = mockk<ApplicationRequest>()
            val headers = mockk<Headers>()

            every { call.request } returns request
            every { request.headers } returns headers
            every { headers["X-Forwarded-For"] } returns "203.0.113.1, 198.51.100.1"

            assertEquals("203.0.113.1", call.getClientIp())
        }

        @Test
        @DisplayName("getClientIp should extract single X-Forwarded-For value")
        fun testGetClientIpForwardedForSingle() {
            val call = mockk<ApplicationCall>()
            val request = mockk<ApplicationRequest>()
            val headers = mockk<Headers>()

            every { call.request } returns request
            every { request.headers } returns headers
            every { headers["X-Forwarded-For"] } returns "203.0.113.1"

            assertEquals("203.0.113.1", call.getClientIp())
        }

        @Test
        @DisplayName("getClientIp should extract X-Real-IP header when no X-Forwarded-For")
        fun testGetClientIpRealIp() {
            val call = mockk<ApplicationCall>()
            val request = mockk<ApplicationRequest>()
            val headers = mockk<Headers>()

            every { call.request } returns request
            every { request.headers } returns headers
            every { headers["X-Forwarded-For"] } returns null
            every { headers["X-Real-IP"] } returns "198.51.100.42"

            assertEquals("198.51.100.42", call.getClientIp())
        }

        @Test
        @DisplayName("getClientIp should fall back to remote host")
        fun testGetClientIpRemoteHost() {
            val call = mockk<ApplicationCall>()
            val request = mockk<ApplicationRequest>()
            val headers = mockk<Headers>()
            val local = mockk<RequestConnectionPoint>()

            every { call.request } returns request
            every { request.headers } returns headers
            every { request.local } returns local
            every { headers["X-Forwarded-For"] } returns null
            every { headers["X-Real-IP"] } returns null
            every { local.remoteHost } returns "192.168.1.100"

            assertEquals("192.168.1.100", call.getClientIp())
        }

        @Test
        @DisplayName("getClientIp should trim whitespace from forwarded IP")
        fun testGetClientIpTrimWhitespace() {
            val call = mockk<ApplicationCall>()
            val request = mockk<ApplicationRequest>()
            val headers = mockk<Headers>()

            every { call.request } returns request
            every { request.headers } returns headers
            every { headers["X-Forwarded-For"] } returns "  203.0.113.1  , 198.51.100.1"

            assertEquals("203.0.113.1", call.getClientIp())
        }

        @Test
        @DisplayName("getClientIp should handle blank X-Forwarded-For")
        fun testGetClientIpBlankForwardedFor() {
            val call = mockk<ApplicationCall>()
            val request = mockk<ApplicationRequest>()
            val headers = mockk<Headers>()
            val local = mockk<RequestConnectionPoint>()

            every { call.request } returns request
            every { request.headers } returns headers
            every { request.local } returns local
            every { headers["X-Forwarded-For"] } returns "   "
            every { headers["X-Real-IP"] } returns null
            every { local.remoteHost } returns "192.168.1.1"

            assertEquals("192.168.1.1", call.getClientIp())
        }

        @Test
        @DisplayName("getDialect should retrieve dialect from SessionConnectionManager")
        fun testGetDialect() {
            val call = mockk<ApplicationCall>()
            val principal = mockk<JWTPrincipal>()

            mockkObject(SessionConnectionManager)

            val mockDialect = mockk<MySQLDialect>()
            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("sessionId").asString() } returns "session123"
            every { SessionConnectionManager.getDialect("session123") } returns mockDialect

            val dialect = call.getDialect()
            assertEquals(mockDialect, dialect)

            unmockkObject(SessionConnectionManager)
        }

        @Test
        @DisplayName("getConnection should retrieve connection from SessionConnectionManager")
        fun testGetConnection() {
            val call = mockk<ApplicationCall>()
            val principal = mockk<JWTPrincipal>()
            val connection = mockk<Connection>()

            mockkObject(SessionConnectionManager)

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("sessionId").asString() } returns "session456"
            every { SessionConnectionManager.getConnection("session456") } returns connection

            val result = call.getConnection()
            assertSame(connection, result)

            unmockkObject(SessionConnectionManager)
        }

        @Test
        @DisplayName("useConnection should execute block with connection")
        fun testUseConnection() {
            val call = mockk<ApplicationCall>()
            val principal = mockk<JWTPrincipal>()
            val connection = mockk<Connection>(relaxed = true)

            mockkObject(SessionConnectionManager)

            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("sessionId").asString() } returns "session789"
            every { SessionConnectionManager.getConnection("session789") } returns connection

            var blockExecuted = false
            var receivedConnection: Connection? = null

            call.useConnection { conn ->
                blockExecuted = true
                receivedConnection = conn
                "result"
            }

            assertTrue(blockExecuted)
            assertSame(connection, receivedConnection)
            verify { connection.close() }

            unmockkObject(SessionConnectionManager)
        }

        @Test
        @DisplayName("useConnectionWithDialect should execute block with connection and dialect")
        fun testUseConnectionWithDialect() {
            val call = mockk<ApplicationCall>()
            val principal = mockk<JWTPrincipal>()
            val connection = mockk<Connection>(relaxed = true)

            mockkObject(SessionConnectionManager)

            val mockDialect = mockk<PostgreSQLDialect>()
            every { call.principal<JWTPrincipal>() } returns principal
            every { principal.payload.getClaim("sessionId").asString() } returns "session999"
            every { SessionConnectionManager.getConnection("session999") } returns connection
            every { SessionConnectionManager.getDialect("session999") } returns mockDialect

            var blockExecuted = false
            var receivedConnection: Connection? = null
            var receivedDialect: DatabaseDialect? = null

            call.useConnectionWithDialect { conn, dialect ->
                blockExecuted = true
                receivedConnection = conn
                receivedDialect = dialect
                "result"
            }

            assertTrue(blockExecuted)
            assertSame(connection, receivedConnection)
            assertEquals(mockDialect, receivedDialect)
            verify { connection.close() }

            unmockkObject(SessionConnectionManager)
        }
    }

    @Nested
    @DisplayName("ErrorResponse Tests")
    inner class ErrorResponseTests {

        @Test
        @DisplayName("ErrorResponse should have error field")
        fun testErrorResponse() {
            val errorResponse = ErrorResponse("Something went wrong")
            assertEquals("Something went wrong", errorResponse.error)
        }

        @Test
        @DisplayName("ErrorResponse should support empty error message")
        fun testErrorResponseEmpty() {
            val errorResponse = ErrorResponse("")
            assertEquals("", errorResponse.error)
        }
    }
}
