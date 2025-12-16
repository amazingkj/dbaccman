package com.dbgate.service

import com.dbgate.config.useConnection
import com.dbgate.model.GrantPermissionRequest
import com.dbgate.model.Permission
import com.dbgate.model.RevokePermissionRequest
import com.dbgate.util.AuditLogger

class PermissionService {

    fun getUserPermissions(username: String, host: String): List<Permission> {
        return useConnection { conn ->
            val permissions = mutableListOf<Permission>()
            val grantee = "'$username'@'$host'"

            // Schema-level privileges
            val schemaSql = """
                SELECT
                    GRANTEE as grantee,
                    TABLE_SCHEMA as db,
                    PRIVILEGE_TYPE as privilege,
                    IS_GRANTABLE as is_grantable
                FROM information_schema.SCHEMA_PRIVILEGES
                WHERE GRANTEE = ?
            """.trimIndent()

            conn.prepareStatement(schemaSql).use { stmt ->
                stmt.setString(1, grantee)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        permissions.add(
                            Permission(
                                grantee = rs.getString("grantee"),
                                database = rs.getString("db"),
                                table = "*",
                                privilege = rs.getString("privilege"),
                                isGrantable = rs.getString("is_grantable") == "YES"
                            )
                        )
                    }
                }
            }

            // Table-level privileges
            val tableSql = """
                SELECT
                    GRANTEE as grantee,
                    TABLE_SCHEMA as db,
                    TABLE_NAME as tbl,
                    PRIVILEGE_TYPE as privilege,
                    IS_GRANTABLE as is_grantable
                FROM information_schema.TABLE_PRIVILEGES
                WHERE GRANTEE = ?
            """.trimIndent()

            conn.prepareStatement(tableSql).use { stmt ->
                stmt.setString(1, grantee)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        permissions.add(
                            Permission(
                                grantee = rs.getString("grantee"),
                                database = rs.getString("db"),
                                table = rs.getString("tbl"),
                                privilege = rs.getString("privilege"),
                                isGrantable = rs.getString("is_grantable") == "YES"
                            )
                        )
                    }
                }
            }

            permissions
        }
    }

    fun grantPermission(request: GrantPermissionRequest) {
        useConnection { conn ->
            val privileges = request.privileges.joinToString(", ")
            val target = if (request.table == "*") {
                "${quoteIdentifier(request.database)}.*"
            } else {
                "${quoteIdentifier(request.database)}.${quoteIdentifier(request.table)}"
            }

            val sql = "GRANT $privileges ON $target TO ?@?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, request.username)
                stmt.setString(2, request.host)
                stmt.execute()
            }

            conn.createStatement().execute("FLUSH PRIVILEGES")

            AuditLogger.log(
                "GRANT_PERMISSION",
                "Granted $privileges on $target to ${request.username}@${request.host}"
            )
        }
    }

    fun revokePermission(request: RevokePermissionRequest) {
        useConnection { conn ->
            val privileges = request.privileges.joinToString(", ")
            val target = if (request.table == "*") {
                "${quoteIdentifier(request.database)}.*"
            } else {
                "${quoteIdentifier(request.database)}.${quoteIdentifier(request.table)}"
            }

            val sql = "REVOKE $privileges ON $target FROM ?@?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, request.username)
                stmt.setString(2, request.host)
                stmt.execute()
            }

            conn.createStatement().execute("FLUSH PRIVILEGES")

            AuditLogger.log(
                "REVOKE_PERMISSION",
                "Revoked $privileges on $target from ${request.username}@${request.host}"
            )
        }
    }

    fun getDatabases(): List<String> {
        return useConnection { conn ->
            val sql = "SHOW DATABASES"
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val databases = mutableListOf<String>()
                    while (rs.next()) {
                        val dbName = rs.getString(1)
                        // Filter out system databases
                        if (dbName !in listOf("information_schema", "performance_schema", "mysql", "sys")) {
                            databases.add(dbName)
                        }
                    }
                    databases
                }
            }
        }
    }

    private fun quoteIdentifier(identifier: String): String {
        return "`${identifier.replace("`", "``")}`"
    }
}
