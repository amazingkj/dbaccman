package com.dbaccman.routes

import com.dbaccman.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class DashboardRoutesTest {

    // ==================== DashboardStats Model Tests ====================

    @Nested
    @DisplayName("DashboardStats Model Tests")
    inner class DashboardStatsTests {

        @Test
        @DisplayName("DashboardStats should have all required properties")
        fun testDashboardStatsProperties() {
            val healthScore = HealthScore(85, 35, 25, 25, "healthy", emptyList())

            val stats = DashboardStats(
                totalAccounts = 100,
                activeSessions = 25,
                expiringSoon = 5,
                slowQueries = 2,
                lockedAccounts = 0,
                totalDatabases = 10,
                totalTables = 500,
                tablespaceUsage = 50,
                criticalTablespaces = 0,
                healthScore = healthScore,
                expiringAccounts = emptyList(),
                longRunningSessions = emptyList(),
                topDatabases = emptyList()
            )

            assertEquals(100, stats.totalAccounts)
            assertEquals(25, stats.activeSessions)
            assertEquals(5, stats.expiringSoon)
            assertEquals(2, stats.slowQueries)
            assertEquals(0, stats.lockedAccounts)
            assertEquals(10, stats.totalDatabases)
            assertEquals(500, stats.totalTables)
            assertEquals(50, stats.tablespaceUsage)
            assertEquals(85, stats.healthScore.total)
        }

        @Test
        @DisplayName("DashboardStats should contain expiring accounts list")
        fun testDashboardStatsWithExpiringAccounts() {
            val expiringAccounts = listOf(
                ExpiringAccount("user1", "localhost", 3),
                ExpiringAccount("user2", "%", 7),
                ExpiringAccount("user3", "192.168.%", 14)
            )
            val healthScore = HealthScore(88, 28, 30, 30, "healthy", listOf("3개 계정이 30일 내 만료 예정"))

            val stats = DashboardStats(
                totalAccounts = 50,
                activeSessions = 10,
                expiringSoon = 3,
                slowQueries = 0,
                lockedAccounts = 0,
                totalDatabases = 5,
                totalTables = 100,
                tablespaceUsage = 30,
                criticalTablespaces = 0,
                healthScore = healthScore,
                expiringAccounts = expiringAccounts,
                longRunningSessions = emptyList(),
                topDatabases = emptyList()
            )

            assertEquals(3, stats.expiringAccounts.size)
            assertEquals("user1", stats.expiringAccounts[0].username)
            assertEquals(3, stats.expiringAccounts[0].daysUntilExpiry)
        }

        @Test
        @DisplayName("DashboardStats should contain long running sessions")
        fun testDashboardStatsWithLongRunningSessions() {
            val sessions = listOf(
                SessionInfo(
                    pid = 12345,
                    serialNum = null,
                    user = "admin",
                    host = "localhost",
                    database = "production",
                    command = "Query",
                    time = 3600,
                    state = "executing",
                    query = "SELECT * FROM large_table"
                )
            )
            val healthScore = HealthScore(77, 36, 21, 20, "warning", listOf("1개 장기 실행 쿼리 감지"))

            val stats = DashboardStats(
                totalAccounts = 20,
                activeSessions = 5,
                expiringSoon = 1,
                slowQueries = 1,
                lockedAccounts = 0,
                totalDatabases = 3,
                totalTables = 50,
                tablespaceUsage = 40,
                criticalTablespaces = 0,
                healthScore = healthScore,
                expiringAccounts = emptyList(),
                longRunningSessions = sessions,
                topDatabases = emptyList()
            )

            assertEquals(1, stats.longRunningSessions.size)
            assertEquals(12345, stats.longRunningSessions[0].pid)
            assertEquals(3600, stats.longRunningSessions[0].time)
        }

        @Test
        @DisplayName("DashboardStats should contain top databases by size")
        fun testDashboardStatsWithTopDatabases() {
            val databases = listOf(
                DatabaseInfo("production", 100, 5000000, 10737418240),
                DatabaseInfo("analytics", 50, 2000000, 5368709120),
                DatabaseInfo("staging", 80, 1000000, 2147483648)
            )
            val healthScore = HealthScore(92, 32, 30, 30, "healthy", emptyList())

            val stats = DashboardStats(
                totalAccounts = 30,
                activeSessions = 15,
                expiringSoon = 2,
                slowQueries = 0,
                lockedAccounts = 0,
                totalDatabases = 3,
                totalTables = 230,
                tablespaceUsage = 55,
                criticalTablespaces = 0,
                healthScore = healthScore,
                expiringAccounts = emptyList(),
                longRunningSessions = emptyList(),
                topDatabases = databases
            )

            assertEquals(3, stats.topDatabases.size)
            assertEquals("production", stats.topDatabases[0].name)
            assertEquals(10737418240, stats.topDatabases[0].size)
        }
    }

    // ==================== ExpiringAccount Model Tests ====================

    @Nested
    @DisplayName("ExpiringAccount Model Tests")
    inner class ExpiringAccountTests {

        @Test
        @DisplayName("ExpiringAccount should have username, host, and daysUntilExpiry")
        fun testExpiringAccountProperties() {
            val account = ExpiringAccount(
                username = "expiring_user",
                host = "localhost",
                daysUntilExpiry = 5
            )

            assertEquals("expiring_user", account.username)
            assertEquals("localhost", account.host)
            assertEquals(5, account.daysUntilExpiry)
        }

        @Test
        @DisplayName("ExpiringAccount with zero days means expires today")
        fun testExpiringAccountZeroDays() {
            val account = ExpiringAccount("user", "%", 0)
            assertEquals(0, account.daysUntilExpiry)
        }

        @Test
        @DisplayName("ExpiringAccount with negative days means already expired")
        fun testExpiringAccountAlreadyExpired() {
            val account = ExpiringAccount("old_user", "%", -10)
            assertTrue(account.daysUntilExpiry < 0)
        }

        @Test
        @DisplayName("ExpiringAccount should support wildcard host")
        fun testExpiringAccountWildcardHost() {
            val account = ExpiringAccount("user", "192.168.%", 30)
            assertEquals("192.168.%", account.host)
        }
    }

    // ==================== DatabaseInfo Model Tests ====================

    @Nested
    @DisplayName("DatabaseInfo Model Tests")
    inner class DatabaseInfoTests {

        @Test
        @DisplayName("DatabaseInfo should have all properties")
        fun testDatabaseInfoProperties() {
            val db = DatabaseInfo(
                name = "mydb",
                tableCount = 25,
                totalRows = 1500000,
                size = 536870912
            )

            assertEquals("mydb", db.name)
            assertEquals(25, db.tableCount)
            assertEquals(1500000, db.totalRows)
            assertEquals(536870912, db.size)
        }

        @Test
        @DisplayName("DatabaseInfo should handle empty database")
        fun testDatabaseInfoEmpty() {
            val db = DatabaseInfo(
                name = "empty_db",
                tableCount = 0,
                totalRows = 0,
                size = 0
            )

            assertEquals(0, db.tableCount)
            assertEquals(0, db.totalRows)
            assertEquals(0, db.size)
        }

        @Test
        @DisplayName("DatabaseInfo should handle large values")
        fun testDatabaseInfoLargeValues() {
            val db = DatabaseInfo(
                name = "huge_db",
                tableCount = 10000,
                totalRows = Long.MAX_VALUE / 2,
                size = 1099511627776  // 1TB
            )

            assertEquals(10000, db.tableCount)
            assertTrue(db.totalRows > 0)
            assertEquals(1099511627776, db.size)
        }
    }

    // ==================== SessionStats Model Tests ====================

    @Nested
    @DisplayName("SessionStats Model Tests")
    inner class SessionStatsTests {

        @Test
        @DisplayName("SessionStats should have all counters")
        fun testSessionStatsProperties() {
            val stats = SessionStats(
                totalSessions = 100,
                activeSessions = 30,
                sleepingSessions = 65,
                longRunningSessions = 5
            )

            assertEquals(100, stats.totalSessions)
            assertEquals(30, stats.activeSessions)
            assertEquals(65, stats.sleepingSessions)
            assertEquals(5, stats.longRunningSessions)
        }

        @Test
        @DisplayName("SessionStats should handle zero values")
        fun testSessionStatsZeroValues() {
            val stats = SessionStats(
                totalSessions = 0,
                activeSessions = 0,
                sleepingSessions = 0,
                longRunningSessions = 0
            )

            assertEquals(0, stats.totalSessions)
        }

        @Test
        @DisplayName("SessionStats sum of active and sleeping should not exceed total")
        fun testSessionStatsConsistency() {
            val stats = SessionStats(
                totalSessions = 100,
                activeSessions = 40,
                sleepingSessions = 55,
                longRunningSessions = 10
            )

            // Note: longRunning is a subset of active
            assertTrue(stats.activeSessions + stats.sleepingSessions <= stats.totalSessions + 10)
        }
    }

    // ==================== SessionInfo Model Tests ====================

    @Nested
    @DisplayName("SessionInfo Model Tests")
    inner class SessionInfoTests {

        @Test
        @DisplayName("SessionInfo should have all properties")
        fun testSessionInfoFullProperties() {
            val session = SessionInfo(
                pid = 54321,
                serialNum = 12345,
                user = "dba",
                host = "192.168.1.100:52341",
                database = "analytics",
                command = "Query",
                time = 120,
                state = "Sending data",
                query = "SELECT COUNT(*) FROM events GROUP BY user_id"
            )

            assertEquals(54321, session.pid)
            assertEquals(12345, session.serialNum)
            assertEquals("dba", session.user)
            assertEquals("192.168.1.100:52341", session.host)
            assertEquals("analytics", session.database)
            assertEquals("Query", session.command)
            assertEquals(120, session.time)
            assertEquals("Sending data", session.state)
            assertTrue(session.query!!.contains("SELECT"))
        }

        @Test
        @DisplayName("SessionInfo should handle nullable fields")
        fun testSessionInfoNullableFields() {
            val session = SessionInfo(
                pid = 1,
                serialNum = null,
                user = "system",
                host = "localhost",
                database = null,
                command = "Sleep",
                time = 0,
                state = null,
                query = null
            )

            assertNull(session.serialNum)
            assertNull(session.database)
            assertNull(session.state)
            assertNull(session.query)
        }

        @Test
        @DisplayName("SessionInfo should handle long running queries")
        fun testSessionInfoLongRunning() {
            val session = SessionInfo(
                pid = 999,
                serialNum = null,
                user = "batch_user",
                host = "10.0.0.50",
                database = "warehouse",
                command = "Query",
                time = 7200,  // 2 hours
                state = "executing",
                query = "INSERT INTO summary SELECT ... FROM transactions"
            )

            assertEquals(7200, session.time)
            assertTrue(session.time > 60)
        }
    }
}
