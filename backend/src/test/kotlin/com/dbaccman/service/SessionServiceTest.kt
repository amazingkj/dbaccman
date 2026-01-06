package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.dialect.PostgreSQLDialect
import com.dbaccman.model.SessionInfo
import com.dbaccman.model.SessionStats
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement

class SessionServiceTest {

    private lateinit var sessionService: SessionService

    @BeforeEach
    fun setUp() {
        sessionService = SessionService()
        mockkObject(SessionConnectionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("SessionService should be instantiable")
    fun testSessionServiceInstantiation() {
        assertNotNull(sessionService)
    }

    // ==================== SessionInfo Model Tests ====================

    @Nested
    @DisplayName("SessionInfo Model Tests")
    inner class SessionInfoModelTests {
        @Test
        @DisplayName("SessionInfo model should have correct properties")
        fun testSessionInfoModel() {
            val sessionInfo = SessionInfo(
                pid = 12345L,
                serialNum = 100L,
                user = "testuser",
                host = "localhost",
                database = "testdb",
                command = "Query",
                time = 120,
                state = "executing",
                query = "SELECT * FROM users"
            )

            assertEquals(12345L, sessionInfo.pid)
            assertEquals(100L, sessionInfo.serialNum)
            assertEquals("testuser", sessionInfo.user)
            assertEquals("localhost", sessionInfo.host)
            assertEquals("testdb", sessionInfo.database)
            assertEquals("Query", sessionInfo.command)
            assertEquals(120, sessionInfo.time)
            assertEquals("executing", sessionInfo.state)
            assertEquals("SELECT * FROM users", sessionInfo.query)
        }

        @Test
        @DisplayName("SessionInfo should allow null serialNum for non-Oracle databases")
        fun testSessionInfoWithoutSerialNum() {
            val sessionInfo = SessionInfo(
                pid = 99999L,
                serialNum = null,
                user = "mysqluser",
                host = "192.168.1.100",
                database = "mydb",
                command = "Sleep",
                time = 0,
                state = "idle",
                query = null
            )

            assertEquals(99999L, sessionInfo.pid)
            assertNull(sessionInfo.serialNum)
            assertNull(sessionInfo.query)
            assertEquals("idle", sessionInfo.state)
        }

        @Test
        @DisplayName("SessionInfo should truncate long queries")
        fun testSessionInfoWithLongQuery() {
            val longQuery = "SELECT ".repeat(100)
            val sessionInfo = SessionInfo(
                pid = 1L,
                user = "admin",
                host = "localhost",
                database = "testdb",
                command = "Query",
                time = 500,
                state = "running",
                query = longQuery.take(500)
            )

            assertTrue(sessionInfo.query!!.length <= 500)
        }

        @Test
        @DisplayName("SessionInfo equality should work correctly")
        fun testSessionInfoEquality() {
            val session1 = SessionInfo(
                pid = 123L,
                serialNum = 456L,
                user = "user1",
                host = "host1",
                database = "db1",
                command = "Query",
                time = 10,
                state = "active",
                query = "SELECT 1"
            )

            val session2 = SessionInfo(
                pid = 123L,
                serialNum = 456L,
                user = "user1",
                host = "host1",
                database = "db1",
                command = "Query",
                time = 10,
                state = "active",
                query = "SELECT 1"
            )

            assertEquals(session1, session2)
            assertEquals(session1.hashCode(), session2.hashCode())
        }

        @Test
        @DisplayName("SessionInfo should handle empty string fields")
        fun testSessionInfoEmptyStrings() {
            val sessionInfo = SessionInfo(
                pid = 1L,
                user = "",
                host = "",
                database = null,
                command = "",
                time = 0,
                state = null,
                query = null
            )

            assertEquals("", sessionInfo.user)
            assertEquals("", sessionInfo.host)
            assertNull(sessionInfo.database)
        }

        @Test
        @DisplayName("SessionInfo copy should work correctly")
        fun testSessionInfoCopy() {
            val original = SessionInfo(
                pid = 100L,
                serialNum = 200L,
                user = "original",
                host = "host1",
                database = "db1",
                command = "Query",
                time = 50,
                state = "running",
                query = "SELECT 1"
            )

            val copied = original.copy(user = "modified", time = 100)

            assertEquals(100L, copied.pid)
            assertEquals(200L, copied.serialNum)
            assertEquals("modified", copied.user)
            assertEquals(100, copied.time)
            assertEquals("SELECT 1", copied.query)
        }
    }

    // ==================== SessionStats Model Tests ====================

    @Nested
    @DisplayName("SessionStats Model Tests")
    inner class SessionStatsModelTests {
        @Test
        @DisplayName("SessionStats model should calculate session counts")
        fun testSessionStatsModel() {
            val stats = SessionStats(
                totalSessions = 100,
                activeSessions = 25,
                sleepingSessions = 70,
                longRunningSessions = 5
            )

            assertEquals(100, stats.totalSessions)
            assertEquals(25, stats.activeSessions)
            assertEquals(70, stats.sleepingSessions)
            assertEquals(5, stats.longRunningSessions)
        }

        @Test
        @DisplayName("SessionStats should handle zero values")
        fun testSessionStatsWithZeroValues() {
            val stats = SessionStats(
                totalSessions = 0,
                activeSessions = 0,
                sleepingSessions = 0,
                longRunningSessions = 0
            )

            assertEquals(0, stats.totalSessions)
            assertEquals(0, stats.activeSessions)
            assertEquals(0, stats.sleepingSessions)
            assertEquals(0, stats.longRunningSessions)
        }

        @Test
        @DisplayName("SessionStats equality should work correctly")
        fun testSessionStatsEquality() {
            val stats1 = SessionStats(10, 5, 4, 1)
            val stats2 = SessionStats(10, 5, 4, 1)

            assertEquals(stats1, stats2)
            assertEquals(stats1.hashCode(), stats2.hashCode())
        }

        @Test
        @DisplayName("SessionStats should handle high numbers")
        fun testSessionStatsHighNumbers() {
            val stats = SessionStats(
                totalSessions = 10000,
                activeSessions = 5000,
                sleepingSessions = 4500,
                longRunningSessions = 500
            )

            assertEquals(10000, stats.totalSessions)
            assertEquals(5000, stats.activeSessions)
        }

        @Test
        @DisplayName("SessionStats all active sessions")
        fun testSessionStatsAllActive() {
            val stats = SessionStats(
                totalSessions = 50,
                activeSessions = 50,
                sleepingSessions = 0,
                longRunningSessions = 10
            )

            assertEquals(50, stats.totalSessions)
            assertEquals(50, stats.activeSessions)
            assertEquals(0, stats.sleepingSessions)
        }
    }

    // ==================== SessionTarget Tests ====================

    @Nested
    @DisplayName("SessionTarget Tests")
    inner class SessionTargetTests {
        @Test
        @DisplayName("SessionTarget should hold pid and serialNum")
        fun testSessionTarget() {
            val target = SessionService.SessionTarget(pid = 12345L, serialNum = 67890L)

            assertEquals(12345L, target.pid)
            assertEquals(67890L, target.serialNum)
        }

        @Test
        @DisplayName("SessionTarget should allow null serialNum")
        fun testSessionTargetNullSerialNum() {
            val target = SessionService.SessionTarget(pid = 99999L, serialNum = null)

            assertEquals(99999L, target.pid)
            assertNull(target.serialNum)
        }

        @Test
        @DisplayName("SessionTarget equality should work correctly")
        fun testSessionTargetEquality() {
            val target1 = SessionService.SessionTarget(100L, 200L)
            val target2 = SessionService.SessionTarget(100L, 200L)

            assertEquals(target1, target2)
            assertEquals(target1.hashCode(), target2.hashCode())
        }

        @Test
        @DisplayName("SessionTarget with different serialNum should not be equal")
        fun testSessionTargetInequality() {
            val target1 = SessionService.SessionTarget(100L, 200L)
            val target2 = SessionService.SessionTarget(100L, 300L)

            assertNotEquals(target1, target2)
        }
    }

    // ==================== BulkKillResult Tests ====================

    @Nested
    @DisplayName("BulkKillResult Tests")
    inner class BulkKillResultTests {
        @Test
        @DisplayName("BulkKillResult should track success and failed counts")
        fun testBulkKillResult() {
            val result = SessionService.BulkKillResult(
                success = 5,
                failed = 2,
                errors = listOf("PID 123: Session not found", "PID 456: Permission denied")
            )

            assertEquals(5, result.success)
            assertEquals(2, result.failed)
            assertEquals(2, result.errors.size)
        }

        @Test
        @DisplayName("BulkKillResult with all success")
        fun testBulkKillResultAllSuccess() {
            val result = SessionService.BulkKillResult(
                success = 10,
                failed = 0,
                errors = emptyList()
            )

            assertEquals(10, result.success)
            assertEquals(0, result.failed)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        @DisplayName("BulkKillResult with all failures")
        fun testBulkKillResultAllFailed() {
            val result = SessionService.BulkKillResult(
                success = 0,
                failed = 3,
                errors = listOf("Error 1", "Error 2", "Error 3")
            )

            assertEquals(0, result.success)
            assertEquals(3, result.failed)
            assertEquals(3, result.errors.size)
        }

        @Test
        @DisplayName("BulkKillResult equality")
        fun testBulkKillResultEquality() {
            val result1 = SessionService.BulkKillResult(5, 2, listOf("err1"))
            val result2 = SessionService.BulkKillResult(5, 2, listOf("err1"))

            assertEquals(result1, result2)
        }
    }

    // ==================== Service Method Tests with Mocking ====================

    @Nested
    @DisplayName("Service Method Tests with Mocking")
    inner class ServiceMethodTests {

        @Test
        @DisplayName("getSessionStats should return stats from database")
        fun testGetSessionStats() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getSessionStatsQuery() } returns "SELECT COUNT(*) as total, ..."
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.next() } returns true
            every { mockResultSet.getInt("total") } returns 100
            every { mockResultSet.getInt("active") } returns 25
            every { mockResultSet.getInt("sleeping") } returns 70
            every { mockResultSet.getInt("long_running") } returns 5
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val stats = sessionService.getSessionStats("test-session")

            assertEquals(100, stats.totalSessions)
            assertEquals(25, stats.activeSessions)
            assertEquals(70, stats.sleepingSessions)
            assertEquals(5, stats.longRunningSessions)
        }

        @Test
        @DisplayName("getSessionStats should return zeros when no results")
        fun testGetSessionStatsEmpty() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getSessionStatsQuery() } returns "SELECT COUNT(*) as total, ..."
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.next() } returns false
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val stats = sessionService.getSessionStats("test-session")

            assertEquals(0, stats.totalSessions)
            assertEquals(0, stats.activeSessions)
            assertEquals(0, stats.sleepingSessions)
            assertEquals(0, stats.longRunningSessions)
        }

        @Test
        @DisplayName("getActiveSessions should return list of sessions")
        fun testGetActiveSessions() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockMetaData = mockk<ResultSetMetaData>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getActiveSessionsQuery() } returns "SELECT * FROM sessions"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.metaData } returns mockMetaData
            every { mockMetaData.columnCount } returns 8
            every { mockMetaData.getColumnLabel(any()) } returns "pid"
            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getLong("pid") } returnsMany listOf(1L, 2L)
            every { mockResultSet.getLong("serial_num") } returns 0L
            every { mockResultSet.wasNull() } returns true
            every { mockResultSet.getString("sess_user") } returnsMany listOf("user1", "user2")
            every { mockResultSet.getString("host") } returns "localhost"
            every { mockResultSet.getString("database_name") } returns "testdb"
            every { mockResultSet.getString("command") } returns "Query"
            every { mockResultSet.getInt("time") } returns 10
            every { mockResultSet.getString("state") } returns "active"
            every { mockResultSet.getString("query") } returns "SELECT 1"
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val sessions = sessionService.getActiveSessions("test-session")

            assertEquals(2, sessions.size)
            assertEquals(1L, sessions[0].pid)
            assertEquals(2L, sessions[1].pid)
            assertEquals("user1", sessions[0].user)
            assertEquals("user2", sessions[1].user)
        }

        @Test
        @DisplayName("killSession should execute kill command")
        fun testKillSession() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getKillSessionSql(12345L, null) } returns "KILL 12345"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute("KILL 12345") } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            sessionService.killSession("test-session", 12345L)

            verify { mockStatement.execute("KILL 12345") }
        }

        @Test
        @DisplayName("killSession should include serialNum for Oracle")
        fun testKillSessionOracle() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getKillSessionSql(100L, 200L) } returns "ALTER SYSTEM KILL SESSION '100,200' IMMEDIATE"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            sessionService.killSession("test-session", 100L, 200L)

            verify { mockDialect.getKillSessionSql(100L, 200L) }
        }

        @Test
        @DisplayName("killQuery should execute kill query command")
        fun testKillQuery() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getKillQuerySql(12345L, null) } returns "KILL QUERY 12345"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute("KILL QUERY 12345") } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            sessionService.killQuery("test-session", 12345L)

            verify { mockStatement.execute("KILL QUERY 12345") }
        }

        @Test
        @DisplayName("killSessions should track success and failures")
        fun testKillSessions() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getKillSessionSql(1L, null) } returns "KILL 1"
            every { mockDialect.getKillSessionSql(2L, null) } returns "KILL 2"
            every { mockDialect.getKillSessionSql(3L, null) } returns "KILL 3"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute("KILL 1") } returns true
            every { mockStatement.execute("KILL 2") } throws RuntimeException("Session not found")
            every { mockStatement.execute("KILL 3") } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val targets = listOf(
                SessionService.SessionTarget(1L, null),
                SessionService.SessionTarget(2L, null),
                SessionService.SessionTarget(3L, null)
            )

            val result = sessionService.killSessions("test-session", targets)

            assertEquals(2, result.success)
            assertEquals(1, result.failed)
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].contains("PID 2"))
        }

        @Test
        @DisplayName("getLongRunningQueries should filter by threshold")
        fun testGetLongRunningQueries() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockMetaData = mockk<ResultSetMetaData>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getLongRunningQueriesQuery() } returns "SELECT * FROM sessions WHERE time > ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setInt(1, 120) } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet
            every { mockResultSet.metaData } returns mockMetaData
            every { mockMetaData.columnCount } returns 8
            every { mockMetaData.getColumnLabel(any()) } returns "pid"
            every { mockResultSet.next() } returnsMany listOf(true, false)
            every { mockResultSet.getLong("pid") } returns 999L
            every { mockResultSet.getLong("serial_num") } returns 0L
            every { mockResultSet.wasNull() } returns true
            every { mockResultSet.getString("sess_user") } returns "slowuser"
            every { mockResultSet.getString("host") } returns "localhost"
            every { mockResultSet.getString("database_name") } returns "testdb"
            every { mockResultSet.getString("command") } returns "Query"
            every { mockResultSet.getInt("time") } returns 300
            every { mockResultSet.getString("state") } returns "executing"
            every { mockResultSet.getString("query") } returns "SELECT SLEEP(1000)"
            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val sessions = sessionService.getLongRunningQueries("test-session", 120)

            assertEquals(1, sessions.size)
            assertEquals(999L, sessions[0].pid)
            assertEquals(300, sessions[0].time)
            assertEquals("slowuser", sessions[0].user)
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Case Tests")
    inner class EdgeCaseTests {
        @Test
        @DisplayName("SessionInfo with max time value")
        fun testSessionInfoMaxTime() {
            val sessionInfo = SessionInfo(
                pid = 1L,
                user = "user",
                host = "host",
                database = "db",
                command = "Query",
                time = Int.MAX_VALUE,
                state = "running",
                query = null
            )

            assertEquals(Int.MAX_VALUE, sessionInfo.time)
        }

        @Test
        @DisplayName("SessionInfo with negative time (edge case)")
        fun testSessionInfoNegativeTime() {
            val sessionInfo = SessionInfo(
                pid = 1L,
                user = "user",
                host = "host",
                database = "db",
                command = "Query",
                time = -1,
                state = "running",
                query = null
            )

            assertEquals(-1, sessionInfo.time)
        }

        @Test
        @DisplayName("Multiple targets with same PID but different serialNum")
        fun testMultipleTargetsSamePid() {
            val target1 = SessionService.SessionTarget(100L, 1L)
            val target2 = SessionService.SessionTarget(100L, 2L)
            val target3 = SessionService.SessionTarget(100L, 3L)

            val targets = listOf(target1, target2, target3)

            assertEquals(3, targets.size)
            assertNotEquals(target1, target2)
            assertNotEquals(target2, target3)
        }
    }
}
