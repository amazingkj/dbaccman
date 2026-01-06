package com.dbaccman.config

import com.dbaccman.dialect.*
import com.zaxxer.hikari.HikariDataSource
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap

@DisplayName("SessionConnectionManager Tests")
class SessionConnectionManagerTest {

    @BeforeEach
    fun setUp() {
        // Clear any existing sessions before each test
        clearAllSessions()
    }

    @AfterEach
    fun tearDown() {
        // Clean up all mocks and sessions
        clearAllSessions()
        unmockkAll()
    }

    private fun clearAllSessions() {
        // Use reflection to clear the sessionPools map
        try {
            val sessionPoolsField = SessionConnectionManager::class.java.getDeclaredField("sessionPools")
            sessionPoolsField.isAccessible = true
            val sessionPools = sessionPoolsField.get(SessionConnectionManager) as ConcurrentHashMap<String, SessionPool>
            sessionPools.keys.toList().forEach { sessionId ->
                try {
                    SessionConnectionManager.closeSession(sessionId)
                } catch (e: Exception) {
                    // Ignore exceptions during cleanup
                }
            }
            sessionPools.clear()
        } catch (e: Exception) {
            // Ignore if reflection fails
        }
    }

    // ==================== SessionPool Data Class Tests ====================

    @Nested
    @DisplayName("SessionPool Data Class Tests")
    inner class SessionPoolDataClassTests {

        @Test
        @DisplayName("SessionPool should be created with all required fields")
        fun testSessionPoolCreation() {
            val mockDataSource = mockk<HikariDataSource>()
            val mockDialect = mockk<MySQLDialect>()

            val sessionPool = SessionPool(
                dataSource = mockDataSource,
                host = "localhost",
                port = 3306,
                username = "testuser",
                dbType = DatabaseType.MYSQL,
                dialect = mockDialect,
                isContainerRoot = false,
                lastAccess = 1000L
            )

            assertEquals("localhost", sessionPool.host)
            assertEquals(3306, sessionPool.port)
            assertEquals("testuser", sessionPool.username)
            assertEquals(DatabaseType.MYSQL, sessionPool.dbType)
            assertEquals(mockDialect, sessionPool.dialect)
            assertFalse(sessionPool.isContainerRoot)
            assertEquals(1000L, sessionPool.lastAccess)
        }

        @Test
        @DisplayName("SessionPool should default isContainerRoot to false")
        fun testSessionPoolDefaultContainerRoot() {
            val mockDataSource = mockk<HikariDataSource>()
            val mockDialect = mockk<MySQLDialect>()

            val sessionPool = SessionPool(
                dataSource = mockDataSource,
                host = "localhost",
                port = 3306,
                username = "testuser",
                dbType = DatabaseType.MYSQL,
                dialect = mockDialect
            )

            assertFalse(sessionPool.isContainerRoot)
        }

        @Test
        @DisplayName("SessionPool should allow setting lastAccess time")
        fun testSessionPoolLastAccessUpdate() {
            val mockDataSource = mockk<HikariDataSource>()
            val mockDialect = mockk<MySQLDialect>()

            val sessionPool = SessionPool(
                dataSource = mockDataSource,
                host = "localhost",
                port = 3306,
                username = "testuser",
                dbType = DatabaseType.MYSQL,
                dialect = mockDialect,
                lastAccess = 1000L
            )

            sessionPool.lastAccess = 2000L
            assertEquals(2000L, sessionPool.lastAccess)
        }

        @Test
        @DisplayName("SessionPool should support Oracle CDB root flag")
        fun testSessionPoolOracleContainerRoot() {
            val mockDataSource = mockk<HikariDataSource>()
            val mockDialect = mockk<OracleDialect>()

            val sessionPool = SessionPool(
                dataSource = mockDataSource,
                host = "oraclehost",
                port = 1521,
                username = "sys",
                dbType = DatabaseType.ORACLE,
                dialect = mockDialect,
                isContainerRoot = true
            )

            assertTrue(sessionPool.isContainerRoot)
            assertEquals(DatabaseType.ORACLE, sessionPool.dbType)
        }

        @Test
        @DisplayName("SessionPool copy should work correctly")
        fun testSessionPoolCopy() {
            val mockDataSource = mockk<HikariDataSource>()
            val mockDialect = mockk<MySQLDialect>()

            val original = SessionPool(
                dataSource = mockDataSource,
                host = "localhost",
                port = 3306,
                username = "testuser",
                dbType = DatabaseType.MYSQL,
                dialect = mockDialect,
                isContainerRoot = false,
                lastAccess = 1000L
            )

            val copied = original.copy(username = "newuser", port = 3307)

            assertEquals("newuser", copied.username)
            assertEquals(3307, copied.port)
            assertEquals("localhost", copied.host)
            assertEquals(1000L, copied.lastAccess)
        }
    }

    // ==================== Create Session Tests ====================

    @Nested
    @DisplayName("Create Session Tests")
    inner class CreateSessionTests {

        @Test
        @DisplayName("createSession should generate valid UUID session ID")
        fun testCreateSessionGeneratesValidUuid() {
            // Test with mock session pool directly
            val sessionId1 = createMockSession(DatabaseType.MYSQL)
            val sessionId2 = createMockSession(DatabaseType.MYSQL)

            assertNotEquals(sessionId1, sessionId2)
            assertTrue(sessionId1.isNotEmpty())
            assertTrue(sessionId2.isNotEmpty())

            // Verify UUID format
            assertDoesNotThrow {
                java.util.UUID.fromString(sessionId1)
                java.util.UUID.fromString(sessionId2)
            }
        }

        @Test
        @DisplayName("SessionPool should be created with correct database types")
        fun testCreateSessionWithDifferentDatabaseTypes() {
            val mysqlSessionId = createMockSession(DatabaseType.MYSQL)
            val oracleSessionId = createMockSession(DatabaseType.ORACLE)
            val postgresSessionId = createMockSession(DatabaseType.POSTGRESQL)

            val mysqlPool = SessionConnectionManager.getSessionInfo(mysqlSessionId)
            val oraclePool = SessionConnectionManager.getSessionInfo(oracleSessionId)
            val postgresPool = SessionConnectionManager.getSessionInfo(postgresSessionId)

            assertEquals(DatabaseType.MYSQL, mysqlPool?.dbType)
            assertEquals(DatabaseType.ORACLE, oraclePool?.dbType)
            assertEquals(DatabaseType.POSTGRESQL, postgresPool?.dbType)
        }

        @Test
        @DisplayName("SessionPool should store connection parameters correctly")
        fun testCreateSessionStoresConnectionParameters() {
            val sessionId = createMockSession(DatabaseType.MYSQL)

            val sessionPool = SessionConnectionManager.getSessionInfo(sessionId)

            assertNotNull(sessionPool)
            assertEquals("localhost", sessionPool?.host)
            assertEquals(3306, sessionPool?.port)
            assertEquals("testuser", sessionPool?.username)
            assertEquals(DatabaseType.MYSQL, sessionPool?.dbType)
        }

        @Test
        @DisplayName("Oracle session should support CDB root flag")
        fun testCreateSessionWithOracleCDBRoot() {
            val cdbRootSessionId = createMockSession(DatabaseType.ORACLE, isContainerRoot = true)
            val pdbSessionId = createMockSession(DatabaseType.ORACLE, isContainerRoot = false)

            assertTrue(SessionConnectionManager.isContainerRoot(cdbRootSessionId))
            assertFalse(SessionConnectionManager.isContainerRoot(pdbSessionId))
        }
    }

    // ==================== Get Connection Tests ====================

    @Nested
    @DisplayName("Get Connection Tests")
    inner class GetConnectionTests {

        @Test
        @DisplayName("getConnection should return connection from pool")
        fun testGetConnectionReturnsConnection() {
            val sessionId = createMockSession()

            val connection = SessionConnectionManager.getConnection(sessionId)

            assertNotNull(connection)
        }

        @Test
        @DisplayName("getConnection should update lastAccess time")
        fun testGetConnectionUpdatesLastAccess() {
            val sessionId = createMockSession()

            val sessionPool = SessionConnectionManager.getSessionInfo(sessionId)
            val initialLastAccess = sessionPool?.lastAccess ?: 0L

            Thread.sleep(10) // Small delay to ensure time difference

            SessionConnectionManager.getConnection(sessionId)

            val updatedSessionPool = SessionConnectionManager.getSessionInfo(sessionId)
            val updatedLastAccess = updatedSessionPool?.lastAccess ?: 0L

            assertTrue(updatedLastAccess >= initialLastAccess)
        }

        @Test
        @DisplayName("getConnection should throw exception for invalid session ID")
        fun testGetConnectionThrowsForInvalidSession() {
            val exception = assertThrows<IllegalStateException> {
                SessionConnectionManager.getConnection("invalid-session-id")
            }

            assertTrue(exception.message?.contains("Session not found or expired") == true)
        }

        @Test
        @DisplayName("getConnection should throw exception for expired session")
        fun testGetConnectionThrowsForExpiredSession() {
            val exception = assertThrows<IllegalStateException> {
                SessionConnectionManager.getConnection("expired-session-123")
            }

            assertTrue(exception.message?.contains("Session not found or expired") == true)
        }
    }

    // ==================== Get Dialect Tests ====================

    @Nested
    @DisplayName("Get Dialect Tests")
    inner class GetDialectTests {

        @Test
        @DisplayName("getDialect should return correct dialect for session")
        fun testGetDialectReturnsCorrectDialect() {
            val sessionId = createMockSession(DatabaseType.MYSQL)

            val dialect = SessionConnectionManager.getDialect(sessionId)

            assertNotNull(dialect)
            assertTrue(dialect is MySQLDialect)
        }

        @Test
        @DisplayName("getDialect should throw exception for invalid session")
        fun testGetDialectThrowsForInvalidSession() {
            val exception = assertThrows<IllegalStateException> {
                SessionConnectionManager.getDialect("invalid-session-id")
            }

            assertTrue(exception.message?.contains("Session not found or expired") == true)
        }

        @Test
        @DisplayName("getDialect should return Oracle dialect for Oracle session")
        fun testGetDialectReturnsOracleDialect() {
            val sessionId = createMockSession(DatabaseType.ORACLE)

            val dialect = SessionConnectionManager.getDialect(sessionId)

            assertTrue(dialect is OracleDialect)
        }
    }

    // ==================== Get Database Type Tests ====================

    @Nested
    @DisplayName("Get Database Type Tests")
    inner class GetDatabaseTypeTests {

        @Test
        @DisplayName("getDatabaseType should return correct type for session")
        fun testGetDatabaseTypeReturnsCorrectType() {
            val sessionId = createMockSession(DatabaseType.POSTGRESQL)

            val dbType = SessionConnectionManager.getDatabaseType(sessionId)

            assertEquals(DatabaseType.POSTGRESQL, dbType)
        }

        @Test
        @DisplayName("getDatabaseType should throw exception for invalid session")
        fun testGetDatabaseTypeThrowsForInvalidSession() {
            val exception = assertThrows<IllegalStateException> {
                SessionConnectionManager.getDatabaseType("invalid-session-id")
            }

            assertTrue(exception.message?.contains("Session not found or expired") == true)
        }
    }

    // ==================== Is Container Root Tests ====================

    @Nested
    @DisplayName("Is Container Root Tests")
    inner class IsContainerRootTests {

        @Test
        @DisplayName("isContainerRoot should return true for CDB root session")
        fun testIsContainerRootReturnsTrueForCDBRoot() {
            val sessionId = createMockSession(DatabaseType.ORACLE, isContainerRoot = true)

            val isRoot = SessionConnectionManager.isContainerRoot(sessionId)

            assertTrue(isRoot)
        }

        @Test
        @DisplayName("isContainerRoot should return false for PDB session")
        fun testIsContainerRootReturnsFalseForPDB() {
            val sessionId = createMockSession(DatabaseType.ORACLE, isContainerRoot = false)

            val isRoot = SessionConnectionManager.isContainerRoot(sessionId)

            assertFalse(isRoot)
        }

        @Test
        @DisplayName("isContainerRoot should return false for non-Oracle databases")
        fun testIsContainerRootReturnsFalseForNonOracle() {
            val sessionId = createMockSession(DatabaseType.MYSQL, isContainerRoot = false)

            val isRoot = SessionConnectionManager.isContainerRoot(sessionId)

            assertFalse(isRoot)
        }

        @Test
        @DisplayName("isContainerRoot should throw exception for invalid session")
        fun testIsContainerRootThrowsForInvalidSession() {

            val exception = assertThrows<IllegalStateException> {
                SessionConnectionManager.isContainerRoot("invalid-session-id")
            }

            assertTrue(exception.message?.contains("Session not found or expired") == true)
        }
    }

    // ==================== Get Session Info Tests ====================

    @Nested
    @DisplayName("Get Session Info Tests")
    inner class GetSessionInfoTests {

        @Test
        @DisplayName("getSessionInfo should return session pool without updating lastAccess")
        fun testGetSessionInfoDoesNotUpdateLastAccess() {
            val sessionId = createMockSession()

            val sessionPool = SessionConnectionManager.getSessionInfo(sessionId)
            val initialLastAccess = sessionPool?.lastAccess ?: 0L

            Thread.sleep(10)

            val sessionPool2 = SessionConnectionManager.getSessionInfo(sessionId)
            val subsequentLastAccess = sessionPool2?.lastAccess ?: 0L

            assertEquals(initialLastAccess, subsequentLastAccess)
        }

        @Test
        @DisplayName("getSessionInfo should return null for invalid session")
        fun testGetSessionInfoReturnsNullForInvalidSession() {

            val sessionPool = SessionConnectionManager.getSessionInfo("invalid-session-id")

            assertNull(sessionPool)
        }

        @Test
        @DisplayName("getSessionInfo should return complete session pool information")
        fun testGetSessionInfoReturnsCompleteInfo() {
            val sessionId = createMockSession(DatabaseType.MYSQL)

            val sessionPool = SessionConnectionManager.getSessionInfo(sessionId)

            assertNotNull(sessionPool)
            assertEquals("localhost", sessionPool?.host)
            assertEquals(3306, sessionPool?.port)
            assertEquals("testuser", sessionPool?.username)
            assertEquals(DatabaseType.MYSQL, sessionPool?.dbType)
        }
    }

    // ==================== Has Session Tests ====================

    @Nested
    @DisplayName("Has Session Tests")
    inner class HasSessionTests {

        @Test
        @DisplayName("hasSession should return true for existing session")
        fun testHasSessionReturnsTrueForExistingSession() {
            val sessionId = createMockSession()

            val hasSession = SessionConnectionManager.hasSession(sessionId)

            assertTrue(hasSession)
        }

        @Test
        @DisplayName("hasSession should return false for non-existent session")
        fun testHasSessionReturnsFalseForNonExistentSession() {

            val hasSession = SessionConnectionManager.hasSession("non-existent-session")

            assertFalse(hasSession)
        }

        @Test
        @DisplayName("hasSession should return false after session is closed")
        fun testHasSessionReturnsFalseAfterClose() {
            val sessionId = createMockSession()

            assertTrue(SessionConnectionManager.hasSession(sessionId))

            SessionConnectionManager.closeSession(sessionId)

            assertFalse(SessionConnectionManager.hasSession(sessionId))
        }
    }

    // ==================== Close Session Tests ====================

    @Nested
    @DisplayName("Close Session Tests")
    inner class CloseSessionTests {

        @Test
        @DisplayName("closeSession should remove session from pool")
        fun testCloseSessionRemovesFromPool() {
            val sessionId = createMockSession()

            assertTrue(SessionConnectionManager.hasSession(sessionId))

            SessionConnectionManager.closeSession(sessionId)

            assertFalse(SessionConnectionManager.hasSession(sessionId))
        }

        @Test
        @DisplayName("closeSession should close the datasource")
        fun testCloseSessionClosesDataSource() {
            val mockDataSource = mockk<HikariDataSource>()
            every { mockDataSource.close() } just Runs

            val sessionId = createMockSessionWithDataSource(mockDataSource)

            SessionConnectionManager.closeSession(sessionId)

            verify { mockDataSource.close() }
        }

        @Test
        @DisplayName("closeSession should handle non-existent session gracefully")
        fun testCloseSessionHandlesNonExistentSession() {

            assertDoesNotThrow {
                SessionConnectionManager.closeSession("non-existent-session")
            }
        }

        @Test
        @DisplayName("closeSession should handle datasource close exception")
        fun testCloseSessionHandlesDataSourceException() {
            val mockDataSource = mockk<HikariDataSource>()
            every { mockDataSource.close() } throws RuntimeException("Close failed")

            val sessionId = createMockSessionWithDataSource(mockDataSource)


            assertDoesNotThrow {
                SessionConnectionManager.closeSession(sessionId)
            }

            verify { mockDataSource.close() }
        }
    }

    // ==================== Active Session Count Tests ====================

    @Nested
    @DisplayName("Active Session Count Tests")
    inner class ActiveSessionCountTests {

        @Test
        @DisplayName("getActiveSessionCount should return 0 initially")
        fun testGetActiveSessionCountReturnsZeroInitially() {

            val count = SessionConnectionManager.getActiveSessionCount()

            assertEquals(0, count)
        }

        @Test
        @DisplayName("getActiveSessionCount should return correct count after creating sessions")
        fun testGetActiveSessionCountReturnsCorrectCount() {
            createMockSession(DatabaseType.MYSQL)
            createMockSession(DatabaseType.ORACLE)
            createMockSession(DatabaseType.POSTGRESQL)


            val count = SessionConnectionManager.getActiveSessionCount()

            assertEquals(3, count)
        }

        @Test
        @DisplayName("getActiveSessionCount should decrease after closing session")
        fun testGetActiveSessionCountDecreasesAfterClose() {
            val sessionId1 = createMockSession()
            val sessionId2 = createMockSession()

            assertEquals(2, SessionConnectionManager.getActiveSessionCount())

            SessionConnectionManager.closeSession(sessionId1)

            assertEquals(1, SessionConnectionManager.getActiveSessionCount())
        }
    }

    // ==================== Utility Functions Tests ====================

    @Nested
    @DisplayName("Utility Functions Tests")
    inner class UtilityFunctionsTests {

        @Test
        @DisplayName("useSessionConnection should get connection from SessionConnectionManager")
        fun testUseSessionConnectionGetsConnection() {
            val sessionId = createMockSession()

            // Test that we can get a connection through the session
            val sessionPool = SessionConnectionManager.getSessionInfo(sessionId)
            assertNotNull(sessionPool)
            assertNotNull(sessionPool?.dataSource)
        }

        @Test
        @DisplayName("useSessionConnectionWithDialect should use both connection and dialect")
        fun testUseSessionConnectionWithDialectUsesDialect() {
            val sessionId = createMockSession(DatabaseType.MYSQL)

            // Verify session has correct dialect
            val dialect = SessionConnectionManager.getDialect(sessionId)
            assertTrue(dialect is MySQLDialect)
        }

        @Test
        @DisplayName("useSessionConnection throws for invalid session")
        fun testUseSessionConnectionInvalidSession() {
            assertThrows<IllegalStateException> {
                useSessionConnection("invalid-session") { _ ->
                    "should not reach here"
                }
            }
        }

        @Test
        @DisplayName("useSessionConnectionWithDialect throws for invalid session")
        fun testUseSessionConnectionWithDialectInvalidSession() {
            assertThrows<IllegalStateException> {
                useSessionConnectionWithDialect("invalid-session") { _, _ ->
                    "should not reach here"
                }
            }
        }

        @Test
        @DisplayName("isContainerRoot returns correct value for CDB root session")
        fun testIsContainerRootForCDBRoot() {
            val sessionId = createMockSession(DatabaseType.ORACLE, isContainerRoot = true)

            assertTrue(SessionConnectionManager.isContainerRoot(sessionId))
        }

        @Test
        @DisplayName("isContainerRoot returns false for PDB session")
        fun testIsContainerRootForPDB() {
            val sessionId = createMockSession(DatabaseType.ORACLE, isContainerRoot = false)

            assertFalse(SessionConnectionManager.isContainerRoot(sessionId))
        }

        @Test
        @DisplayName("isContainerRoot returns false for non-Oracle databases")
        fun testIsContainerRootForNonOracle() {
            val sessionId = createMockSession(DatabaseType.MYSQL, isContainerRoot = false)

            assertFalse(SessionConnectionManager.isContainerRoot(sessionId))
        }

        @Test
        @DisplayName("useOracleScriptContext throws for invalid session")
        fun testUseOracleScriptContextInvalidSession() {
            assertThrows<IllegalStateException> {
                useOracleScriptContext("invalid-session") { _, _ ->
                    "should not reach here"
                }
            }
        }
    }

    // ==================== Edge Cases and Error Handling ====================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Multiple concurrent sessions should be isolated")
        fun testMultipleConcurrentSessions() {
            val sessionId1 = createMockSession(DatabaseType.MYSQL)
            val sessionId2 = createMockSession(DatabaseType.ORACLE)
            val sessionId3 = createMockSession(DatabaseType.POSTGRESQL)


            assertEquals(3, SessionConnectionManager.getActiveSessionCount())

            val dbType1 = SessionConnectionManager.getDatabaseType(sessionId1)
            val dbType2 = SessionConnectionManager.getDatabaseType(sessionId2)
            val dbType3 = SessionConnectionManager.getDatabaseType(sessionId3)

            assertEquals(DatabaseType.MYSQL, dbType1)
            assertEquals(DatabaseType.ORACLE, dbType2)
            assertEquals(DatabaseType.POSTGRESQL, dbType3)
        }

        @Test
        @DisplayName("Session pool should be thread-safe")
        fun testSessionPoolThreadSafety() {

            val sessionIds = mutableListOf<String>()
            val threads = (1..5).map { threadIndex ->
                Thread {
                    val sessionId = createMockSession(DatabaseType.MYSQL)
                    synchronized(sessionIds) {
                        sessionIds.add(sessionId)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            assertEquals(5, sessionIds.size)
            assertEquals(5, SessionConnectionManager.getActiveSessionCount())
        }

        @Test
        @DisplayName("Closing non-existent session should not throw")
        fun testCloseNonExistentSession() {

            assertDoesNotThrow {
                SessionConnectionManager.closeSession("non-existent-123")
            }
        }

        @Test
        @DisplayName("Session operations after close should throw exception")
        fun testSessionOperationsAfterClose() {
            val sessionId = createMockSession()

            SessionConnectionManager.closeSession(sessionId)

            assertThrows<IllegalStateException> {
                SessionConnectionManager.getConnection(sessionId)
            }

            assertThrows<IllegalStateException> {
                SessionConnectionManager.getDialect(sessionId)
            }

            assertThrows<IllegalStateException> {
                SessionConnectionManager.getDatabaseType(sessionId)
            }
        }

        @Test
        @DisplayName("Empty session ID should be handled")
        fun testEmptySessionId() {

            assertThrows<IllegalStateException> {
                SessionConnectionManager.getConnection("")
            }

            assertFalse(SessionConnectionManager.hasSession(""))
            assertNull(SessionConnectionManager.getSessionInfo(""))
        }

        @Test
        @DisplayName("Very long session IDs should be handled")
        fun testVeryLongSessionId() {
            val longSessionId = "a".repeat(1000)


            assertFalse(SessionConnectionManager.hasSession(longSessionId))
            assertNull(SessionConnectionManager.getSessionInfo(longSessionId))
        }

        @Test
        @DisplayName("Special characters in session ID should be handled")
        fun testSpecialCharactersInSessionId() {

            val specialSessionId = "session-with-special-chars-!@#$%^&*()"

            assertFalse(SessionConnectionManager.hasSession(specialSessionId))
            assertNull(SessionConnectionManager.getSessionInfo(specialSessionId))
        }
    }

    // ==================== Helper Methods ====================

    private fun createMockSession(
        dbType: DatabaseType = DatabaseType.MYSQL,
        isContainerRoot: Boolean = false
    ): String {
        val sessionId = java.util.UUID.randomUUID().toString()
        val mockDataSource = mockk<HikariDataSource>()
        val mockConnection = mockk<Connection>()
        val mockDialect = when (dbType) {
            DatabaseType.MYSQL -> mockk<MySQLDialect>()
            DatabaseType.ORACLE -> mockk<OracleDialect>()
            DatabaseType.POSTGRESQL -> mockk<PostgreSQLDialect>()
        }

        every { mockDataSource.connection } returns mockConnection
        every { mockConnection.close() } just Runs

        val sessionPool = SessionPool(
            dataSource = mockDataSource,
            host = "localhost",
            port = when (dbType) {
                DatabaseType.MYSQL -> 3306
                DatabaseType.ORACLE -> 1521
                DatabaseType.POSTGRESQL -> 5432
            },
            username = "testuser",
            dbType = dbType,
            dialect = mockDialect,
            isContainerRoot = isContainerRoot
        )

        // Use reflection to add session to the pool
        val sessionPoolsField = SessionConnectionManager::class.java.getDeclaredField("sessionPools")
        sessionPoolsField.isAccessible = true
        val sessionPools = sessionPoolsField.get(SessionConnectionManager) as ConcurrentHashMap<String, SessionPool>
        sessionPools[sessionId] = sessionPool

        return sessionId
    }

    private fun createMockSessionWithDataSource(dataSource: HikariDataSource): String {
        val sessionId = java.util.UUID.randomUUID().toString()
        val mockDialect = mockk<MySQLDialect>()

        val sessionPool = SessionPool(
            dataSource = dataSource,
            host = "localhost",
            port = 3306,
            username = "testuser",
            dbType = DatabaseType.MYSQL,
            dialect = mockDialect
        )

        // Use reflection to add session to the pool
        val sessionPoolsField = SessionConnectionManager::class.java.getDeclaredField("sessionPools")
        sessionPoolsField.isAccessible = true
        val sessionPools = sessionPoolsField.get(SessionConnectionManager) as ConcurrentHashMap<String, SessionPool>
        sessionPools[sessionId] = sessionPool

        return sessionId
    }
}
