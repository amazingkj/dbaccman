package com.dbaccman.service

import com.dbaccman.model.GrantPermissionRequest
import com.dbaccman.model.MySQLPrivileges
import com.dbaccman.model.Permission
import com.dbaccman.model.RevokePermissionRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class PermissionServiceTest {

    private lateinit var permissionService: PermissionService

    @BeforeEach
    fun setUp() {
        permissionService = PermissionService()
    }

    @Test
    @DisplayName("PermissionService should be instantiable")
    fun testPermissionServiceInstantiation() {
        assertNotNull(permissionService)
    }

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
}
