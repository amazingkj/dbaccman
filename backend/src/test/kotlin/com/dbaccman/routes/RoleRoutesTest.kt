package com.dbaccman.routes

import com.dbaccman.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class RoleRoutesTest {

    // ==================== Role Model Tests ====================

    @Nested
    @DisplayName("Role Model Tests")
    inner class RoleTests {

        @Test
        @DisplayName("Role should have name property")
        fun testRoleNameProperty() {
            val role = Role(name = "CONNECT")
            assertEquals("CONNECT", role.name)
        }

        @Test
        @DisplayName("Role should have default isDefault as false")
        fun testRoleDefaultIsDefault() {
            val role = Role(name = "RESOURCE")
            assertFalse(role.isDefault)
        }

        @Test
        @DisplayName("Role should have default isAdmin as false")
        fun testRoleDefaultIsAdmin() {
            val role = Role(name = "DBA")
            assertFalse(role.isAdmin)
        }

        @Test
        @DisplayName("Role with all properties set")
        fun testRoleAllProperties() {
            val role = Role(
                name = "DBA",
                isDefault = true,
                isAdmin = true
            )

            assertEquals("DBA", role.name)
            assertTrue(role.isDefault)
            assertTrue(role.isAdmin)
        }

        @Test
        @DisplayName("Role should support Oracle common role names")
        fun testOracleCommonRoles() {
            val roles = listOf(
                Role("CONNECT"),
                Role("RESOURCE"),
                Role("DBA"),
                Role("EXP_FULL_DATABASE"),
                Role("IMP_FULL_DATABASE"),
                Role("SELECT_CATALOG_ROLE")
            )

            assertEquals(6, roles.size)
            assertTrue(roles.any { it.name == "DBA" })
        }
    }

    // ==================== UserRole Model Tests ====================

    @Nested
    @DisplayName("UserRole Model Tests")
    inner class UserRoleTests {

        @Test
        @DisplayName("UserRole should have username and roleName")
        fun testUserRoleBasicProperties() {
            val userRole = UserRole(
                username = "app_user",
                roleName = "CONNECT"
            )

            assertEquals("app_user", userRole.username)
            assertEquals("CONNECT", userRole.roleName)
        }

        @Test
        @DisplayName("UserRole should have default isDefault as false")
        fun testUserRoleDefaultIsDefault() {
            val userRole = UserRole("user", "ROLE1")
            assertFalse(userRole.isDefault)
        }

        @Test
        @DisplayName("UserRole should have default isAdmin as false")
        fun testUserRoleDefaultIsAdmin() {
            val userRole = UserRole("user", "ROLE1")
            assertFalse(userRole.isAdmin)
        }

        @Test
        @DisplayName("UserRole with admin option")
        fun testUserRoleWithAdminOption() {
            val userRole = UserRole(
                username = "dba_user",
                roleName = "DBA",
                isDefault = false,
                isAdmin = true
            )

            assertTrue(userRole.isAdmin)
            assertFalse(userRole.isDefault)
        }

        @Test
        @DisplayName("UserRole with default role")
        fun testUserRoleWithDefaultRole() {
            val userRole = UserRole(
                username = "regular_user",
                roleName = "CONNECT",
                isDefault = true,
                isAdmin = false
            )

            assertTrue(userRole.isDefault)
            assertFalse(userRole.isAdmin)
        }
    }

    // ==================== GrantRoleRequest Model Tests ====================

    @Nested
    @DisplayName("GrantRoleRequest Model Tests")
    inner class GrantRoleRequestTests {

        @Test
        @DisplayName("GrantRoleRequest should have username and roles")
        fun testGrantRoleRequestBasicProperties() {
            val request = GrantRoleRequest(
                username = "new_user",
                roles = listOf("CONNECT", "RESOURCE")
            )

            assertEquals("new_user", request.username)
            assertEquals(2, request.roles.size)
        }

        @Test
        @DisplayName("GrantRoleRequest should have default host as %")
        fun testGrantRoleRequestDefaultHost() {
            val request = GrantRoleRequest(
                username = "user",
                roles = listOf("CONNECT")
            )

            assertEquals("%", request.host)
        }

        @Test
        @DisplayName("GrantRoleRequest should have default withAdminOption as false")
        fun testGrantRoleRequestDefaultAdminOption() {
            val request = GrantRoleRequest(
                username = "user",
                roles = listOf("CONNECT")
            )

            assertFalse(request.withAdminOption)
        }

        @Test
        @DisplayName("GrantRoleRequest with custom host")
        fun testGrantRoleRequestWithCustomHost() {
            val request = GrantRoleRequest(
                username = "local_user",
                host = "localhost",
                roles = listOf("DBA")
            )

            assertEquals("localhost", request.host)
        }

        @Test
        @DisplayName("GrantRoleRequest with admin option")
        fun testGrantRoleRequestWithAdminOption() {
            val request = GrantRoleRequest(
                username = "admin_user",
                host = "%",
                roles = listOf("DBA"),
                withAdminOption = true
            )

            assertTrue(request.withAdminOption)
        }

        @Test
        @DisplayName("GrantRoleRequest with multiple roles")
        fun testGrantRoleRequestMultipleRoles() {
            val request = GrantRoleRequest(
                username = "developer",
                roles = listOf(
                    "CONNECT",
                    "RESOURCE",
                    "SELECT_CATALOG_ROLE",
                    "EXECUTE_CATALOG_ROLE"
                )
            )

            assertEquals(4, request.roles.size)
            assertTrue(request.roles.contains("CONNECT"))
            assertTrue(request.roles.contains("RESOURCE"))
        }

        @Test
        @DisplayName("GrantRoleRequest with single role")
        fun testGrantRoleRequestSingleRole() {
            val request = GrantRoleRequest(
                username = "readonly_user",
                roles = listOf("SELECT_CATALOG_ROLE")
            )

            assertEquals(1, request.roles.size)
        }
    }

    // ==================== RevokeRoleRequest Model Tests ====================

    @Nested
    @DisplayName("RevokeRoleRequest Model Tests")
    inner class RevokeRoleRequestTests {

        @Test
        @DisplayName("RevokeRoleRequest should have username and roles")
        fun testRevokeRoleRequestBasicProperties() {
            val request = RevokeRoleRequest(
                username = "old_dba",
                roles = listOf("DBA")
            )

            assertEquals("old_dba", request.username)
            assertEquals(1, request.roles.size)
        }

        @Test
        @DisplayName("RevokeRoleRequest should have default host as %")
        fun testRevokeRoleRequestDefaultHost() {
            val request = RevokeRoleRequest(
                username = "user",
                roles = listOf("ROLE1")
            )

            assertEquals("%", request.host)
        }

        @Test
        @DisplayName("RevokeRoleRequest with custom host")
        fun testRevokeRoleRequestWithCustomHost() {
            val request = RevokeRoleRequest(
                username = "local_user",
                host = "192.168.1.%",
                roles = listOf("CONNECT")
            )

            assertEquals("192.168.1.%", request.host)
        }

        @Test
        @DisplayName("RevokeRoleRequest with multiple roles")
        fun testRevokeRoleRequestMultipleRoles() {
            val request = RevokeRoleRequest(
                username = "departing_user",
                roles = listOf("DBA", "RESOURCE", "CONNECT")
            )

            assertEquals(3, request.roles.size)
        }
    }

    // ==================== PdbInfo Model Tests ====================

    @Nested
    @DisplayName("PdbInfo Model Tests")
    inner class PdbInfoTests {

        @Test
        @DisplayName("PdbInfo should have name and openMode")
        fun testPdbInfoBasicProperties() {
            val pdb = PdbInfo(
                name = "ORCLPDB1",
                openMode = "READ WRITE"
            )

            assertEquals("ORCLPDB1", pdb.name)
            assertEquals("READ WRITE", pdb.openMode)
        }

        @Test
        @DisplayName("PdbInfo should have default restricted as false")
        fun testPdbInfoDefaultRestricted() {
            val pdb = PdbInfo(name = "PDB1", openMode = "MOUNTED")
            assertFalse(pdb.restricted)
        }

        @Test
        @DisplayName("PdbInfo in restricted mode")
        fun testPdbInfoRestricted() {
            val pdb = PdbInfo(
                name = "MAINT_PDB",
                openMode = "READ WRITE",
                restricted = true
            )

            assertTrue(pdb.restricted)
        }

        @Test
        @DisplayName("PdbInfo with various open modes")
        fun testPdbInfoOpenModes() {
            val modes = listOf(
                PdbInfo("PDB1", "READ WRITE"),
                PdbInfo("PDB2", "READ ONLY"),
                PdbInfo("PDB3", "MOUNTED"),
                PdbInfo("PDB4", "READ WRITE WITH RESTRICTED SESSION")
            )

            assertEquals("READ WRITE", modes[0].openMode)
            assertEquals("READ ONLY", modes[1].openMode)
            assertEquals("MOUNTED", modes[2].openMode)
        }

        @Test
        @DisplayName("PdbInfo for CDB ROOT container")
        fun testPdbInfoCdbRoot() {
            val cdb = PdbInfo(
                name = "CDB\$ROOT",
                openMode = "READ WRITE"
            )

            assertEquals("CDB\$ROOT", cdb.name)
        }

        @Test
        @DisplayName("PdbInfo for PDB SEED template")
        fun testPdbInfoPdbSeed() {
            val seed = PdbInfo(
                name = "PDB\$SEED",
                openMode = "READ ONLY",
                restricted = false
            )

            assertEquals("PDB\$SEED", seed.name)
            assertEquals("READ ONLY", seed.openMode)
        }
    }

    // ==================== Role Management Scenarios ====================

    @Nested
    @DisplayName("Role Management Scenarios")
    inner class RoleManagementScenarios {

        @Test
        @DisplayName("Typical new user role assignment")
        fun testNewUserRoleAssignment() {
            // When creating a new application user, typically grant CONNECT and RESOURCE
            val request = GrantRoleRequest(
                username = "app_user_001",
                host = "%",
                roles = listOf("CONNECT", "RESOURCE"),
                withAdminOption = false
            )

            assertTrue(request.roles.contains("CONNECT"))
            assertTrue(request.roles.contains("RESOURCE"))
            assertFalse(request.withAdminOption)
        }

        @Test
        @DisplayName("DBA user role assignment")
        fun testDbaUserRoleAssignment() {
            // When creating a DBA user, grant DBA role with admin option
            val request = GrantRoleRequest(
                username = "senior_dba",
                host = "localhost",
                roles = listOf("DBA"),
                withAdminOption = true
            )

            assertEquals("localhost", request.host)
            assertTrue(request.roles.contains("DBA"))
            assertTrue(request.withAdminOption)
        }

        @Test
        @DisplayName("Read-only user role assignment")
        fun testReadOnlyUserRoleAssignment() {
            val request = GrantRoleRequest(
                username = "report_user",
                roles = listOf("CONNECT", "SELECT_CATALOG_ROLE"),
                withAdminOption = false
            )

            assertTrue(request.roles.contains("SELECT_CATALOG_ROLE"))
            assertFalse(request.withAdminOption)
        }

        @Test
        @DisplayName("User role revocation on departure")
        fun testUserDepartureRoleRevocation() {
            // When user leaves, revoke all roles
            val request = RevokeRoleRequest(
                username = "former_employee",
                roles = listOf("CONNECT", "RESOURCE", "DBA", "EXP_FULL_DATABASE")
            )

            assertEquals(4, request.roles.size)
        }

        @Test
        @DisplayName("Privilege escalation - promoting user to DBA")
        fun testPrivilegeEscalation() {
            val grantRequest = GrantRoleRequest(
                username = "promoted_user",
                roles = listOf("DBA"),
                withAdminOption = false
            )

            assertTrue(grantRequest.roles.contains("DBA"))
        }

        @Test
        @DisplayName("Privilege reduction - demoting DBA to regular user")
        fun testPrivilegeReduction() {
            val revokeRequest = RevokeRoleRequest(
                username = "demoted_dba",
                roles = listOf("DBA")
            )

            assertEquals(1, revokeRequest.roles.size)
            assertEquals("DBA", revokeRequest.roles[0])
        }
    }

    // ==================== Multiple PDB Scenario Tests ====================

    @Nested
    @DisplayName("Multiple PDB Scenarios")
    inner class MultiplePdbScenarios {

        @Test
        @DisplayName("List of all PDBs in a CDB")
        fun testAllPdbsInCdb() {
            val pdbs = listOf(
                PdbInfo("CDB\$ROOT", "READ WRITE"),
                PdbInfo("PDB\$SEED", "READ ONLY"),
                PdbInfo("PROD_PDB", "READ WRITE"),
                PdbInfo("DEV_PDB", "READ WRITE"),
                PdbInfo("TEST_PDB", "READ ONLY")
            )

            assertEquals(5, pdbs.size)
            assertEquals(1, pdbs.count { it.name == "CDB\$ROOT" })
            assertEquals(1, pdbs.count { it.name == "PDB\$SEED" })
            assertEquals(3, pdbs.count { it.openMode == "READ WRITE" })
        }

        @Test
        @DisplayName("PDBs with mixed states")
        fun testPdbsMixedStates() {
            val pdbs = listOf(
                PdbInfo("ACTIVE_PDB", "READ WRITE", false),
                PdbInfo("MAINT_PDB", "READ WRITE", true),  // In restricted mode
                PdbInfo("STANDBY_PDB", "READ ONLY", false),
                PdbInfo("OFFLINE_PDB", "MOUNTED", false)
            )

            assertEquals(1, pdbs.count { it.restricted })
            assertEquals(1, pdbs.count { it.openMode == "MOUNTED" })
        }
    }
}
