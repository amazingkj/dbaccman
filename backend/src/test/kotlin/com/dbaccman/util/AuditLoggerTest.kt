package com.dbaccman.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class AuditLoggerTest {

    @BeforeEach
    fun setUp() {
        // Clear logs before each test
        AuditLogger.clearLogs()
    }

    @AfterEach
    fun tearDown() {
        // Clear logs after each test
        AuditLogger.clearLogs()
    }

    @Test
    @DisplayName("AuditLogger should be an object singleton")
    fun testAuditLoggerIsSingleton() {
        assertNotNull(AuditLogger)
    }

    // ==================== AuditLogEntry Model Tests ====================

    @Test
    @DisplayName("AuditLogEntry model should have correct properties")
    fun testAuditLogEntryModel() {
        val entry = AuditLogEntry(
            timestamp = "2024-01-15 10:30:00",
            action = "LOGIN",
            user = "testuser",
            ipAddress = "192.168.1.100",
            message = "User logged in successfully",
            target = "MySQL",
            details = "localhost:3306"
        )

        assertEquals("2024-01-15 10:30:00", entry.timestamp)
        assertEquals("LOGIN", entry.action)
        assertEquals("testuser", entry.user)
        assertEquals("192.168.1.100", entry.ipAddress)
        assertEquals("User logged in successfully", entry.message)
        assertEquals("MySQL", entry.target)
        assertEquals("localhost:3306", entry.details)
    }

    @Test
    @DisplayName("AuditLogEntry should allow null optional fields")
    fun testAuditLogEntryNullFields() {
        val entry = AuditLogEntry(
            timestamp = "2024-01-15 10:30:00",
            action = "SYSTEM",
            user = null,
            ipAddress = null,
            message = "System startup"
        )

        assertNull(entry.user)
        assertNull(entry.ipAddress)
        assertNull(entry.target)
        assertNull(entry.details)
    }

    @Test
    @DisplayName("AuditLogEntry equality should work correctly")
    fun testAuditLogEntryEquality() {
        val entry1 = AuditLogEntry("ts", "ACTION", "user", "ip", "msg", "target", "details")
        val entry2 = AuditLogEntry("ts", "ACTION", "user", "ip", "msg", "target", "details")

        assertEquals(entry1, entry2)
        assertEquals(entry1.hashCode(), entry2.hashCode())
    }

    // ==================== log() Method Tests ====================

    @Test
    @DisplayName("log should add entry to log storage")
    fun testLogAddsEntry() {
        AuditLogger.log("TEST_ACTION", "Test message")

        val logs = AuditLogger.getRecentLogs()
        assertEquals(1, logs.size)
        assertEquals("TEST_ACTION", logs.first().action)
    }

    @Test
    @DisplayName("log should include user when provided")
    fun testLogWithUser() {
        AuditLogger.log("LOGIN", "User logged in", user = "testuser")

        val logs = AuditLogger.getRecentLogs()
        assertEquals("testuser", logs.first().user)
    }

    @Test
    @DisplayName("log should include IP address when provided")
    fun testLogWithIpAddress() {
        AuditLogger.log("LOGIN", "User logged in", ipAddress = "10.0.0.1")

        val logs = AuditLogger.getRecentLogs()
        assertEquals("10.0.0.1", logs.first().ipAddress)
    }

    @Test
    @DisplayName("log should include all parameters")
    fun testLogWithAllParameters() {
        AuditLogger.log("CREATE_USER", "Created new user", user = "admin", ipAddress = "192.168.1.1")

        val logs = AuditLogger.getRecentLogs()
        val entry = logs.first()
        assertEquals("CREATE_USER", entry.action)
        assertEquals("Created new user", entry.message)
        assertEquals("admin", entry.user)
        assertEquals("192.168.1.1", entry.ipAddress)
    }

    // ==================== logWithTarget() Method Tests ====================

    @Test
    @DisplayName("logWithTarget should include target")
    fun testLogWithTarget() {
        AuditLogger.logWithTarget("GRANT_PERMISSION", "admin", "testuser@localhost")

        val logs = AuditLogger.getRecentLogs()
        val entry = logs.first()
        assertEquals("GRANT_PERMISSION", entry.action)
        assertEquals("admin", entry.user)
        assertEquals("testuser@localhost", entry.target)
    }

    @Test
    @DisplayName("logWithTarget should include details when provided")
    fun testLogWithTargetAndDetails() {
        AuditLogger.logWithTarget(
            action = "ALTER_USER",
            user = "admin",
            target = "testuser",
            details = "Password changed"
        )

        val logs = AuditLogger.getRecentLogs()
        assertEquals("Password changed", logs.first().details)
    }

    @Test
    @DisplayName("logWithTarget should include IP address when provided")
    fun testLogWithTargetAndIp() {
        AuditLogger.logWithTarget(
            action = "DELETE_USER",
            user = "admin",
            target = "olduser",
            ipAddress = "172.16.0.1"
        )

        val logs = AuditLogger.getRecentLogs()
        assertEquals("172.16.0.1", logs.first().ipAddress)
    }

    // ==================== logQuery() Method Tests ====================

    @Test
    @DisplayName("logQuery should log successful query")
    fun testLogQuerySuccess() {
        AuditLogger.logQuery(
            user = "analyst",
            query = "SELECT * FROM users",
            database = "testdb",
            ipAddress = "192.168.1.50",
            success = true
        )

        val logs = AuditLogger.getRecentLogs()
        val entry = logs.first()
        assertEquals("QUERY_EXECUTE", entry.action)
        assertEquals("analyst", entry.user)
        assertEquals("testdb", entry.target)
        assertTrue(entry.details!!.contains("SUCCESS"))
    }

    @Test
    @DisplayName("logQuery should log failed query with error")
    fun testLogQueryFailure() {
        AuditLogger.logQuery(
            user = "developer",
            query = "SELECT * FROM nonexistent",
            database = "testdb",
            ipAddress = "192.168.1.60",
            success = false,
            error = "Table does not exist"
        )

        val logs = AuditLogger.getRecentLogs()
        val entry = logs.first()
        assertTrue(entry.details!!.contains("FAILED"))
        assertTrue(entry.details!!.contains("Table does not exist"))
    }

    @Test
    @DisplayName("logQuery should truncate long queries")
    fun testLogQueryTruncation() {
        val longQuery = "SELECT ".repeat(100)  // Create a very long query
        AuditLogger.logQuery(
            user = "user",
            query = longQuery,
            database = "db",
            ipAddress = null,
            success = true
        )

        val logs = AuditLogger.getRecentLogs()
        assertTrue(logs.first().message.length <= 200)
    }

    @Test
    @DisplayName("logQuery should handle null database")
    fun testLogQueryNullDatabase() {
        AuditLogger.logQuery(
            user = "user",
            query = "SELECT 1",
            database = null,
            ipAddress = null,
            success = true
        )

        val logs = AuditLogger.getRecentLogs()
        assertNull(logs.first().target)
    }

    // ==================== getRecentLogs() Method Tests ====================

    @Test
    @DisplayName("getRecentLogs should return empty list initially")
    fun testGetRecentLogsEmpty() {
        val logs = AuditLogger.getRecentLogs()
        assertTrue(logs.isEmpty())
    }

    @Test
    @DisplayName("getRecentLogs should respect limit parameter")
    fun testGetRecentLogsLimit() {
        repeat(10) { i ->
            AuditLogger.log("ACTION_$i", "Message $i")
        }

        val logs = AuditLogger.getRecentLogs(limit = 5)
        assertEquals(5, logs.size)
    }

    @Test
    @DisplayName("getRecentLogs should filter by action")
    fun testGetRecentLogsFilterByAction() {
        AuditLogger.log("LOGIN", "User logged in")
        AuditLogger.log("LOGOUT", "User logged out")
        AuditLogger.log("LOGIN", "Another login")

        val logs = AuditLogger.getRecentLogs(action = "LOGIN")
        assertEquals(2, logs.size)
        assertTrue(logs.all { it.action == "LOGIN" })
    }

    @Test
    @DisplayName("getRecentLogs should filter by user")
    fun testGetRecentLogsFilterByUser() {
        AuditLogger.log("ACTION1", "Message", user = "admin")
        AuditLogger.log("ACTION2", "Message", user = "user1")
        AuditLogger.log("ACTION3", "Message", user = "admin")

        val logs = AuditLogger.getRecentLogs(user = "admin")
        assertEquals(2, logs.size)
        assertTrue(logs.all { it.user?.contains("admin") == true })
    }

    @Test
    @DisplayName("getRecentLogs should filter by both action and user")
    fun testGetRecentLogsFilterByBoth() {
        AuditLogger.log("LOGIN", "Login", user = "admin")
        AuditLogger.log("LOGIN", "Login", user = "user1")
        AuditLogger.log("LOGOUT", "Logout", user = "admin")

        val logs = AuditLogger.getRecentLogs(action = "LOGIN", user = "admin")
        assertEquals(1, logs.size)
        assertEquals("LOGIN", logs.first().action)
        assertEquals("admin", logs.first().user)
    }

    @Test
    @DisplayName("getRecentLogs should be case insensitive for user filter")
    fun testGetRecentLogsUserCaseInsensitive() {
        AuditLogger.log("ACTION", "Message", user = "Admin")

        val logs = AuditLogger.getRecentLogs(user = "admin")
        assertEquals(1, logs.size)
    }

    // ==================== clearLogs() Method Tests ====================

    @Test
    @DisplayName("clearLogs should remove all entries")
    fun testClearLogs() {
        AuditLogger.log("ACTION1", "Message1")
        AuditLogger.log("ACTION2", "Message2")

        AuditLogger.clearLogs()

        val logs = AuditLogger.getRecentLogs()
        assertTrue(logs.isEmpty())
    }

    // ==================== Log Order Tests ====================

    @Test
    @DisplayName("Logs should be returned in reverse chronological order")
    fun testLogOrder() {
        AuditLogger.log("FIRST", "First message")
        Thread.sleep(10)  // Small delay to ensure different timestamps
        AuditLogger.log("SECOND", "Second message")

        val logs = AuditLogger.getRecentLogs()
        assertEquals("SECOND", logs[0].action)
        assertEquals("FIRST", logs[1].action)
    }

    // ==================== Timestamp Format Tests ====================

    @Test
    @DisplayName("Log timestamp should be in expected format")
    fun testTimestampFormat() {
        AuditLogger.log("TEST", "Test message")

        val logs = AuditLogger.getRecentLogs()
        val timestamp = logs.first().timestamp

        // Format should be: yyyy-MM-dd HH:mm:ss
        val regex = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")
        assertTrue(regex.matches(timestamp))
    }
}
