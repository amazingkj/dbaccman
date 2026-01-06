package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.DatabaseType
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.dialect.PostgreSQLDialect
import com.dbaccman.model.*
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
import java.sql.Statement

class PrivilegeServiceTest {

    private lateinit var privilegeService: PrivilegeService

    @BeforeEach
    fun setUp() {
        privilegeService = PrivilegeService()
        mockkObject(SessionConnectionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("PrivilegeService should be instantiable")
    fun testPrivilegeServiceInstantiation() {
        assertNotNull(privilegeService)
    }

    // ==================== PasswordExpiryInfo Model Tests ====================

    @Nested
    @DisplayName("PasswordExpiryInfo Model Tests")
    inner class PasswordExpiryInfoModelTests {

        @Test
        @DisplayName("PasswordExpiryInfo should have correct properties")
        fun testPasswordExpiryInfoModel() {
            val info = PasswordExpiryInfo(
                username = "testuser",
                host = "localhost",
                daysUntilExpiry = 30,
                passwordLastChanged = "2024-01-01 00:00:00",
                isExpired = false
            )

            assertEquals("testuser", info.username)
            assertEquals("localhost", info.host)
            assertEquals(30, info.daysUntilExpiry)
            assertEquals("2024-01-01 00:00:00", info.passwordLastChanged)
            assertFalse(info.isExpired)
        }

        @Test
        @DisplayName("PasswordExpiryInfo with null expiry days (no expiration)")
        fun testPasswordExpiryInfoNoExpiration() {
            val info = PasswordExpiryInfo(
                username = "admin",
                host = "%",
                daysUntilExpiry = null,
                passwordLastChanged = "2024-01-01 00:00:00",
                isExpired = false
            )

            assertNull(info.daysUntilExpiry)
            assertFalse(info.isExpired)
        }

        @Test
        @DisplayName("PasswordExpiryInfo with null password last changed")
        fun testPasswordExpiryInfoNullLastChanged() {
            val info = PasswordExpiryInfo(
                username = "user",
                host = "localhost",
                daysUntilExpiry = 90,
                passwordLastChanged = null,
                isExpired = false
            )

            assertNull(info.passwordLastChanged)
        }

        @Test
        @DisplayName("PasswordExpiryInfo for expired password")
        fun testPasswordExpiryInfoExpired() {
            val info = PasswordExpiryInfo(
                username = "expireduser",
                host = "localhost",
                daysUntilExpiry = -5,
                passwordLastChanged = "2023-01-01 00:00:00",
                isExpired = true
            )

            assertTrue(info.isExpired)
            assertTrue(info.daysUntilExpiry!! < 0)
        }

        @Test
        @DisplayName("PasswordExpiryInfo for password expiring soon")
        fun testPasswordExpiryInfoExpiringSoon() {
            val info = PasswordExpiryInfo(
                username = "user",
                host = "localhost",
                daysUntilExpiry = 5,
                passwordLastChanged = "2024-01-01 00:00:00",
                isExpired = false
            )

            assertFalse(info.isExpired)
            assertTrue(info.daysUntilExpiry!! <= 30)
        }

        @Test
        @DisplayName("PasswordExpiryInfo equality")
        fun testPasswordExpiryInfoEquality() {
            val info1 = PasswordExpiryInfo("user", "localhost", 30, "2024-01-01", false)
            val info2 = PasswordExpiryInfo("user", "localhost", 30, "2024-01-01", false)

            assertEquals(info1, info2)
            assertEquals(info1.hashCode(), info2.hashCode())
        }
    }

    // ==================== isAdmin Tests for MySQL ====================

    @Nested
    @DisplayName("isAdmin Tests for MySQL")
    inner class IsAdminMySQLTests {

        @Test
        @DisplayName("isAdmin should return true for MySQL user with ALL PRIVILEGES")
        fun testIsAdminMySQLAllPrivileges() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.MYSQL
            every { mockDialect.getAdminCheckQuery() } returns "SHOW GRANTS FOR CURRENT_USER()"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returnsMany listOf(true, false)
            every { mockResultSet.getString(1) } returns "GRANT ALL PRIVILEGES ON *.* TO 'admin'@'%' WITH GRANT OPTION"

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val isAdmin = privilegeService.isAdmin("test-session")

            assertTrue(isAdmin)
        }

        @Test
        @DisplayName("isAdmin should return true for MySQL user with CREATE USER privilege")
        fun testIsAdminMySQLCreateUser() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.MYSQL
            every { mockDialect.getAdminCheckQuery() } returns "SHOW GRANTS FOR CURRENT_USER()"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returnsMany listOf(true, false)
            every { mockResultSet.getString(1) } returns "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE USER ON *.* TO 'admin'@'%'"

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val isAdmin = privilegeService.isAdmin("test-session")

            assertTrue(isAdmin)
        }

        @Test
        @DisplayName("isAdmin should return false for MySQL regular user")
        fun testIsAdminMySQLRegularUser() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.MYSQL
            every { mockDialect.getAdminCheckQuery() } returns "SHOW GRANTS FOR CURRENT_USER()"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returnsMany listOf(true, false)
            every { mockResultSet.getString(1) } returns "GRANT SELECT, INSERT, UPDATE ON testdb.* TO 'user'@'%'"

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val isAdmin = privilegeService.isAdmin("test-session")

            assertFalse(isAdmin)
        }
    }

    // ==================== isAdmin Tests for Oracle ====================

    @Nested
    @DisplayName("isAdmin Tests for Oracle")
    inner class IsAdminOracleTests {

        @Test
        @DisplayName("isAdmin should return true for Oracle user with DBA role")
        fun testIsAdminOracleDBA() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.ORACLE
            every { mockDialect.getAdminCheckQuery() } returns "SELECT GRANTED_ROLE FROM USER_ROLE_PRIVS WHERE GRANTED_ROLE = 'DBA'"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returns true
            every { mockResultSet.getString(1) } returns "DBA"

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val isAdmin = privilegeService.isAdmin("test-session")

            assertTrue(isAdmin)
        }

        @Test
        @DisplayName("isAdmin should return false for Oracle regular user")
        fun testIsAdminOracleRegularUser() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.ORACLE
            every { mockDialect.getAdminCheckQuery() } returns "SELECT GRANTED_ROLE FROM USER_ROLE_PRIVS WHERE GRANTED_ROLE = 'DBA'"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returns false

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val isAdmin = privilegeService.isAdmin("test-session")

            assertFalse(isAdmin)
        }

        @Test
        @DisplayName("isAdmin should return false for Oracle user with non-DBA role")
        fun testIsAdminOracleNonDBARole() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.ORACLE
            every { mockDialect.getAdminCheckQuery() } returns "SELECT GRANTED_ROLE FROM USER_ROLE_PRIVS WHERE GRANTED_ROLE = 'DBA'"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returns true
            every { mockResultSet.getString(1) } returns "CONNECT"

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val isAdmin = privilegeService.isAdmin("test-session")

            assertFalse(isAdmin)
        }
    }

    // ==================== isAdmin Tests for PostgreSQL ====================

    @Nested
    @DisplayName("isAdmin Tests for PostgreSQL")
    inner class IsAdminPostgreSQLTests {

        @Test
        @DisplayName("isAdmin should return true for PostgreSQL superuser")
        fun testIsAdminPostgreSQLSuperuser() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<PostgreSQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.POSTGRESQL
            every { mockDialect.getAdminCheckQuery() } returns "SELECT usesuper FROM pg_user WHERE usename = CURRENT_USER AND usesuper = true"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returns true

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val isAdmin = privilegeService.isAdmin("test-session")

            assertTrue(isAdmin)
        }

        @Test
        @DisplayName("isAdmin should return false for PostgreSQL regular user")
        fun testIsAdminPostgreSQLRegularUser() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<PostgreSQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.POSTGRESQL
            every { mockDialect.getAdminCheckQuery() } returns "SELECT usesuper FROM pg_user WHERE usename = CURRENT_USER AND usesuper = true"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returns false

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val isAdmin = privilegeService.isAdmin("test-session")

            assertFalse(isAdmin)
        }
    }

    // ==================== detectRole Tests ====================

    @Nested
    @DisplayName("detectRole Tests")
    inner class DetectRoleTests {

        @Test
        @DisplayName("detectRole should return 'admin' for admin user")
        fun testDetectRoleAdmin() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.MYSQL
            every { mockDialect.getAdminCheckQuery() } returns "SHOW GRANTS FOR CURRENT_USER()"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returnsMany listOf(true, false)
            every { mockResultSet.getString(1) } returns "GRANT ALL PRIVILEGES ON *.* TO 'admin'@'%'"

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val role = privilegeService.detectRole("test-session")

            assertEquals("admin", role)
        }

        @Test
        @DisplayName("detectRole should return 'user' for regular user")
        fun testDetectRoleUser() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.MYSQL
            every { mockDialect.getAdminCheckQuery() } returns "SHOW GRANTS FOR CURRENT_USER()"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returnsMany listOf(true, false)
            every { mockResultSet.getString(1) } returns "GRANT SELECT ON testdb.* TO 'user'@'%'"

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val role = privilegeService.detectRole("test-session")

            assertEquals("user", role)
        }
    }

    // ==================== getMyPasswordExpiry Tests for MySQL ====================

    @Nested
    @DisplayName("getMyPasswordExpiry Tests for MySQL")
    inner class GetMyPasswordExpiryMySQLTests {

        @Test
        @DisplayName("getMyPasswordExpiry should return password info for MySQL admin")
        fun testGetMyPasswordExpiryMySQLAdmin() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getPasswordExpiryQuery() } returns "SELECT user, host, password_expired, password_lifetime FROM mysql.user WHERE user = ? AND host = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "admin") } just Runs
            every { mockPreparedStatement.setString(2, "localhost") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet

            every { mockResultSet.next() } returns true
            every { mockResultSet.getString("user") } returns "admin"
            every { mockResultSet.getString("host") } returns "localhost"
            every { mockResultSet.getObject("days_until_expiry") } returns 90
            every { mockResultSet.getString("password_last_changed") } returns "2024-01-01 00:00:00"
            every { mockResultSet.getBoolean("is_expired") } returns false

            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val info = privilegeService.getMyPasswordExpiry("test-session", "admin", "localhost")

            assertEquals("admin", info.username)
            assertEquals("localhost", info.host)
            assertEquals(90, info.daysUntilExpiry)
            assertEquals("2024-01-01 00:00:00", info.passwordLastChanged)
            assertFalse(info.isExpired)
        }

        @Test
        @DisplayName("getMyPasswordExpiry should use user-level query for MySQL regular user")
        fun testGetMyPasswordExpiryMySQLUser() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            // Admin query fails
            every { mockDialect.getPasswordExpiryQuery() } returns "SELECT user, host FROM mysql.user WHERE user = ? AND host = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "user") } just Runs
            every { mockPreparedStatement.setString(2, "localhost") } just Runs
            every { mockPreparedStatement.executeQuery() } throws RuntimeException("Access denied")
            every { mockPreparedStatement.close() } just Runs

            // User-level query succeeds
            every { mockDialect.getMyPasswordExpiryQuery() } returns "SELECT CURRENT_USER() as user"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returns true
            every { mockResultSet.getString("user") } returns "user"
            every { mockResultSet.getString("host") } returns "localhost"
            every { mockResultSet.getObject("days_until_expiry") } returns 60
            every { mockResultSet.getString("password_last_changed") } returns "2024-01-15 00:00:00"
            every { mockResultSet.getBoolean("is_expired") } returns false

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val info = privilegeService.getMyPasswordExpiry("test-session", "user", "localhost")

            assertEquals("user", info.username)
            assertEquals("localhost", info.host)
        }

        @Test
        @DisplayName("getMyPasswordExpiry should return basic info when all queries fail")
        fun testGetMyPasswordExpiryMySQLFallback() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            // Admin query fails
            every { mockDialect.getPasswordExpiryQuery() } returns "SELECT user FROM mysql.user WHERE user = ? AND host = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "user") } just Runs
            every { mockPreparedStatement.setString(2, "localhost") } just Runs
            every { mockPreparedStatement.executeQuery() } throws RuntimeException("Access denied")
            every { mockPreparedStatement.close() } just Runs

            // User query also fails
            every { mockDialect.getMyPasswordExpiryQuery() } returns "SELECT CURRENT_USER()"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } throws RuntimeException("Access denied")
            every { mockStatement.close() } just Runs

            every { mockConnection.close() } just Runs

            val info = privilegeService.getMyPasswordExpiry("test-session", "user", "localhost")

            assertEquals("user", info.username)
            assertEquals("localhost", info.host)
            assertNull(info.daysUntilExpiry)
            assertNull(info.passwordLastChanged)
            assertFalse(info.isExpired)
        }
    }

    // ==================== getMyPasswordExpiry Tests for Oracle ====================

    @Nested
    @DisplayName("getMyPasswordExpiry Tests for Oracle")
    inner class GetMyPasswordExpiryOracleTests {

        @Test
        @DisplayName("getMyPasswordExpiry should use user-level query for Oracle user")
        fun testGetMyPasswordExpiryOracleUser() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockUserPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            val adminSql = "SELECT username FROM DBA_USERS WHERE username = ? AND account_status = ?"
            val userSql = "SELECT username FROM USER_USERS WHERE username = ? AND account_status = ?"

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            // Admin query fails
            every { mockDialect.getPasswordExpiryQuery() } returns adminSql
            every { mockConnection.prepareStatement(adminSql) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "USER") } just Runs
            every { mockPreparedStatement.setString(2, "OPEN") } just Runs
            every { mockPreparedStatement.executeQuery() } throws RuntimeException("Insufficient privileges")
            every { mockPreparedStatement.close() } just Runs

            // User-level query succeeds
            every { mockDialect.getMyPasswordExpiryQuery() } returns userSql
            every { mockConnection.prepareStatement(userSql) } returns mockUserPreparedStatement
            every { mockUserPreparedStatement.setString(1, "OPEN") } just Runs
            every { mockUserPreparedStatement.executeQuery() } returns mockResultSet

            every { mockResultSet.next() } returns true
            every { mockResultSet.getString("user") } returns "USER"
            every { mockResultSet.getString("host") } returns "OPEN"
            every { mockResultSet.getObject("days_until_expiry") } returns 45
            every { mockResultSet.getString("password_last_changed") } returns "2024-01-01"
            every { mockResultSet.getBoolean("is_expired") } returns false

            every { mockResultSet.close() } just Runs
            every { mockUserPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val info = privilegeService.getMyPasswordExpiry("test-session", "USER", "OPEN")

            assertEquals("USER", info.username)
            assertEquals("OPEN", info.host)
            assertEquals(45, info.daysUntilExpiry)
        }
    }

    // ==================== getPasswordExpiryDays Tests ====================

    @Nested
    @DisplayName("getPasswordExpiryDays Tests")
    inner class GetPasswordExpiryDaysTests {

        @Test
        @DisplayName("getPasswordExpiryDays should return days for user with expiry")
        fun testGetPasswordExpiryDaysWithExpiry() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getPasswordExpiryDaysQuery() } returns "SELECT days_until_expiry FROM mysql.user WHERE user = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "testuser") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet

            every { mockResultSet.next() } returns true
            every { mockResultSet.getObject("days_until_expiry") } returns 30

            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val days = privilegeService.getPasswordExpiryDays("test-session", "testuser")

            assertEquals(30, days)
        }

        @Test
        @DisplayName("getPasswordExpiryDays should return null when no expiry is set")
        fun testGetPasswordExpiryDaysNoExpiry() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getPasswordExpiryDaysQuery() } returns "SELECT days_until_expiry FROM mysql.user WHERE user = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "testuser") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet

            every { mockResultSet.next() } returns true
            every { mockResultSet.getObject("days_until_expiry") } returns null

            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val days = privilegeService.getPasswordExpiryDays("test-session", "testuser")

            assertNull(days)
        }

        @Test
        @DisplayName("getPasswordExpiryDays should return null when user not found")
        fun testGetPasswordExpiryDaysUserNotFound() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getPasswordExpiryDaysQuery() } returns "SELECT days_until_expiry FROM mysql.user WHERE user = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "nonexistent") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet

            every { mockResultSet.next() } returns false

            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val days = privilegeService.getPasswordExpiryDays("test-session", "nonexistent")

            assertNull(days)
        }

        @Test
        @DisplayName("getPasswordExpiryDays should return null on exception")
        fun testGetPasswordExpiryDaysException() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getPasswordExpiryDaysQuery() } returns "SELECT days_until_expiry FROM mysql.user WHERE user = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "testuser") } just Runs
            every { mockPreparedStatement.executeQuery() } throws RuntimeException("Access denied")
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val days = privilegeService.getPasswordExpiryDays("test-session", "testuser")

            assertNull(days)
        }
    }

    // ==================== Integration Scenarios ====================

    @Nested
    @DisplayName("Integration Scenarios")
    inner class IntegrationScenariosTests {

        @Test
        @DisplayName("Admin user workflow: check admin status and get own password expiry")
        fun testAdminUserWorkflow() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet1 = mockk<ResultSet>()
            val mockResultSet2 = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            // Setup for isAdmin check
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.MYSQL
            every { mockDialect.getAdminCheckQuery() } returns "SHOW GRANTS FOR CURRENT_USER()"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet1
            every { mockConnection.close() } just Runs

            every { mockResultSet1.next() } returnsMany listOf(true, false)
            every { mockResultSet1.getString(1) } returns "GRANT ALL PRIVILEGES ON *.* TO 'admin'@'localhost'"
            every { mockResultSet1.close() } just Runs
            every { mockStatement.close() } just Runs

            // First call: isAdmin
            val isAdmin = privilegeService.isAdmin("test-session")
            assertTrue(isAdmin)

            // Setup for getMyPasswordExpiry
            every { mockDialect.getPasswordExpiryQuery() } returns "SELECT user, host FROM mysql.user WHERE user = ? AND host = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "admin") } just Runs
            every { mockPreparedStatement.setString(2, "localhost") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet2

            every { mockResultSet2.next() } returns true
            every { mockResultSet2.getString("user") } returns "admin"
            every { mockResultSet2.getString("host") } returns "localhost"
            every { mockResultSet2.getObject("days_until_expiry") } returns 90
            every { mockResultSet2.getString("password_last_changed") } returns "2024-01-01"
            every { mockResultSet2.getBoolean("is_expired") } returns false
            every { mockResultSet2.close() } just Runs
            every { mockPreparedStatement.close() } just Runs

            // Second call: getMyPasswordExpiry
            val passwordInfo = privilegeService.getMyPasswordExpiry("test-session", "admin", "localhost")
            assertEquals("admin", passwordInfo.username)
            assertEquals(90, passwordInfo.daysUntilExpiry)
        }

        @Test
        @DisplayName("Regular user workflow: check non-admin status")
        fun testRegularUserWorkflow() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.type } returns DatabaseType.ORACLE
            every { mockDialect.getAdminCheckQuery() } returns "SELECT GRANTED_ROLE FROM USER_ROLE_PRIVS WHERE GRANTED_ROLE = 'DBA'"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returns false
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val isAdmin = privilegeService.isAdmin("test-session")
            assertFalse(isAdmin)

            val role = privilegeService.detectRole("test-session")
            assertEquals("user", role)
        }
    }
}
