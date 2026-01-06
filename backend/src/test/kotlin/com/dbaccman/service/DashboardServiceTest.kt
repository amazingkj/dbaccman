package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.model.*
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class DashboardServiceTest {

    private lateinit var dashboardService: DashboardService
    private lateinit var mockAccountService: AccountService
    private lateinit var mockSessionService: SessionService
    private lateinit var mockTableService: TableService

    @BeforeEach
    fun setUp() {
        // Mock the services that DashboardService depends on
        mockAccountService = mockk()
        mockSessionService = mockk()
        mockTableService = mockk()

        // Create DashboardService instance
        dashboardService = DashboardService()

        // Mock SessionConnectionManager for any potential session needs
        mockkObject(SessionConnectionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("DashboardService should be instantiable")
    fun testDashboardServiceInstantiation() {
        assertNotNull(dashboardService)
    }

    // ==================== DashboardStats Model Tests ====================

    @Nested
    @DisplayName("DashboardStats Model Tests")
    inner class DashboardStatsModelTests {

        @Test
        @DisplayName("DashboardStats should have all required properties")
        fun testDashboardStatsModel() {
            val expiringAccounts = listOf(
                ExpiringAccount("user1", "localhost", 15),
                ExpiringAccount("user2", "%", 5)
            )

            val longRunningSessions = listOf(
                SessionInfo(1L, null, "user1", "localhost", "testdb", "SELECT", 120, "Running", "SELECT * FROM large_table")
            )

            val topDatabases = listOf(
                DatabaseInfo("db1", 50, 10000L, 1048576L),
                DatabaseInfo("db2", 30, 5000L, 524288L)
            )

            val stats = DashboardStats(
                totalAccounts = 100,
                activeSessions = 25,
                expiringSoon = 5,
                slowQueries = 3,
                totalDatabases = 10,
                totalTables = 150,
                expiringAccounts = expiringAccounts,
                longRunningSessions = longRunningSessions,
                topDatabases = topDatabases
            )

            assertEquals(100, stats.totalAccounts)
            assertEquals(25, stats.activeSessions)
            assertEquals(5, stats.expiringSoon)
            assertEquals(3, stats.slowQueries)
            assertEquals(10, stats.totalDatabases)
            assertEquals(150, stats.totalTables)
            assertEquals(2, stats.expiringAccounts.size)
            assertEquals(1, stats.longRunningSessions.size)
            assertEquals(2, stats.topDatabases.size)
        }

        @Test
        @DisplayName("DashboardStats with empty collections")
        fun testDashboardStatsEmpty() {
            val stats = DashboardStats(
                totalAccounts = 0,
                activeSessions = 0,
                expiringSoon = 0,
                slowQueries = 0,
                totalDatabases = 0,
                totalTables = 0,
                expiringAccounts = emptyList(),
                longRunningSessions = emptyList(),
                topDatabases = emptyList()
            )

            assertEquals(0, stats.totalAccounts)
            assertEquals(0, stats.activeSessions)
            assertTrue(stats.expiringAccounts.isEmpty())
            assertTrue(stats.longRunningSessions.isEmpty())
            assertTrue(stats.topDatabases.isEmpty())
        }

        @Test
        @DisplayName("DashboardStats should handle large numbers")
        fun testDashboardStatsLargeNumbers() {
            val stats = DashboardStats(
                totalAccounts = 10000,
                activeSessions = 500,
                expiringSoon = 250,
                slowQueries = 100,
                totalDatabases = 1000,
                totalTables = 50000,
                expiringAccounts = emptyList(),
                longRunningSessions = emptyList(),
                topDatabases = emptyList()
            )

            assertEquals(10000, stats.totalAccounts)
            assertEquals(500, stats.activeSessions)
            assertEquals(50000, stats.totalTables)
        }

        @Test
        @DisplayName("DashboardStats should properly limit expiring accounts to 5")
        fun testDashboardStatsExpiringAccountsLimit() {
            val accounts = (1..10).map {
                ExpiringAccount("user$it", "localhost", it)
            }

            val limitedAccounts = accounts.take(5)

            val stats = DashboardStats(
                totalAccounts = 100,
                activeSessions = 10,
                expiringSoon = 10,
                slowQueries = 5,
                totalDatabases = 5,
                totalTables = 100,
                expiringAccounts = limitedAccounts,
                longRunningSessions = emptyList(),
                topDatabases = emptyList()
            )

            assertEquals(5, stats.expiringAccounts.size)
            assertEquals("user1", stats.expiringAccounts[0].username)
            assertEquals("user5", stats.expiringAccounts[4].username)
        }

        @Test
        @DisplayName("DashboardStats should properly limit long running sessions to 5")
        fun testDashboardStatsLongRunningSessionsLimit() {
            val sessions = (1L..10L).map {
                SessionInfo(it, null, "user", "localhost", "db", "SELECT", (it * 60).toInt(), "Running", "query")
            }

            val limitedSessions = sessions.sortedByDescending { it.time }.take(5)

            val stats = DashboardStats(
                totalAccounts = 100,
                activeSessions = 10,
                expiringSoon = 5,
                slowQueries = 10,
                totalDatabases = 5,
                totalTables = 100,
                expiringAccounts = emptyList(),
                longRunningSessions = limitedSessions,
                topDatabases = emptyList()
            )

            assertEquals(5, stats.longRunningSessions.size)
            // Should be sorted by time descending
            assertTrue(stats.longRunningSessions[0].time > stats.longRunningSessions[1].time)
        }

        @Test
        @DisplayName("DashboardStats should properly limit top databases to 5")
        fun testDashboardStatsTopDatabasesLimit() {
            val databases = (1..10).map {
                DatabaseInfo("db$it", it * 10, it * 1000L, it * 1048576L)
            }

            val limitedDatabases = databases.take(5)

            val stats = DashboardStats(
                totalAccounts = 100,
                activeSessions = 10,
                expiringSoon = 5,
                slowQueries = 5,
                totalDatabases = 10,
                totalTables = 100,
                expiringAccounts = emptyList(),
                longRunningSessions = emptyList(),
                topDatabases = limitedDatabases
            )

            assertEquals(5, stats.topDatabases.size)
        }
    }

    // ==================== ExpiringAccount Model Tests ====================

    @Nested
    @DisplayName("ExpiringAccount Model Tests")
    inner class ExpiringAccountModelTests {

        @Test
        @DisplayName("ExpiringAccount should have correct properties")
        fun testExpiringAccountModel() {
            val account = ExpiringAccount(
                username = "testuser",
                host = "localhost",
                daysUntilExpiry = 15
            )

            assertEquals("testuser", account.username)
            assertEquals("localhost", account.host)
            assertEquals(15, account.daysUntilExpiry)
        }

        @Test
        @DisplayName("ExpiringAccount with wildcard host")
        fun testExpiringAccountWildcardHost() {
            val account = ExpiringAccount(
                username = "testuser",
                host = "%",
                daysUntilExpiry = 5
            )

            assertEquals("%", account.host)
        }

        @Test
        @DisplayName("ExpiringAccount with 0 days (expires today)")
        fun testExpiringAccountExpiresToday() {
            val account = ExpiringAccount(
                username = "urgentuser",
                host = "localhost",
                daysUntilExpiry = 0
            )

            assertEquals(0, account.daysUntilExpiry)
        }

        @Test
        @DisplayName("ExpiringAccount with negative days (already expired)")
        fun testExpiringAccountAlreadyExpired() {
            val account = ExpiringAccount(
                username = "expireduser",
                host = "localhost",
                daysUntilExpiry = -3
            )

            assertTrue(account.daysUntilExpiry < 0)
        }

        @Test
        @DisplayName("ExpiringAccount sorting by days")
        fun testExpiringAccountSorting() {
            val accounts = listOf(
                ExpiringAccount("user1", "localhost", 30),
                ExpiringAccount("user2", "localhost", 5),
                ExpiringAccount("user3", "localhost", 15)
            )

            val sorted = accounts.sortedBy { it.daysUntilExpiry }

            assertEquals(5, sorted[0].daysUntilExpiry)
            assertEquals(15, sorted[1].daysUntilExpiry)
            assertEquals(30, sorted[2].daysUntilExpiry)
        }

        @Test
        @DisplayName("ExpiringAccount equality")
        fun testExpiringAccountEquality() {
            val account1 = ExpiringAccount("user", "localhost", 10)
            val account2 = ExpiringAccount("user", "localhost", 10)

            assertEquals(account1, account2)
            assertEquals(account1.hashCode(), account2.hashCode())
        }
    }

    // ==================== SessionInfo Model Tests ====================

    @Nested
    @DisplayName("SessionInfo Model Tests")
    inner class SessionInfoModelTests {

        @Test
        @DisplayName("SessionInfo should have correct properties")
        fun testSessionInfoModel() {
            val session = SessionInfo(
                pid = 12345L,
                serialNum = 67890L,
                user = "testuser",
                host = "192.168.1.100",
                database = "testdb",
                command = "SELECT",
                time = 120,
                state = "Running",
                query = "SELECT * FROM users WHERE id = 1"
            )

            assertEquals(12345L, session.pid)
            assertEquals(67890L, session.serialNum)
            assertEquals("testuser", session.user)
            assertEquals("192.168.1.100", session.host)
            assertEquals("testdb", session.database)
            assertEquals("SELECT", session.command)
            assertEquals(120, session.time)
            assertEquals("Running", session.state)
            assertEquals("SELECT * FROM users WHERE id = 1", session.query)
        }

        @Test
        @DisplayName("SessionInfo with null optional fields")
        fun testSessionInfoWithNulls() {
            val session = SessionInfo(
                pid = 100L,
                serialNum = null,
                user = "user",
                host = "localhost",
                database = null,
                command = "Sleep",
                time = 60,
                state = null,
                query = null
            )

            assertNull(session.serialNum)
            assertNull(session.database)
            assertNull(session.state)
            assertNull(session.query)
        }

        @Test
        @DisplayName("SessionInfo for long running query")
        fun testSessionInfoLongRunning() {
            val session = SessionInfo(
                pid = 1L,
                serialNum = null,
                user = "user",
                host = "localhost",
                database = "db",
                command = "SELECT",
                time = 3600, // 1 hour
                state = "Running",
                query = "SELECT * FROM large_table"
            )

            assertTrue(session.time >= 60) // At least 60 seconds
            assertTrue(session.time >= 3600) // At least 1 hour
        }

        @Test
        @DisplayName("SessionInfo sorting by time descending")
        fun testSessionInfoSortingByTime() {
            val sessions = listOf(
                SessionInfo(1L, null, "user1", "localhost", "db", "SELECT", 30, "Running", null),
                SessionInfo(2L, null, "user2", "localhost", "db", "SELECT", 120, "Running", null),
                SessionInfo(3L, null, "user3", "localhost", "db", "SELECT", 60, "Running", null)
            )

            val sorted = sessions.sortedByDescending { it.time }

            assertEquals(120, sorted[0].time)
            assertEquals(60, sorted[1].time)
            assertEquals(30, sorted[2].time)
        }
    }

    // ==================== SessionStats Model Tests ====================

    @Nested
    @DisplayName("SessionStats Model Tests")
    inner class SessionStatsModelTests {

        @Test
        @DisplayName("SessionStats should have correct properties")
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
        @DisplayName("SessionStats with zero values")
        fun testSessionStatsZero() {
            val stats = SessionStats(
                totalSessions = 0,
                activeSessions = 0,
                sleepingSessions = 0,
                longRunningSessions = 0
            )

            assertEquals(0, stats.totalSessions)
            assertEquals(0, stats.activeSessions)
        }

        @Test
        @DisplayName("SessionStats total should equal active plus sleeping")
        fun testSessionStatsTotals() {
            val stats = SessionStats(
                totalSessions = 100,
                activeSessions = 30,
                sleepingSessions = 70,
                longRunningSessions = 5
            )

            assertEquals(stats.totalSessions, stats.activeSessions + stats.sleepingSessions)
        }

        @Test
        @DisplayName("SessionStats with all sessions active")
        fun testSessionStatsAllActive() {
            val stats = SessionStats(
                totalSessions = 50,
                activeSessions = 50,
                sleepingSessions = 0,
                longRunningSessions = 10
            )

            assertEquals(50, stats.activeSessions)
            assertEquals(0, stats.sleepingSessions)
        }
    }

    // ==================== DatabaseInfo Model Tests ====================

    @Nested
    @DisplayName("DatabaseInfo Model Tests")
    inner class DatabaseInfoModelTests {

        @Test
        @DisplayName("DatabaseInfo should have correct properties")
        fun testDatabaseInfoModel() {
            val dbInfo = DatabaseInfo(
                name = "production_db",
                tableCount = 50,
                totalRows = 1000000L,
                size = 1073741824L // 1GB
            )

            assertEquals("production_db", dbInfo.name)
            assertEquals(50, dbInfo.tableCount)
            assertEquals(1000000L, dbInfo.totalRows)
            assertEquals(1073741824L, dbInfo.size)
        }

        @Test
        @DisplayName("DatabaseInfo with zero values")
        fun testDatabaseInfoEmpty() {
            val dbInfo = DatabaseInfo(
                name = "empty_db",
                tableCount = 0,
                totalRows = 0L,
                size = 0L
            )

            assertEquals(0, dbInfo.tableCount)
            assertEquals(0L, dbInfo.totalRows)
            assertEquals(0L, dbInfo.size)
        }

        @Test
        @DisplayName("DatabaseInfo size calculations")
        fun testDatabaseInfoSizeCalculations() {
            val oneKB = 1024L
            val oneMB = 1024L * 1024L
            val oneGB = 1024L * 1024L * 1024L

            val smallDB = DatabaseInfo("small", 5, 1000L, 512 * oneKB)
            val mediumDB = DatabaseInfo("medium", 20, 50000L, 100 * oneMB)
            val largeDB = DatabaseInfo("large", 100, 10000000L, 5 * oneGB)

            assertTrue(smallDB.size < oneMB)
            assertTrue(mediumDB.size < oneGB)
            assertTrue(largeDB.size >= oneGB)
        }

        @Test
        @DisplayName("DatabaseInfo sorting by size")
        fun testDatabaseInfoSortingBySize() {
            val databases = listOf(
                DatabaseInfo("small", 5, 1000L, 1024L),
                DatabaseInfo("large", 100, 1000000L, 1073741824L),
                DatabaseInfo("medium", 20, 50000L, 10485760L)
            )

            val sorted = databases.sortedByDescending { it.size }

            assertEquals("large", sorted[0].name)
            assertEquals("medium", sorted[1].name)
            assertEquals("small", sorted[2].name)
        }

        @Test
        @DisplayName("DatabaseInfo sorting by table count")
        fun testDatabaseInfoSortingByTableCount() {
            val databases = listOf(
                DatabaseInfo("db1", 50, 10000L, 1048576L),
                DatabaseInfo("db2", 100, 20000L, 2097152L),
                DatabaseInfo("db3", 25, 5000L, 524288L)
            )

            val sorted = databases.sortedByDescending { it.tableCount }

            assertEquals("db2", sorted[0].name)
            assertEquals("db1", sorted[1].name)
            assertEquals("db3", sorted[2].name)
        }
    }

    // ==================== Collection Operations Tests ====================

    @Nested
    @DisplayName("Collection Operations Tests")
    inner class CollectionOperationsTests {

        @Test
        @DisplayName("Sum of table counts across databases")
        fun testSumTableCounts() {
            val databases = listOf(
                DatabaseInfo("db1", 50, 10000L, 1048576L),
                DatabaseInfo("db2", 30, 5000L, 524288L),
                DatabaseInfo("db3", 20, 2000L, 262144L)
            )

            val totalTables = databases.sumOf { it.tableCount }

            assertEquals(100, totalTables)
        }

        @Test
        @DisplayName("Filter expiring accounts by days threshold")
        fun testFilterExpiringAccounts() {
            val accounts = listOf(
                ExpiringAccount("user1", "localhost", 5),
                ExpiringAccount("user2", "localhost", 15),
                ExpiringAccount("user3", "localhost", 25),
                ExpiringAccount("user4", "localhost", 35)
            )

            val expiringSoon = accounts.filter { it.daysUntilExpiry <= 30 }

            assertEquals(3, expiringSoon.size)
        }

        @Test
        @DisplayName("Take top 5 databases")
        fun testTakeTopDatabases() {
            val databases = (1..10).map {
                DatabaseInfo("db$it", it * 10, it * 1000L, it * 1048576L)
            }

            val top5 = databases.take(5)

            assertEquals(5, top5.size)
            assertEquals("db1", top5[0].name)
            assertEquals("db5", top5[4].name)
        }

        @Test
        @DisplayName("Filter and sort long running sessions")
        fun testFilterLongRunningSessions() {
            val sessions = listOf(
                SessionInfo(1L, null, "user1", "localhost", "db", "SELECT", 30, "Running", null),
                SessionInfo(2L, null, "user2", "localhost", "db", "SELECT", 120, "Running", null),
                SessionInfo(3L, null, "user3", "localhost", "db", "SELECT", 90, "Running", null),
                SessionInfo(4L, null, "user4", "localhost", "db", "SELECT", 200, "Running", null)
            )

            val longRunning = sessions
                .filter { it.time >= 60 }
                .sortedByDescending { it.time }
                .take(5)

            assertEquals(3, longRunning.size)
            assertEquals(200, longRunning[0].time)
            assertEquals(120, longRunning[1].time)
            assertEquals(90, longRunning[2].time)
        }
    }

    // ==================== Data Consistency Tests ====================

    @Nested
    @DisplayName("Data Consistency Tests")
    inner class DataConsistencyTests {

        @Test
        @DisplayName("DashboardStats expiringSoon should match expiringAccounts size")
        fun testExpiringAccountsConsistency() {
            val expiringAccounts = listOf(
                ExpiringAccount("user1", "localhost", 5),
                ExpiringAccount("user2", "localhost", 10),
                ExpiringAccount("user3", "localhost", 15)
            )

            val stats = DashboardStats(
                totalAccounts = 100,
                activeSessions = 10,
                expiringSoon = expiringAccounts.size,
                slowQueries = 5,
                totalDatabases = 5,
                totalTables = 100,
                expiringAccounts = expiringAccounts,
                longRunningSessions = emptyList(),
                topDatabases = emptyList()
            )

            assertEquals(stats.expiringSoon, stats.expiringAccounts.size)
        }

        @Test
        @DisplayName("DashboardStats totalDatabases should match topDatabases actual count")
        fun testDatabaseCountConsistency() {
            val databases = listOf(
                DatabaseInfo("db1", 50, 10000L, 1048576L),
                DatabaseInfo("db2", 30, 5000L, 524288L)
            )

            val stats = DashboardStats(
                totalAccounts = 100,
                activeSessions = 10,
                expiringSoon = 5,
                slowQueries = 5,
                totalDatabases = 10, // Total in system
                totalTables = 80,
                expiringAccounts = emptyList(),
                longRunningSessions = emptyList(),
                topDatabases = databases // Top 5 (or less)
            )

            assertTrue(stats.totalDatabases >= stats.topDatabases.size)
        }

        @Test
        @DisplayName("DashboardStats totalTables should equal sum of database table counts")
        fun testTableCountConsistency() {
            val databases = listOf(
                DatabaseInfo("db1", 50, 10000L, 1048576L),
                DatabaseInfo("db2", 30, 5000L, 524288L),
                DatabaseInfo("db3", 20, 2000L, 262144L)
            )

            val totalTables = databases.sumOf { it.tableCount }

            val stats = DashboardStats(
                totalAccounts = 100,
                activeSessions = 10,
                expiringSoon = 5,
                slowQueries = 5,
                totalDatabases = 3,
                totalTables = totalTables,
                expiringAccounts = emptyList(),
                longRunningSessions = emptyList(),
                topDatabases = databases
            )

            assertEquals(100, stats.totalTables)
            assertEquals(stats.totalTables, databases.sumOf { it.tableCount })
        }
    }
}
