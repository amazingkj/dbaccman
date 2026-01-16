package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.PostgreSQLDialect
import com.dbaccman.model.GrantPermissionRequest
import com.dbaccman.model.Permission
import com.dbaccman.model.RevokePermissionRequest
import com.dbaccman.util.AuditLogger
import com.dbaccman.util.InputValidator

class PermissionService {

    fun getUserPermissions(sessionId: String, username: String, host: String): List<Permission> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val permissions = mutableListOf<Permission>()
            val grantee = dialect.formatGrantee(username, host)

            // Global privileges (MySQL only - stored in mysql.user table)
            if (dialect is MySQLDialect) {
                val globalSql = dialect.getGlobalPrivilegesQuery()
                conn.prepareStatement(globalSql).use { stmt ->
                    stmt.setString(1, grantee)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            permissions.add(
                                Permission(
                                    grantee = rs.getString("grantee"),
                                    database = "*",
                                    table = "*",
                                    privilege = rs.getString("privilege"),
                                    isGrantable = rs.getString("is_grantable") == "YES"
                                )
                            )
                        }
                    }
                }
            }

            // Schema-level privileges
            val schemaSql = dialect.getSchemaPrivilegesQuery()
            conn.prepareStatement(schemaSql).use { stmt ->
                stmt.setString(1, grantee)
                // PostgreSQL uses has_schema_privilege() which needs the username as second parameter
                if (dialect is PostgreSQLDialect) {
                    stmt.setString(2, grantee)
                }
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

    /**
     * Get all permissions for all users in a single batch query.
     * Returns a map of grantee (username@host) to their permissions.
     * This is much more efficient than calling getUserPermissions() N times.
     */
    fun getAllUsersPermissions(sessionId: String): Map<String, List<Permission>> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val permissionsByUser = mutableMapOf<String, MutableList<Permission>>()

            // Global privileges (MySQL only - stored in mysql.user table)
            if (dialect is MySQLDialect) {
                val globalSql = dialect.getAllGlobalPrivilegesQuery()
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(globalSql).use { rs ->
                        while (rs.next()) {
                            val grantee = rs.getString("grantee")
                            val permission = Permission(
                                grantee = grantee,
                                database = "*",
                                table = "*",
                                privilege = rs.getString("privilege"),
                                isGrantable = rs.getString("is_grantable") == "YES"
                            )
                            permissionsByUser.getOrPut(grantee) { mutableListOf() }.add(permission)
                        }
                    }
                }
            }

            // Schema-level privileges (all users)
            val schemaSql = dialect.getAllSchemaPrivilegesQuery()
            conn.createStatement().use { stmt ->
                stmt.executeQuery(schemaSql).use { rs ->
                    while (rs.next()) {
                        val grantee = rs.getString("grantee")
                        val permission = Permission(
                            grantee = grantee,
                            database = rs.getString("db"),
                            table = "*",
                            privilege = rs.getString("privilege"),
                            isGrantable = rs.getString("is_grantable") == "YES"
                        )
                        permissionsByUser.getOrPut(grantee) { mutableListOf() }.add(permission)
                    }
                }
            }

            // Table-level privileges (all users)
            val tableSql = dialect.getAllTablePrivilegesQuery()
            conn.createStatement().use { stmt ->
                stmt.executeQuery(tableSql).use { rs ->
                    while (rs.next()) {
                        val grantee = rs.getString("grantee")
                        val permission = Permission(
                            grantee = grantee,
                            database = rs.getString("db"),
                            table = rs.getString("tbl"),
                            privilege = rs.getString("privilege"),
                            isGrantable = rs.getString("is_grantable") == "YES"
                        )
                        permissionsByUser.getOrPut(grantee) { mutableListOf() }.add(permission)
                    }
                }
            }

            permissionsByUser
        }
    }

    fun grantPermission(sessionId: String, request: GrantPermissionRequest) {
        // Validate inputs
        InputValidator.validateIdentifier(request.username, "username")
        InputValidator.validateHost(request.host)
        InputValidator.validatePrivileges(request.privileges)
        InputValidator.validateDatabaseName(request.database)
        InputValidator.validateTableName(request.table)

        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getGrantSql(
                privileges = request.privileges,
                database = request.database,
                table = request.table,
                username = request.username,
                host = request.host
            )

            // DCL statement - use createStatement
            // Handle multiple statements separated by semicolons
            conn.createStatement().use { stmt ->
                sql.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { singleSql ->
                    stmt.execute(singleSql)
                }
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            // Build target string - handle system privileges (empty database)
            val target = if (request.database.isEmpty()) {
                "SYSTEM PRIVILEGES"
            } else if (request.table == "*") {
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

    /**
     * Batch grant permissions - executes all grants in a single connection session.
     * More efficient than calling grantPermission() multiple times.
     * Returns a pair of (successCount, errors).
     */
    fun batchGrantPermissions(sessionId: String, requests: List<GrantPermissionRequest>): Pair<Int, List<String>> {
        if (requests.isEmpty()) return Pair(0, emptyList())

        // Validate all requests before executing
        requests.forEach { request ->
            InputValidator.validateIdentifier(request.username, "username")
            InputValidator.validateHost(request.host)
            InputValidator.validatePrivileges(request.privileges)
            InputValidator.validateDatabaseName(request.database)
            InputValidator.validateTableName(request.table)
        }

        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            var successCount = 0
            val errors = mutableListOf<String>()

            conn.createStatement().use { stmt ->
                requests.forEach { request ->
                    try {
                        val sql = dialect.getGrantSql(
                            privileges = request.privileges,
                            database = request.database,
                            table = request.table,
                            username = request.username,
                            host = request.host
                        )
                        stmt.execute(sql)
                        successCount++
                    } catch (e: Exception) {
                        val target = if (request.table == "*") {
                            "${request.database}.*"
                        } else {
                            "${request.database}.${request.table}"
                        }
                        errors.add("$target: ${e.message}")
                    }
                }
            }

            // Flush privileges once at the end (for MySQL)
            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            if (successCount > 0) {
                AuditLogger.log(
                    "BATCH_GRANT_PERMISSION",
                    "Granted $successCount permissions to ${requests.first().username}@${requests.first().host}"
                )
            }

            Pair(successCount, errors)
        }
    }

    fun revokePermission(sessionId: String, request: RevokePermissionRequest) {
        // Validate inputs
        InputValidator.validateIdentifier(request.username, "username")
        InputValidator.validateHost(request.host)
        InputValidator.validatePrivileges(request.privileges)
        InputValidator.validateDatabaseName(request.database)
        InputValidator.validateTableName(request.table)

        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getRevokeSql(
                privileges = request.privileges,
                database = request.database,
                table = request.table,
                username = request.username,
                host = request.host
            )

            // DCL statement - use createStatement
            // Handle multiple statements separated by semicolons
            conn.createStatement().use { stmt ->
                sql.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { singleSql ->
                    stmt.execute(singleSql)
                }
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            // Build target string - handle system privileges (empty database)
            val target = if (request.database.isEmpty()) {
                "SYSTEM PRIVILEGES"
            } else if (request.table == "*") {
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