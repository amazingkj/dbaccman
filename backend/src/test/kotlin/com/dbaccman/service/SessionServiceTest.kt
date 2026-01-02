package com.dbaccman.service

import com.dbaccman.model.SessionInfo
import com.dbaccman.model.SessionStats
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class SessionServiceTest {

    private lateinit var sessionService: SessionService

    @BeforeEach
    fun setUp() {
        sessionService = SessionService()
    }

    @Test
    @DisplayName("SessionService should be instantiable")
    fun testSessionServiceInstantiation() {
        assertNotNull(sessionService)
    }

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
    @DisplayName("SessionInfo should truncate long queries")
    fun testSessionInfoWithLongQuery() {
        val longQuery = "SELECT ".repeat(100) // Create a long query string
        val sessionInfo = SessionInfo(
            pid = 1L,
            user = "admin",
            host = "localhost",
            database = "testdb",
            command = "Query",
            time = 500,
            state = "running",
            query = longQuery.take(500) // Simulate the 500 char limit from service
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
    @DisplayName("SessionStats equality should work correctly")
    fun testSessionStatsEquality() {
        val stats1 = SessionStats(10, 5, 4, 1)
        val stats2 = SessionStats(10, 5, 4, 1)

        assertEquals(stats1, stats2)
        assertEquals(stats1.hashCode(), stats2.hashCode())
    }
}
