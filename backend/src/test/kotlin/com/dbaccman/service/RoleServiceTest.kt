package com.dbaccman.service

import com.dbaccman.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class RoleServiceTest {

    private lateinit var roleService: RoleService

    @BeforeEach
    fun setUp() {
        roleService = RoleService()
    }

    @Test
    @DisplayName("RoleService should be instantiable")
    fun testRoleServiceInstantiation() {
        assertNotNull(roleService)
    }

    // ==================== Role Model Tests ====================

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

    // ==================== Request Model Tests ====================

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

    // ==================== PDB Model Tests ====================

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

    // ==================== Collection Tests ====================

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
}
