package com.dbaccman.integration

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.DatabaseType
import com.dbaccman.model.CreateAccountRequest
import com.dbaccman.model.GrantPermissionRequest
import com.dbaccman.model.RevokePermissionRequest
import com.dbaccman.service.AccountService
import com.dbaccman.service.PermissionService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Integration tests for Account Creation and Privilege Granting.
 *
 * These tests require actual database connections to run.
 * Set the environment variable RUN_INTEGRATION_TESTS=true to enable.
 *
 * Required environment variables for each database:
 * - ORACLE_HOST, ORACLE_PORT, ORACLE_USER, ORACLE_PASSWORD, ORACLE_SID
 * - MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE
 * - POSTGRESQL_HOST, POSTGRESQL_PORT, POSTGRESQL_USER, POSTGRESQL_PASSWORD, POSTGRESQL_DATABASE
 */
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Account and Privilege Integration Tests")
class AccountPrivilegeIntegrationTest {

    private val accountService = AccountService()
    private val permissionService = PermissionService()

    // Test account credentials
    private val testUsername = "test_integration_user"
    private val testPassword = "TestPass123!"
    private val testExpireDays = 90

    // ==================== Oracle Tests ====================

    @Nested
    @DisplayName("Oracle Integration Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class OracleIntegrationTests {

        private var adminSessionId: String? = null

        @BeforeAll
        fun setup() {
            val host = System.getenv("ORACLE_HOST") ?: "localhost"
            val port = System.getenv("ORACLE_PORT")?.toIntOrNull() ?: 1521
            val user = System.getenv("ORACLE_USER") ?: "APIM_OWNER"
            val password = System.getenv("ORACLE_PASSWORD") ?: "common_password"
            val database = System.getenv("ORACLE_SID") ?: "XE"

            try {
                adminSessionId = SessionConnectionManager.createSession(
                    host = host,
                    port = port,
                    username = user,
                    password = password,
                    dbType = DatabaseType.ORACLE,
                    database = database
                )
            } catch (e: Exception) {
                println("Oracle connection failed: ${e.message}")
            }
        }

        @AfterAll
        fun cleanup() {
            adminSessionId?.let { sessionId ->
                try {
                    accountService.deleteAccount(sessionId, testUsername.uppercase(), "%")
                } catch (_: Exception) { }
                SessionConnectionManager.closeSession(sessionId)
            }
        }

        private fun requireSession(): String {
            val sessionId = adminSessionId
            assumeTrue(sessionId != null, "Oracle not connected")
            return sessionId!!
        }

        @Test
        @Order(1)
        @DisplayName("1. Create Oracle account")
        fun testCreateAccount() {
            val sessionId = requireSession()

            // Delete if exists from previous failed test
            try {
                accountService.deleteAccount(sessionId, testUsername.uppercase(), "%")
            } catch (_: Exception) { }

            val request = CreateAccountRequest(
                username = testUsername,
                password = testPassword,
                expireDays = testExpireDays
            )

            val account = accountService.createAccount(sessionId, request)

            assertEquals(testUsername.uppercase(), account.username.uppercase())
            assertEquals(testExpireDays, account.passwordLifetime)
        }

        @Test
        @Order(2)
        @DisplayName("2. Grant CREATE SESSION privilege")
        fun testGrantCreateSession() {
            val sessionId = requireSession()

            val request = GrantPermissionRequest(
                username = testUsername,
                privileges = listOf("CREATE SESSION")
            )

            assertDoesNotThrow {
                permissionService.grantPermission(sessionId, request)
            }
        }

        @Test
        @Order(3)
        @DisplayName("3. Grant additional system privileges (CREATE TABLE, CREATE VIEW)")
        fun testGrantAdditionalPrivileges() {
            val sessionId = requireSession()

            val request = GrantPermissionRequest(
                username = testUsername,
                privileges = listOf("CREATE TABLE", "CREATE VIEW")
            )

            assertDoesNotThrow {
                permissionService.grantPermission(sessionId, request)
            }
        }

        @Test
        @Order(4)
        @DisplayName("4. Verify privileges are granted")
        fun testVerifyPrivileges() {
            val sessionId = requireSession()

            val permissions = permissionService.getUserPermissions(sessionId, testUsername, "%")

            assertTrue(permissions.isNotEmpty(), "Permissions should not be empty")

            val privilegeNames = permissions.map { it.privilege.uppercase() }
            assertTrue(privilegeNames.contains("CREATE SESSION"), "Should have CREATE SESSION")
            assertTrue(privilegeNames.contains("CREATE TABLE"), "Should have CREATE TABLE")
            assertTrue(privilegeNames.contains("CREATE VIEW"), "Should have CREATE VIEW")
        }

        @Test
        @Order(5)
        @DisplayName("5. Verify new account can login with CREATE SESSION")
        fun testNewAccountLogin() {
            requireSession()

            val host = System.getenv("ORACLE_HOST") ?: "localhost"
            val port = System.getenv("ORACLE_PORT")?.toIntOrNull() ?: 1521
            val database = System.getenv("ORACLE_SID") ?: "XE"

            var testSessionId: String? = null
            assertDoesNotThrow {
                testSessionId = SessionConnectionManager.createSession(
                    host = host,
                    port = port,
                    username = testUsername,
                    password = testPassword,
                    dbType = DatabaseType.ORACLE,
                    database = database
                )
            }

            assertNotNull(testSessionId, "Should be able to login with new account")
            testSessionId?.let { SessionConnectionManager.closeSession(it) }
        }

        @Test
        @Order(6)
        @DisplayName("6. Cleanup - Delete test account")
        fun testDeleteAccount() {
            val sessionId = requireSession()

            assertDoesNotThrow {
                accountService.deleteAccount(sessionId, testUsername.uppercase(), "%")
            }
        }
    }

    // ==================== MySQL Tests ====================

    @Nested
    @DisplayName("MySQL Integration Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class MySQLIntegrationTests {

        private var adminSessionId: String? = null

        @BeforeAll
        fun setup() {
            val host = System.getenv("MYSQL_HOST") ?: "localhost"
            val port = System.getenv("MYSQL_PORT")?.toIntOrNull() ?: 3306
            val user = System.getenv("MYSQL_USER") ?: "root"
            val password = System.getenv("MYSQL_PASSWORD") ?: "common_password"
            val database = System.getenv("MYSQL_DATABASE") ?: "manual"

            try {
                adminSessionId = SessionConnectionManager.createSession(
                    host = host,
                    port = port,
                    username = user,
                    password = password,
                    dbType = DatabaseType.MYSQL,
                    database = database
                )
            } catch (e: Exception) {
                println("MySQL connection failed: ${e.message}")
            }
        }

        @AfterAll
        fun cleanup() {
            adminSessionId?.let { sessionId ->
                try {
                    accountService.deleteAccount(sessionId, testUsername, "%")
                } catch (_: Exception) { }
                SessionConnectionManager.closeSession(sessionId)
            }
        }

        private fun requireSession(): String {
            val sessionId = adminSessionId
            assumeTrue(sessionId != null, "MySQL not connected")
            return sessionId!!
        }

        @Test
        @Order(1)
        @DisplayName("1. Create MySQL account")
        fun testCreateAccount() {
            val sessionId = requireSession()

            try {
                accountService.deleteAccount(sessionId, testUsername, "%")
            } catch (_: Exception) { }

            val request = CreateAccountRequest(
                username = testUsername,
                host = "%",
                password = testPassword,
                expireDays = testExpireDays
            )

            val account = accountService.createAccount(sessionId, request)

            assertEquals(testUsername, account.username)
        }

        @Test
        @Order(2)
        @DisplayName("2. Grant global privileges (SELECT, INSERT on *.*)")
        fun testGrantGlobalPrivileges() {
            val sessionId = requireSession()

            val request = GrantPermissionRequest(
                username = testUsername,
                host = "%",
                database = "*",
                table = "*",
                privileges = listOf("SELECT", "INSERT")
            )

            assertDoesNotThrow {
                permissionService.grantPermission(sessionId, request)
            }
        }

        @Test
        @Order(3)
        @DisplayName("3. Grant database-level privileges")
        fun testGrantDatabasePrivileges() {
            val sessionId = requireSession()
            val database = System.getenv("MYSQL_DATABASE") ?: "manual"

            val request = GrantPermissionRequest(
                username = testUsername,
                host = "%",
                database = database,
                table = "*",
                privileges = listOf("UPDATE", "DELETE")
            )

            assertDoesNotThrow {
                permissionService.grantPermission(sessionId, request)
            }
        }

        @Test
        @Order(4)
        @DisplayName("4. Verify new account can login")
        fun testNewAccountLogin() {
            requireSession()

            val host = System.getenv("MYSQL_HOST") ?: "localhost"
            val port = System.getenv("MYSQL_PORT")?.toIntOrNull() ?: 3306
            val database = System.getenv("MYSQL_DATABASE") ?: "manual"

            var testSessionId: String? = null
            assertDoesNotThrow {
                testSessionId = SessionConnectionManager.createSession(
                    host = host,
                    port = port,
                    username = testUsername,
                    password = testPassword,
                    dbType = DatabaseType.MYSQL,
                    database = database
                )
            }

            assertNotNull(testSessionId, "Should be able to login with new account")
            testSessionId?.let { SessionConnectionManager.closeSession(it) }
        }

        @Test
        @Order(5)
        @DisplayName("5. Cleanup - Delete test account")
        fun testDeleteAccount() {
            val sessionId = requireSession()

            assertDoesNotThrow {
                accountService.deleteAccount(sessionId, testUsername, "%")
            }
        }
    }

    // ==================== PostgreSQL Tests ====================

    @Nested
    @DisplayName("PostgreSQL Integration Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class PostgreSQLIntegrationTests {

        private var adminSessionId: String? = null

        @BeforeAll
        fun setup() {
            val host = System.getenv("POSTGRESQL_HOST") ?: "localhost"
            val port = System.getenv("POSTGRESQL_PORT")?.toIntOrNull() ?: 5432
            val user = System.getenv("POSTGRESQL_USER") ?: "common_user"
            val password = System.getenv("POSTGRESQL_PASSWORD") ?: "common_password"
            val database = System.getenv("POSTGRESQL_DATABASE") ?: "manual"

            try {
                adminSessionId = SessionConnectionManager.createSession(
                    host = host,
                    port = port,
                    username = user,
                    password = password,
                    dbType = DatabaseType.POSTGRESQL,
                    database = database
                )
            } catch (e: Exception) {
                println("PostgreSQL connection failed: ${e.message}")
            }
        }

        @AfterAll
        fun cleanup() {
            adminSessionId?.let { sessionId ->
                try {
                    val revokeRequest = RevokePermissionRequest(
                        username = testUsername,
                        database = System.getenv("POSTGRESQL_DATABASE") ?: "manual",
                        privileges = listOf("CONNECT", "CREATE")
                    )
                    permissionService.revokePermission(sessionId, revokeRequest)
                } catch (_: Exception) { }
                try {
                    accountService.deleteAccount(sessionId, testUsername, "%")
                } catch (_: Exception) { }
                SessionConnectionManager.closeSession(sessionId)
            }
        }

        private fun requireSession(): String {
            val sessionId = adminSessionId
            assumeTrue(sessionId != null, "PostgreSQL not connected")
            return sessionId!!
        }

        @Test
        @Order(1)
        @DisplayName("1. Create PostgreSQL account")
        fun testCreateAccount() {
            val sessionId = requireSession()

            // Cleanup first
            try {
                val revokeRequest = RevokePermissionRequest(
                    username = testUsername,
                    database = System.getenv("POSTGRESQL_DATABASE") ?: "manual",
                    privileges = listOf("CONNECT", "CREATE")
                )
                permissionService.revokePermission(sessionId, revokeRequest)
            } catch (_: Exception) { }
            try {
                accountService.deleteAccount(sessionId, testUsername, "%")
            } catch (_: Exception) { }

            val request = CreateAccountRequest(
                username = testUsername,
                password = testPassword,
                expireDays = testExpireDays
            )

            val account = accountService.createAccount(sessionId, request)

            assertEquals(testUsername, account.username)
            assertEquals(testExpireDays, account.passwordLifetime)
        }

        @Test
        @Order(2)
        @DisplayName("2. Grant CONNECT privilege")
        fun testGrantConnectPrivilege() {
            val sessionId = requireSession()
            val database = System.getenv("POSTGRESQL_DATABASE") ?: "manual"

            val request = GrantPermissionRequest(
                username = testUsername,
                database = database,
                privileges = listOf("CONNECT")
            )

            assertDoesNotThrow {
                permissionService.grantPermission(sessionId, request)
            }
        }

        @Test
        @Order(3)
        @DisplayName("3. Grant CREATE privilege")
        fun testGrantCreatePrivilege() {
            val sessionId = requireSession()
            val database = System.getenv("POSTGRESQL_DATABASE") ?: "manual"

            val request = GrantPermissionRequest(
                username = testUsername,
                database = database,
                privileges = listOf("CREATE")
            )

            assertDoesNotThrow {
                permissionService.grantPermission(sessionId, request)
            }
        }

        @Test
        @Order(4)
        @DisplayName("4. Verify new account can login with CONNECT")
        fun testNewAccountLogin() {
            requireSession()

            val host = System.getenv("POSTGRESQL_HOST") ?: "localhost"
            val port = System.getenv("POSTGRESQL_PORT")?.toIntOrNull() ?: 5432
            val database = System.getenv("POSTGRESQL_DATABASE") ?: "manual"

            var testSessionId: String? = null
            assertDoesNotThrow {
                testSessionId = SessionConnectionManager.createSession(
                    host = host,
                    port = port,
                    username = testUsername,
                    password = testPassword,
                    dbType = DatabaseType.POSTGRESQL,
                    database = database
                )
            }

            assertNotNull(testSessionId, "Should be able to login with new account")
            testSessionId?.let { SessionConnectionManager.closeSession(it) }
        }

        @Test
        @Order(5)
        @DisplayName("5. Revoke privileges before deletion")
        fun testRevokePrivileges() {
            val sessionId = requireSession()
            val database = System.getenv("POSTGRESQL_DATABASE") ?: "manual"

            val request = RevokePermissionRequest(
                username = testUsername,
                database = database,
                privileges = listOf("CONNECT", "CREATE")
            )

            assertDoesNotThrow {
                permissionService.revokePermission(sessionId, request)
            }
        }

        @Test
        @Order(6)
        @DisplayName("6. Cleanup - Delete test account")
        fun testDeleteAccount() {
            val sessionId = requireSession()

            assertDoesNotThrow {
                accountService.deleteAccount(sessionId, testUsername, "%")
            }
        }
    }
}
