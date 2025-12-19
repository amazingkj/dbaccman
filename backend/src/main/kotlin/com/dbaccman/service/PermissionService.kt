package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.model.GrantPermissionRequest
import com.dbaccman.model.Permission
import com.dbaccman.model.RevokePermissionRequest
import com.dbaccman.util.AuditLogger

class PermissionService {

    fun getUserPermissions(sessionId: String, username: String, host: String): List<Permission> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val permissions = mutableListOf<Permission>()
            val grantee = dialect.formatGrantee(username, host)

            // Schema-level privileges
            val schemaSql = dialect.getSchemaPrivilegesQuery()
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
            val tableSql = dialect.getTablePrivilegesQuery()
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

    fun grantPermission(sessionId: String, request: GrantPermissionRequest) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getGrantSql(
                privileges = request.privileges,
                database = request.database,
                table = request.table,
                username = request.username,
                host = request.host
            )

            // DCL statement - use createStatement
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            val target = if (request.table == "*") {
                "${request.database}.*"
            } else {
                "${request.database}.${request.table}"
            }
            val privileges = request.privileges.joinToString(", ")

            AuditLogger.log(
                "GRANT_PERMISSION",
                "Granted $privileges on $target to ${request.username}@${request.host}"
            )
        }
    }

    fun revokePermission(sessionId: String, request: RevokePermissionRequest) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getRevokeSql(
                privileges = request.privileges,
                database = request.database,
                table = request.table,
                username = request.username,
                host = request.host
            )

            // DCL statement - use createStatement
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            val target = if (request.table == "*") {
                "${request.database}.*"
            } else {
                "${request.database}.${request.table}"
            }
            val privileges = request.privileges.joinToString(", ")

            AuditLogger.log(
                "REVOKE_PERMISSION",
                "Revoked $privileges on $target from ${request.username}@${request.host}"
            )
        }
    }

    fun getDatabases(sessionId: String): List<String> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getShowDatabasesQuery()
            val systemSchemas = dialect.getSystemSchemas()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val databases = mutableListOf<String>()
                    while (rs.next()) {
                        val dbName = rs.getString(1)
                        if (dbName !in systemSchemas) {
                            databases.add(dbName)
                        }
                    }
                    databases
                }
            }
        }
    }
}