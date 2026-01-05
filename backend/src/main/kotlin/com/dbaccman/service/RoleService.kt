package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.dialect.DatabaseType
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.model.*
import com.dbaccman.util.AuditLogger

class RoleService {

    fun getAllRoles(sessionId: String): List<Role> {
        val dbType = SessionConnectionManager.getDatabaseType(sessionId)
        if (dbType != DatabaseType.ORACLE) {
            throw UnsupportedOperationException("Role management is only supported for Oracle databases")
        }

        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val oracleDialect = dialect as OracleDialect
            val sql = oracleDialect.getAllRolesQuery()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val roles = mutableListOf<Role>()
                    while (rs.next()) {
                        roles.add(
                            Role(
                                name = rs.getString("name"),
                                isAdmin = rs.getBoolean("is_admin")
                            )
                        )
                    }
                    roles
                }
            }
        }
    }

    fun getUserRoles(sessionId: String, username: String): List<UserRole> {
        val dbType = SessionConnectionManager.getDatabaseType(sessionId)
        if (dbType != DatabaseType.ORACLE) {
            throw UnsupportedOperationException("Role management is only supported for Oracle databases")
        }

        val isContainerRoot = SessionConnectionManager.isContainerRoot(sessionId)

        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val oracleDialect = dialect as OracleDialect

            // For Oracle CDB root, add C## prefix
            val actualUsername = if (isContainerRoot) {
                oracleDialect.formatCdbUsername(username, true)
            } else {
                username
            }

            val sql = oracleDialect.getUserRolesQuery()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, actualUsername)
                stmt.executeQuery().use { rs ->
                    val roles = mutableListOf<UserRole>()
                    while (rs.next()) {
                        roles.add(
                            UserRole(
                                username = username,  // Return without C## for display
                                roleName = rs.getString("role_name"),
                                isDefault = rs.getBoolean("is_default"),
                                isAdmin = rs.getBoolean("is_admin")
                            )
                        )
                    }
                    roles
                }
            }
        }
    }

    fun grantRoles(sessionId: String, request: GrantRoleRequest) {
        val dbType = SessionConnectionManager.getDatabaseType(sessionId)
        if (dbType != DatabaseType.ORACLE) {
            throw UnsupportedOperationException("Role management is only supported for Oracle databases")
        }

        val isContainerRoot = SessionConnectionManager.isContainerRoot(sessionId)

        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val oracleDialect = dialect as OracleDialect

            // For Oracle CDB root, add C## prefix
            val actualUsername = if (isContainerRoot) {
                oracleDialect.formatCdbUsername(request.username, true)
            } else {
                request.username
            }

            request.roles.forEach { role ->
                val sql = oracleDialect.getGrantRoleSql(actualUsername, role, request.withAdminOption)
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }
            }

            AuditLogger.log(
                "GRANT_ROLES",
                "Granted roles ${request.roles.joinToString()} to ${request.username}@${request.host}" +
                if (request.withAdminOption) " WITH ADMIN OPTION" else ""
            )
        }
    }

    fun revokeRoles(sessionId: String, request: RevokeRoleRequest) {
        val dbType = SessionConnectionManager.getDatabaseType(sessionId)
        if (dbType != DatabaseType.ORACLE) {
            throw UnsupportedOperationException("Role management is only supported for Oracle databases")
        }

        val isContainerRoot = SessionConnectionManager.isContainerRoot(sessionId)

        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val oracleDialect = dialect as OracleDialect

            // For Oracle CDB root, add C## prefix
            val actualUsername = if (isContainerRoot) {
                oracleDialect.formatCdbUsername(request.username, true)
            } else {
                request.username
            }

            request.roles.forEach { role ->
                val sql = oracleDialect.getRevokeRoleSql(actualUsername, role)
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }
            }

            AuditLogger.log(
                "REVOKE_ROLES",
                "Revoked roles ${request.roles.joinToString()} from ${request.username}@${request.host}"
            )
        }
    }

    fun getCommonRoles(sessionId: String): List<String> {
        val dbType = SessionConnectionManager.getDatabaseType(sessionId)
        if (dbType != DatabaseType.ORACLE) {
            throw UnsupportedOperationException("Role management is only supported for Oracle databases")
        }

        return useSessionConnectionWithDialect(sessionId) { _, dialect ->
            val oracleDialect = dialect as OracleDialect
            oracleDialect.getCommonRoles()
        }
    }

    // ==================== PDB Management ====================

    fun getPdbList(sessionId: String): List<PdbInfo> {
        val dbType = SessionConnectionManager.getDatabaseType(sessionId)
        if (dbType != DatabaseType.ORACLE) {
            throw UnsupportedOperationException("PDB management is only supported for Oracle databases")
        }

        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val oracleDialect = dialect as OracleDialect

            // Check if this is a CDB first
            try {
                val isCdb = conn.createStatement().use { stmt ->
                    stmt.executeQuery(oracleDialect.getIsCdbQuery()).use { rs ->
                        rs.next() && rs.getString(1) == "YES"
                    }
                }

                if (!isCdb) {
                    return@useSessionConnectionWithDialect emptyList()
                }

                val sql = oracleDialect.getPdbListQuery()
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        val pdbs = mutableListOf<PdbInfo>()
                        while (rs.next()) {
                            pdbs.add(
                                PdbInfo(
                                    name = rs.getString("name"),
                                    openMode = rs.getString("open_mode"),
                                    restricted = rs.getBoolean("restricted")
                                )
                            )
                        }
                        pdbs
                    }
                }
            } catch (e: Exception) {
                // Not a CDB or no permission
                emptyList()
            }
        }
    }

    fun getCurrentContainer(sessionId: String): String {
        val dbType = SessionConnectionManager.getDatabaseType(sessionId)
        if (dbType != DatabaseType.ORACLE) {
            throw UnsupportedOperationException("PDB management is only supported for Oracle databases")
        }

        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val oracleDialect = dialect as OracleDialect
            val sql = oracleDialect.getCurrentContainerQuery()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    if (rs.next()) rs.getString("container_name") else ""
                }
            }
        }
    }

    fun switchPdb(sessionId: String, pdbName: String) {
        val dbType = SessionConnectionManager.getDatabaseType(sessionId)
        if (dbType != DatabaseType.ORACLE) {
            throw UnsupportedOperationException("PDB management is only supported for Oracle databases")
        }

        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val oracleDialect = dialect as OracleDialect
            val sql = oracleDialect.getSwitchPdbSql(pdbName)

            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            AuditLogger.log("SWITCH_PDB", "Switched to PDB: $pdbName")
        }
    }
}
