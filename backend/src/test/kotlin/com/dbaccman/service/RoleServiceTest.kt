package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.DatabaseType
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.model.*
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

class RoleServiceTest {

    private lateinit var roleService: RoleService

    @BeforeEach
    fun setUp() {
        roleService = RoleService()
        mockkObject(SessionConnectionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("RoleService should be instantiable")
    fun testRoleServiceInstantiation() {
        assertNotNull(roleService)
    }

    // ==================== Role Model Tests ====================

    @Nested
    @DisplayName("Role Model Tests")
    inner class RoleModelTests {

        @Test
        @DisplayName("Role model should have correct properties")
        fun testRoleModel() {
            val role = Role(
                name = "DBA",
                isDefault = false,
                isAdmin = true
            )

            assertEquals("DBA", role.name)
            assertFalse(role.isDefault)
            assertTrue(role.isAdmin)
        }

        @Test
        @DisplayName("Role equality should work correctly")
        fun testRoleEquality() {
            val role1 = Role("CONNECT", true, false)
            val role2 = Role("CONNECT", true, false)

            assertEquals(role1, role2)
            assertEquals(role1.hashCode(), role2.hashCode())
        }

        @Test
        @DisplayName("Role copy should work correctly")
        fun testRoleCopy() {
            val original = Role("DBA", false, true)
            val copied = original.copy(isDefault = true)

            assertEquals("DBA", copied.name)
            assertTrue(copied.isDefault)
            assertTrue(copied.isAdmin)
        }

        @Test
        @DisplayName("Role with default values")
        fun testRoleDefaults() {
            val role = Role(name = "CONNECT")

            assertEquals("CONNECT", role.name)
            assertFalse(role.isDefault)
            assertFalse(role.isAdmin)
        }
    }

    // ==================== UserRole Model Tests ====================

    @Nested
    @DisplayName("UserRole Model Tests")
    inner class UserRoleModelTests {

        @Test
        @DisplayName("UserRole model should have correct properties")
        fun testUserRoleModel() {
            val userRole = UserRole(
                username = "TESTUSER",
                roleName = "DBA",
                isDefault = false,
                isAdmin = true
            )

            assertEquals("TESTUSER", userRole.username)
            assertEquals("DBA", userRole.roleName)
            assertFalse(userRole.isDefault)
            assertTrue(userRole.isAdmin)
        }

        @Test
        @DisplayName("UserRole equality should work correctly")
        fun testUserRoleEquality() {
            val role1 = UserRole("USER1", "DBA", false, true)
            val role2 = UserRole("USER1", "DBA", false, true)

            assertEquals(role1, role2)
            assertEquals(role1.hashCode(), role2.hashCode())
        }

        @Test
        @DisplayName("UserRole copy should work correctly")
        fun testUserRoleCopy() {
            val original = UserRole("USER1", "DBA", false, true)
            val copied = original.copy(roleName = "CONNECT", isAdmin = false)

            assertEquals("USER1", copied.username)
            assertEquals("CONNECT", copied.roleName)
            assertFalse(copied.isAdmin)
        }
    }

    // ==================== GrantRoleRequest Tests ====================

    @Nested
    @DisplayName("GrantRoleRequest Tests")
    inner class GrantRoleRequestTests {

        @Test
        @DisplayName("GrantRoleRequest should have correct properties")
        fun testGrantRoleRequestModel() {
            val request = GrantRoleRequest(
                username = "TESTUSER",
                host = "localhost",
                roles = listOf("CONNECT", "RESOURCE"),
                withAdminOption = true
            )

            assertEquals("TESTUSER", request.username)
            assertEquals("localhost", request.host)
            assertEquals(2, request.roles.size)
            assertTrue(request.withAdminOption)
        }

        @Test
        @DisplayName("GrantRoleRequest should have default values")
        fun testGrantRoleRequestDefaults() {
            val request = GrantRoleRequest(
                username = "TESTUSER",
                roles = listOf("CONNECT")
            )

            assertEquals("%", request.host)
            assertFalse(request.withAdminOption)
        }

        @Test
        @DisplayName("GrantRoleRequest equality")
        fun testGrantRoleRequestEquality() {
            val req1 = GrantRoleRequest("USER", "%", listOf("ROLE"), false)
            val req2 = GrantRoleRequest("USER", "%", listOf("ROLE"), false)

            assertEquals(req1, req2)
        }
    }

    // ==================== RevokeRoleRequest Tests ====================

    @Nested
    @DisplayName("RevokeRoleRequest Tests")
    inner class RevokeRoleRequestTests {

        @Test
        @DisplayName("RevokeRoleRequest should have correct properties")
        fun testRevokeRoleRequestModel() {
            val request = RevokeRoleRequest(
                username = "TESTUSER",
                host = "localhost",
                roles = listOf("DBA")
            )

            assertEquals("TESTUSER", request.username)
            assertEquals("localhost", request.host)
            assertEquals(1, request.roles.size)
            assertEquals("DBA", request.roles.first())
        }

        @Test
        @DisplayName("RevokeRoleRequest should have default host")
        fun testRevokeRoleRequestDefaults() {
            val request = RevokeRoleRequest(
                username = "TESTUSER",
                roles = listOf("CONNECT", "RESOURCE")
            )

            assertEquals("%", request.host)
            assertEquals(2, request.roles.size)
        }
    }

    // ==================== PdbInfo Model Tests ====================

    @Nested
    @DisplayName("PdbInfo Model Tests")
    inner class PdbInfoModelTests {

        @Test
        @DisplayName("PdbInfo model should have correct properties")
        fun testPdbInfoModel() {
            val pdb = PdbInfo(
                name = "XEPDB1",
                openMode = "READ WRITE",
                restricted = false
            )

            assertEquals("XEPDB1", pdb.name)
            assertEquals("READ WRITE", pdb.openMode)
            assertFalse(pdb.restricted)
        }

        @Test
        @DisplayName("PdbInfo equality should work correctly")
        fun testPdbInfoEquality() {
            val pdb1 = PdbInfo("XEPDB1", "READ WRITE", false)
            val pdb2 = PdbInfo("XEPDB1", "READ WRITE", false)

            assertEquals(pdb1, pdb2)
            assertEquals(pdb1.hashCode(), pdb2.hashCode())
        }

        @Test
        @DisplayName("PdbInfo with restricted mode")
        fun testPdbInfoRestricted() {
            val pdb = PdbInfo(
                name = "PDB_MAINTENANCE",
                openMode = "READ ONLY",
                restricted = true
            )

            assertEquals("PDB_MAINTENANCE", pdb.name)
            assertEquals("READ ONLY", pdb.openMode)
            assertTrue(pdb.restricted)
        }

        @Test
        @DisplayName("PdbInfo copy should work correctly")
        fun testPdbInfoCopy() {
            val original = PdbInfo("XEPDB1", "READ WRITE", false)
            val copied = original.copy(openMode = "MOUNTED", restricted = true)

            assertEquals("XEPDB1", copied.name)
            assertEquals("MOUNTED", copied.openMode)
            assertTrue(copied.restricted)
        }
    }

    // ==================== Service Method Tests with Mocking ====================

    @Nested
    @DisplayName("Service Method Tests with Mocking")
    inner class ServiceMethodTests {

        @Test
        @DisplayName("getAllRoles should return roles from Oracle database")
        fun testGetAllRoles() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getAllRolesQuery() } returns "SELECT * FROM DBA_ROLES"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.next() } returnsMany listOf(true, true, true, false)
            every { mockResultSet.getString("name") } returnsMany listOf("CONNECT", "RESOURCE", "DBA")
            every { mockResultSet.getBoolean("is_admin") } returnsMany listOf(false, false, true)
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val roles = roleService.getAllRoles("test-session")

            assertEquals(3, roles.size)
            assertEquals("CONNECT", roles[0].name)
            assertFalse(roles[0].isAdmin)
            assertEquals("DBA", roles[2].name)
            assertTrue(roles[2].isAdmin)
        }

        @Test
        @DisplayName("getAllRoles should throw exception for non-Oracle database")
        fun testGetAllRolesNonOracle() {
            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.MYSQL

            assertThrows<UnsupportedOperationException> {
                roleService.getAllRoles("test-session")
            }
        }

        @Test
        @DisplayName("getUserRoles should return user's assigned roles")
        fun testGetUserRoles() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false

            every { mockDialect.getUserRolesQuery() } returns "SELECT * FROM DBA_ROLE_PRIVS WHERE GRANTEE = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "TESTUSER") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet
            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getString("role_name") } returnsMany listOf("CONNECT", "RESOURCE")
            every { mockResultSet.getBoolean("is_default") } returnsMany listOf(true, false)
            every { mockResultSet.getBoolean("is_admin") } returnsMany listOf(false, false)
            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val roles = roleService.getUserRoles("test-session", "TESTUSER")

            assertEquals(2, roles.size)
            assertEquals("TESTUSER", roles[0].username)
            assertEquals("CONNECT", roles[0].roleName)
            assertTrue(roles[0].isDefault)
            assertEquals("RESOURCE", roles[1].roleName)
            assertFalse(roles[1].isDefault)
        }

        @Test
        @DisplayName("getUserRoles should handle CDB container root")
        fun testGetUserRolesCdb() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns true

            every { mockDialect.formatCdbUsername("TESTUSER", true) } returns "C##TESTUSER"
            every { mockDialect.getUserRolesQuery() } returns "SELECT * FROM DBA_ROLE_PRIVS WHERE GRANTEE = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "C##TESTUSER") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet
            every { mockResultSet.next() } returns false
            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val roles = roleService.getUserRoles("test-session", "TESTUSER")

            assertTrue(roles.isEmpty())
            verify { mockPreparedStatement.setString(1, "C##TESTUSER") }
        }

        @Test
        @DisplayName("grantRoles should execute GRANT SQL for each role")
        fun testGrantRoles() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false

            every { mockDialect.getGrantRoleSql("TESTUSER", "CONNECT", false) } returns "GRANT CONNECT TO TESTUSER"
            every { mockDialect.getGrantRoleSql("TESTUSER", "RESOURCE", false) } returns "GRANT RESOURCE TO TESTUSER"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val request = GrantRoleRequest(
                username = "TESTUSER",
                roles = listOf("CONNECT", "RESOURCE"),
                withAdminOption = false
            )

            roleService.grantRoles("test-session", request)

            verify { mockStatement.execute("GRANT CONNECT TO TESTUSER") }
            verify { mockStatement.execute("GRANT RESOURCE TO TESTUSER") }
        }

        @Test
        @DisplayName("grantRoles with admin option")
        fun testGrantRolesWithAdminOption() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false

            every { mockDialect.getGrantRoleSql("ADMIN_USER", "DBA", true) } returns "GRANT DBA TO ADMIN_USER WITH ADMIN OPTION"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val request = GrantRoleRequest(
                username = "ADMIN_USER",
                roles = listOf("DBA"),
                withAdminOption = true
            )

            roleService.grantRoles("test-session", request)

            verify { mockStatement.execute("GRANT DBA TO ADMIN_USER WITH ADMIN OPTION") }
        }

        @Test
        @DisplayName("revokeRoles should execute REVOKE SQL for each role")
        fun testRevokeRoles() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false

            every { mockDialect.getRevokeRoleSql("TESTUSER", "RESOURCE") } returns "REVOKE RESOURCE FROM TESTUSER"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val request = RevokeRoleRequest(
                username = "TESTUSER",
                roles = listOf("RESOURCE")
            )

            roleService.revokeRoles("test-session", request)

            verify { mockStatement.execute("REVOKE RESOURCE FROM TESTUSER") }
        }

        @Test
        @DisplayName("getCommonRoles should return predefined common roles")
        fun testGetCommonRoles() {
            val mockConnection = mockk<Connection>(relaxed = true)
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getCommonRoles() } returns listOf("CONNECT", "RESOURCE", "DBA")

            val roles = roleService.getCommonRoles("test-session")

            assertEquals(3, roles.size)
            assertTrue(roles.contains("CONNECT"))
            assertTrue(roles.contains("RESOURCE"))
            assertTrue(roles.contains("DBA"))
        }

        @Test
        @DisplayName("getPdbList should return PDB list from CDB")
        fun testGetPdbList() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getIsCdbQuery() } returns "SELECT CDB FROM V\$DATABASE"
            every { mockDialect.getPdbListQuery() } returns "SELECT * FROM V\$PDBS"

            val isCdbResultSet = mockk<ResultSet>()
            every { isCdbResultSet.next() } returns true
            every { isCdbResultSet.getString(1) } returns "YES"
            every { isCdbResultSet.close() } just Runs

            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery("SELECT CDB FROM V\$DATABASE") } returns isCdbResultSet
            every { mockStatement.executeQuery("SELECT * FROM V\$PDBS") } returns mockResultSet

            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getString("name") } returnsMany listOf("PDB\$SEED", "XEPDB1")
            every { mockResultSet.getString("open_mode") } returnsMany listOf("READ ONLY", "READ WRITE")
            every { mockResultSet.getBoolean("restricted") } returnsMany listOf(true, false)
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val pdbs = roleService.getPdbList("test-session")

            assertEquals(2, pdbs.size)
            assertEquals("PDB\$SEED", pdbs[0].name)
            assertEquals("READ ONLY", pdbs[0].openMode)
            assertTrue(pdbs[0].restricted)
            assertEquals("XEPDB1", pdbs[1].name)
            assertEquals("READ WRITE", pdbs[1].openMode)
            assertFalse(pdbs[1].restricted)
        }

        @Test
        @DisplayName("getPdbList should return empty list for non-CDB")
        fun testGetPdbListNonCdb() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getIsCdbQuery() } returns "SELECT CDB FROM V\$DATABASE"

            val isCdbResultSet = mockk<ResultSet>()
            every { isCdbResultSet.next() } returns true
            every { isCdbResultSet.getString(1) } returns "NO"
            every { isCdbResultSet.close() } just Runs

            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns isCdbResultSet
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val pdbs = roleService.getPdbList("test-session")

            assertTrue(pdbs.isEmpty())
        }

        @Test
        @DisplayName("getCurrentContainer should return container name")
        fun testGetCurrentContainer() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getCurrentContainerQuery() } returns "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') AS container_name FROM DUAL"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.next() } returns true
            every { mockResultSet.getString("container_name") } returns "CDB\$ROOT"
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val container = roleService.getCurrentContainer("test-session")

            assertEquals("CDB\$ROOT", container)
        }

        @Test
        @DisplayName("switchPdb should execute ALTER SESSION SQL")
        fun testSwitchPdb() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDatabaseType("test-session") } returns DatabaseType.ORACLE
            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getSwitchPdbSql("XEPDB1") } returns "ALTER SESSION SET CONTAINER = XEPDB1"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            roleService.switchPdb("test-session", "XEPDB1")

            verify { mockStatement.execute("ALTER SESSION SET CONTAINER = XEPDB1") }
        }
    }

    // ==================== Collection Tests ====================

    @Nested
    @DisplayName("Collection Tests")
    inner class CollectionTests {

        @Test
        @DisplayName("List of roles should be sortable by name")
        fun testRoleListSorting() {
            val roles = listOf(
                Role("DBA", false, true),
                Role("CONNECT", true, false),
                Role("RESOURCE", false, false)
            )

            val sorted = roles.sortedBy { it.name }

            assertEquals("CONNECT", sorted[0].name)
            assertEquals("DBA", sorted[1].name)
            assertEquals("RESOURCE", sorted[2].name)
        }

        @Test
        @DisplayName("List of UserRoles should preserve order")
        fun testUserRoleListOrder() {
            val userRoles = listOf(
                UserRole("USER1", "DBA", false, true),
                UserRole("USER1", "CONNECT", true, false),
                UserRole("USER1", "RESOURCE", false, false)
            )

            assertEquals("DBA", userRoles[0].roleName)
            assertEquals("CONNECT", userRoles[1].roleName)
            assertEquals("RESOURCE", userRoles[2].roleName)
        }

        @Test
        @DisplayName("List of UserRoles should be filterable by isAdmin")
        fun testUserRoleFiltering() {
            val userRoles = listOf(
                UserRole("USER1", "DBA", false, true),
                UserRole("USER1", "CONNECT", true, false),
                UserRole("USER1", "RESOURCE", false, false)
            )

            val adminRoles = userRoles.filter { it.isAdmin }

            assertEquals(1, adminRoles.size)
            assertEquals("DBA", adminRoles[0].roleName)
        }

        @Test
        @DisplayName("List of PdbInfo should be filterable by openMode")
        fun testPdbInfoFiltering() {
            val pdbs = listOf(
                PdbInfo("PDB\$SEED", "READ ONLY", true),
                PdbInfo("XEPDB1", "READ WRITE", false),
                PdbInfo("PDB_TEST", "MOUNTED", false)
            )

            val openPdbs = pdbs.filter { it.openMode == "READ WRITE" }

            assertEquals(1, openPdbs.size)
            assertEquals("XEPDB1", openPdbs[0].name)
        }
    }
}
