package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.dialect.PostgreSQLDialect
import com.dbaccman.model.GrantPermissionRequest
import com.dbaccman.model.MySQLPrivileges
import com.dbaccman.model.Permission
import com.dbaccman.model.RevokePermissionRequest
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

class PermissionServiceTest {

    private lateinit var permissionService: PermissionService

    @BeforeEach
    fun setUp() {
        permissionService = PermissionService()
        mockkObject(SessionConnectionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("PermissionService should be instantiable")
    fun testPermissionServiceInstantiation() {
        assertNotNull(permissionService)
    }

    // ==================== Permission Model Tests ====================

    @Nested
    @DisplayName("Permission Model Tests")
    inner class PermissionModelTests {

        @Test
        @DisplayName("Permission model should have correct properties")
        fun testPermissionModel() {
            val permission = Permission(
                grantee = "testuser@localhost",
                database = "testdb",
                table = "users",
                privilege = "SELECT",
                isGrantable = true
            )

            assertEquals("testuser@localhost", permission.grantee)
            assertEquals("testdb", permission.database)
            assertEquals("users", permission.table)
            assertEquals("SELECT", permission.privilege)
            assertTrue(permission.isGrantable)
        }

        @Test
        @DisplayName("Permission should have default table value of *")
        fun testPermissionDefaultTable() {
            val permission = Permission(
                grantee = "admin@%",
                database = "mydb",
                privilege = "ALL"
            )

            assertEquals("*", permission.table)
            assertFalse(permission.isGrantable)
        }

        @Test
        @DisplayName("Permission equality should work correctly")
        fun testPermissionEquality() {
            val perm1 = Permission(
                grantee = "user@host",
                database = "db",
                table = "tbl",
                privilege = "SELECT",
                isGrantable = true
            )

            val perm2 = Permission(
                grantee = "user@host",
                database = "db",
                table = "tbl",
                privilege = "SELECT",
                isGrantable = true
            )

            assertEquals(perm1, perm2)
            assertEquals(perm1.hashCode(), perm2.hashCode())
        }

        @Test
        @DisplayName("Different permissions should not be equal")
        fun testPermissionInequality() {
            val perm1 = Permission(
                grantee = "user1@host",
                database = "db",
                privilege = "SELECT"
            )

            val perm2 = Permission(
                grantee = "user2@host",
                database = "db",
                privilege = "SELECT"
            )

            assertNotEquals(perm1, perm2)
        }

        @Test
        @DisplayName("Permission copy should work correctly")
        fun testPermissionCopy() {
            val original = Permission(
                grantee = "user@host",
                database = "db",
                table = "tbl",
                privilege = "SELECT",
                isGrantable = false
            )

            val copied = original.copy(privilege = "UPDATE", isGrantable = true)

            assertEquals("UPDATE", copied.privilege)
            assertTrue(copied.isGrantable)
            assertEquals("user@host", copied.grantee)
        }
    }

    // ==================== GrantPermissionRequest Tests ====================

    @Nested
    @DisplayName("GrantPermissionRequest Tests")
    inner class GrantPermissionRequestTests {

        @Test
        @DisplayName("GrantPermissionRequest should have correct defaults")
        fun testGrantPermissionRequestDefaults() {
            val request = GrantPermissionRequest(
                username = "newuser",
                database = "testdb",
                privileges = listOf("SELECT", "INSERT")
            )

            assertEquals("newuser", request.username)
            assertEquals("%", request.host)
            assertEquals("testdb", request.database)
            assertEquals("*", request.table)
            assertEquals(2, request.privileges.size)
            assertTrue(request.privileges.contains("SELECT"))
            assertTrue(request.privileges.contains("INSERT"))
        }

        @Test
        @DisplayName("GrantPermissionRequest with custom host and table")
        fun testGrantPermissionRequestWithCustomValues() {
            val request = GrantPermissionRequest(
                username = "appuser",
                host = "192.168.1.%",
                database = "production",
                table = "orders",
                privileges = listOf("SELECT", "UPDATE")
            )

            assertEquals("appuser", request.username)
            assertEquals("192.168.1.%", request.host)
            assertEquals("production", request.database)
            assertEquals("orders", request.table)
        }

        @Test
        @DisplayName("GrantPermissionRequest with empty privileges list")
        fun testGrantPermissionRequestEmptyPrivileges() {
            val request = GrantPermissionRequest(
                username = "testuser",
                database = "testdb",
                privileges = emptyList()
            )

            assertTrue(request.privileges.isEmpty())
        }

        @Test
        @DisplayName("GrantPermissionRequest with multiple privileges")
        fun testGrantPermissionRequestMultiplePrivileges() {
            val request = GrantPermissionRequest(
                username = "poweruser",
                database = "analytics",
                privileges = MySQLPrivileges.ALL
            )

            assertEquals(15, request.privileges.size)
        }

        @Test
        @DisplayName("GrantPermissionRequest equality")
        fun testGrantPermissionRequestEquality() {
            val req1 = GrantPermissionRequest("user", "%", "db", "*", listOf("SELECT"))
            val req2 = GrantPermissionRequest("user", "%", "db", "*", listOf("SELECT"))

            assertEquals(req1, req2)
        }
    }

    // ==================== RevokePermissionRequest Tests ====================

    @Nested
    @DisplayName("RevokePermissionRequest Tests")
    inner class RevokePermissionRequestTests {

        @Test
        @DisplayName("RevokePermissionRequest should have correct defaults")
        fun testRevokePermissionRequestDefaults() {
            val request = RevokePermissionRequest(
                username = "olduser",
                database = "testdb",
                privileges = listOf("DELETE")
            )

            assertEquals("olduser", request.username)
            assertEquals("%", request.host)
            assertEquals("testdb", request.database)
            assertEquals("*", request.table)
            assertEquals(1, request.privileges.size)
            assertEquals("DELETE", request.privileges.first())
        }

        @Test
        @DisplayName("RevokePermissionRequest with custom values")
        fun testRevokePermissionRequestCustomValues() {
            val request = RevokePermissionRequest(
                username = "testuser",
                host = "localhost",
                database = "mydb",
                table = "users",
                privileges = listOf("INSERT", "UPDATE", "DELETE")
            )

            assertEquals("localhost", request.host)
            assertEquals("users", request.table)
            assertEquals(3, request.privileges.size)
        }

        @Test
        @DisplayName("RevokePermissionRequest equality")
        fun testRevokePermissionRequestEquality() {
            val req1 = RevokePermissionRequest("user", "%", "db", "*", listOf("SELECT"))
            val req2 = RevokePermissionRequest("user", "%", "db", "*", listOf("SELECT"))

            assertEquals(req1, req2)
        }
    }

    // ==================== MySQLPrivileges Tests ====================

    @Nested
    @DisplayName("MySQLPrivileges Tests")
    inner class MySQLPrivilegesTests {

        @Test
        @DisplayName("MySQLPrivileges.ALL should contain expected privileges")
        fun testMySQLPrivilegesAll() {
            val allPrivileges = MySQLPrivileges.ALL

            assertTrue(allPrivileges.contains("SELECT"))
            assertTrue(allPrivileges.contains("INSERT"))
            assertTrue(allPrivileges.contains("UPDATE"))
            assertTrue(allPrivileges.contains("DELETE"))
            assertTrue(allPrivileges.contains("CREATE"))
            assertTrue(allPrivileges.contains("DROP"))
            assertTrue(allPrivileges.contains("INDEX"))
            assertTrue(allPrivileges.contains("ALTER"))
            assertTrue(allPrivileges.contains("CREATE VIEW"))
            assertTrue(allPrivileges.contains("SHOW VIEW"))
            assertTrue(allPrivileges.contains("CREATE ROUTINE"))
            assertTrue(allPrivileges.contains("ALTER ROUTINE"))
            assertTrue(allPrivileges.contains("EXECUTE"))
            assertTrue(allPrivileges.contains("TRIGGER"))
            assertTrue(allPrivileges.contains("REFERENCES"))
        }

        @Test
        @DisplayName("MySQLPrivileges.READ_ONLY should only contain SELECT")
        fun testMySQLPrivilegesReadOnly() {
            val readOnlyPrivileges = MySQLPrivileges.READ_ONLY

            assertEquals(1, readOnlyPrivileges.size)
            assertEquals("SELECT", readOnlyPrivileges.first())
        }

        @Test
        @DisplayName("MySQLPrivileges.READ_WRITE should contain CRUD operations")
        fun testMySQLPrivilegesReadWrite() {
            val readWritePrivileges = MySQLPrivileges.READ_WRITE

            assertEquals(4, readWritePrivileges.size)
            assertTrue(readWritePrivileges.contains("SELECT"))
            assertTrue(readWritePrivileges.contains("INSERT"))
            assertTrue(readWritePrivileges.contains("UPDATE"))
            assertTrue(readWritePrivileges.contains("DELETE"))
        }

        @Test
        @DisplayName("MySQLPrivileges.DDL should contain DDL operations")
        fun testMySQLPrivilegesDDL() {
            val ddlPrivileges = MySQLPrivileges.DDL

            assertEquals(4, ddlPrivileges.size)
            assertTrue(ddlPrivileges.contains("CREATE"))
            assertTrue(ddlPrivileges.contains("DROP"))
            assertTrue(ddlPrivileges.contains("INDEX"))
            assertTrue(ddlPrivileges.contains("ALTER"))
        }
    }

    // ==================== Service Method Tests with Mocking ====================

    @Nested
    @DisplayName("Service Method Tests with Mocking")
    inner class ServiceMethodTests {

        @Test
        @DisplayName("getUserPermissions should return permissions from database")
        fun testGetUserPermissions() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.formatGrantee("testuser", "localhost") } returns "'testuser'@'localhost'"
            every { mockDialect.getGlobalPrivilegesQuery() } returns "SELECT * FROM global_privileges WHERE grantee = ?"
            every { mockDialect.getSchemaPrivilegesQuery() } returns "SELECT * FROM schema_privileges WHERE grantee = ?"
            every { mockDialect.getTablePrivilegesQuery() } returns "SELECT * FROM table_privileges WHERE grantee = ?"

            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, any()) } just Runs

            // Global privileges result (empty), Schema privileges result, Table privileges result (empty)
            every { mockPreparedStatement.executeQuery() } returnsMany listOf(
                mockk<ResultSet>().also { emptyRs ->
                    every { emptyRs.next() } returns false
                    every { emptyRs.close() } just Runs
                },
                mockResultSet,
                mockk<ResultSet>().also { emptyRs ->
                    every { emptyRs.next() } returns false
                    every { emptyRs.close() } just Runs
                }
            )

            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getString("grantee") } returns "'testuser'@'localhost'"
            every { mockResultSet.getString("db") } returnsMany listOf("testdb", "testdb")
            every { mockResultSet.getString("privilege") } returnsMany listOf("SELECT", "INSERT")
            every { mockResultSet.getString("is_grantable") } returnsMany listOf("YES", "NO")
            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val permissions = permissionService.getUserPermissions("test-session", "testuser", "localhost")

            assertEquals(2, permissions.size)
            assertEquals("SELECT", permissions[0].privilege)
            assertTrue(permissions[0].isGrantable)
            assertEquals("INSERT", permissions[1].privilege)
            assertFalse(permissions[1].isGrantable)
        }

        @Test
        @DisplayName("grantPermission should execute GRANT SQL")
        fun testGrantPermission() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getGrantSql(any(), any(), any(), any(), any()) } returns "GRANT SELECT ON testdb.* TO 'user'@'%'"
            every { mockDialect.getFlushPrivilegesSql() } returns "FLUSH PRIVILEGES"

            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val request = GrantPermissionRequest(
                username = "user",
                database = "testdb",
                privileges = listOf("SELECT")
            )

            permissionService.grantPermission("test-session", request)

            verify { mockStatement.execute("GRANT SELECT ON testdb.* TO 'user'@'%'") }
            verify { mockStatement.execute("FLUSH PRIVILEGES") }
        }

        @Test
        @DisplayName("revokePermission should execute REVOKE SQL")
        fun testRevokePermission() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getRevokeSql(any(), any(), any(), any(), any()) } returns "REVOKE DELETE ON testdb.* FROM 'user'@'%'"
            every { mockDialect.getFlushPrivilegesSql() } returns "FLUSH PRIVILEGES"

            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val request = RevokePermissionRequest(
                username = "user",
                database = "testdb",
                privileges = listOf("DELETE")
            )

            permissionService.revokePermission("test-session", request)

            verify { mockStatement.execute("REVOKE DELETE ON testdb.* FROM 'user'@'%'") }
        }

        @Test
        @DisplayName("getDatabases should filter out system schemas")
        fun testGetDatabases() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getShowDatabasesQuery() } returns "SHOW DATABASES"
            every { mockDialect.getSystemSchemas() } returns listOf("information_schema", "mysql", "performance_schema", "sys")

            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.next() } returnsMany listOf(true, true, true, true, true, false)
            every { mockResultSet.getString(1) } returnsMany listOf("information_schema", "mysql", "testdb", "production", "sys")
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val databases = permissionService.getDatabases("test-session")

            assertEquals(2, databases.size)
            assertTrue(databases.contains("testdb"))
            assertTrue(databases.contains("production"))
            assertFalse(databases.contains("mysql"))
            assertFalse(databases.contains("information_schema"))
        }

        @Test
        @DisplayName("batchGrantPermissions should return success count and errors")
        fun testBatchGrantPermissions() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getGrantSql(listOf("SELECT"), "db1", "*", "user", "%") } returns "GRANT SELECT ON db1.* TO 'user'@'%'"
            every { mockDialect.getGrantSql(listOf("SELECT"), "db2", "*", "user", "%") } throws RuntimeException("Access denied")
            every { mockDialect.getGrantSql(listOf("SELECT"), "db3", "*", "user", "%") } returns "GRANT SELECT ON db3.* TO 'user'@'%'"
            every { mockDialect.getFlushPrivilegesSql() } returns "FLUSH PRIVILEGES"

            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute("GRANT SELECT ON db1.* TO 'user'@'%'") } returns true
            every { mockStatement.execute("GRANT SELECT ON db3.* TO 'user'@'%'") } returns true
            every { mockStatement.execute("FLUSH PRIVILEGES") } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val requests = listOf(
                GrantPermissionRequest("user", "%", "db1", "*", listOf("SELECT")),
                GrantPermissionRequest("user", "%", "db2", "*", listOf("SELECT")),
                GrantPermissionRequest("user", "%", "db3", "*", listOf("SELECT"))
            )

            val (successCount, errors) = permissionService.batchGrantPermissions("test-session", requests)

            assertEquals(2, successCount)
            assertEquals(1, errors.size)
            assertTrue(errors[0].contains("db2"))
        }

        @Test
        @DisplayName("batchGrantPermissions with empty list should return zero")
        fun testBatchGrantPermissionsEmpty() {
            val (successCount, errors) = permissionService.batchGrantPermissions("test-session", emptyList())

            assertEquals(0, successCount)
            assertTrue(errors.isEmpty())
        }

        @Test
        @DisplayName("grantPermission should handle system privileges (empty database)")
        fun testGrantSystemPrivileges() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.validatePrivileges(listOf("CREATE SESSION"), "*") } returns Pair(listOf("CREATE SESSION"), emptyList())
            every { mockDialect.getGrantSql(listOf("CREATE SESSION"), "", "*", "testuser", "%") } returns "GRANT CREATE SESSION TO TESTUSER"
            every { mockDialect.getFlushPrivilegesSql() } returns null

            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val request = GrantPermissionRequest(
                username = "testuser",
                database = "",
                privileges = listOf("CREATE SESSION")
            )

            permissionService.grantPermission("test-session", request)

            verify { mockStatement.execute("GRANT CREATE SESSION TO TESTUSER") }
        }

        @Test
        @DisplayName("grantPermission should handle multiple SQL statements")
        fun testGrantPermissionMultipleStatements() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<PostgreSQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            // PostgreSQL may return multiple statements separated by semicolons
            every { mockDialect.getGrantSql(any(), any(), any(), any(), any()) } returns "GRANT CONNECT ON DATABASE testdb TO \"user\"; GRANT SELECT ON ALL TABLES IN SCHEMA \"public\" TO \"user\""
            every { mockDialect.getFlushPrivilegesSql() } returns null

            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val request = GrantPermissionRequest(
                username = "user",
                database = "testdb",
                privileges = listOf("CONNECT", "SELECT")
            )

            permissionService.grantPermission("test-session", request)

            verify { mockStatement.execute("GRANT CONNECT ON DATABASE testdb TO \"user\"") }
            verify { mockStatement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA \"public\" TO \"user\"") }
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    @DisplayName("Edge Cases Tests")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("Permission with all fields null except required")
        fun testPermissionMinimalFields() {
            val permission = Permission(
                grantee = "user",
                database = "db",
                privilege = "SELECT"
            )

            assertEquals("*", permission.table)
            assertFalse(permission.isGrantable)
        }

        @Test
        @DisplayName("GrantPermissionRequest with special characters in database name")
        fun testGrantPermissionRequestSpecialChars() {
            val request = GrantPermissionRequest(
                username = "user",
                database = "test-db_v2",
                privileges = listOf("SELECT")
            )

            assertEquals("test-db_v2", request.database)
        }

        @Test
        @DisplayName("Multiple permissions for same grantee")
        fun testMultiplePermissionsSameGrantee() {
            val permissions = listOf(
                Permission("user@host", "db1", "*", "SELECT", false),
                Permission("user@host", "db2", "*", "SELECT", false),
                Permission("user@host", "db1", "table1", "INSERT", true)
            )

            val db1Perms = permissions.filter { it.database == "db1" }
            assertEquals(2, db1Perms.size)
        }
    }
}
