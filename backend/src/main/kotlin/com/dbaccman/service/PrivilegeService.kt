package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.dialect.DatabaseType
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.model.PasswordExpiryInfo

class PrivilegeService {

    /**
     * Checks if the current session user has admin privileges.
     * - MySQL: CREATE USER or ALL PRIVILEGES
     * - Oracle: DBA role
     * - PostgreSQL: superuser
     */
    fun isAdmin(sessionId: String): Boolean {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery(dialect.getAdminCheckQuery()).use { rs ->
                    when (dialect.type) {
                        // Oracle: If DBA role exists in result, user is admin
                        DatabaseType.ORACLE -> {
                            if (rs.next()) {
                                val role = rs.getString(1)?.uppercase() ?: ""
                                role == "DBA"
                            } else {
                                false
                            }
                        }
                        // PostgreSQL: If any row returned (superuser check), user is admin
                        DatabaseType.POSTGRESQL -> {
                            rs.next()
                        }
                        // MySQL: Check for admin-level privileges
                        DatabaseType.MYSQL -> {
                            while (rs.next()) {
                                val grant = rs.getString(1).uppercase()
                                if (grant.contains("ALL PRIVILEGES ON *.*") ||
                                    grant.contains("CREATE USER") ||
                                    (grant.contains("ALL PRIVILEGES") && grant.contains("WITH GRANT OPTION"))) {
                                    return@useSessionConnectionWithDialect true
                                }
                            }
                            false
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns "admin" or "user" based on privileges.
     */
    fun detectRole(sessionId: String): String {
        return if (isAdmin(sessionId)) "admin" else "user"
    }

    /**
     * Gets password expiry information for the current user.
     * Tries admin query first, falls back to user-level query for regular users.
     */
    fun getMyPasswordExpiry(sessionId: String, username: String, host: String): PasswordExpiryInfo {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            // First try admin query
            try {
                val sql = dialect.getPasswordExpiryQuery()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, username)
                    stmt.setString(2, host)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return@useSessionConnectionWithDialect PasswordExpiryInfo(
                                username = rs.getString("user"),
                                host = rs.getString("host"),
                                daysUntilExpiry = rs.getObject("days_until_expiry")?.let {
                                    (it as Number).toInt()
                                },
                                passwordLastChanged = rs.getString("password_last_changed"),
                                isExpired = rs.getBoolean("is_expired")
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Admin query failed, try user-level query
            }

            // Try user-level query for Oracle (USER_USERS instead of DBA_USERS)
            if (dialect is OracleDialect) {
                try {
                    val sql = dialect.getMyPasswordExpiryQuery()
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, host)
                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                return@useSessionConnectionWithDialect PasswordExpiryInfo(
                                    username = rs.getString("user"),
                                    host = rs.getString("host"),
                                    daysUntilExpiry = rs.getObject("days_until_expiry")?.let {
                                        (it as Number).toInt()
                                    },
                                    passwordLastChanged = rs.getString("password_last_changed"),
                                    isExpired = rs.getBoolean("is_expired")
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // User query also failed
                }
            }

            // Try user-level query for MySQL (using CURRENT_USER)
            if (dialect is MySQLDialect) {
                try {
                    val sql = dialect.getMyPasswordExpiryQuery()
                    conn.createStatement().use { stmt ->
                        stmt.executeQuery(sql).use { rs ->
                            if (rs.next()) {
                                return@useSessionConnectionWithDialect PasswordExpiryInfo(
                                    username = rs.getString("user"),
                                    host = rs.getString("host"),
                                    daysUntilExpiry = rs.getObject("days_until_expiry")?.let {
                                        (it as Number).toInt()
                                    },
                                    passwordLastChanged = rs.getString("password_last_changed"),
                                    isExpired = rs.getBoolean("is_expired")
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // User query also failed
                }
            }

            // Return basic info if all queries failed
            PasswordExpiryInfo(
                username = username,
                host = host,
                daysUntilExpiry = null,
                passwordLastChanged = null,
                isExpired = false
            )
        }
    }

    /**
     * Gets password expiry days for a user (for login response).
     * Returns null if no expiration is set or can't be determined.
     */
    fun getPasswordExpiryDays(sessionId: String, username: String): Int? {
        return try {
            useSessionConnectionWithDialect(sessionId) { conn, dialect ->
                val sql = dialect.getPasswordExpiryDaysQuery()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, username)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.getObject("days_until_expiry")?.let { (it as Number).toInt() }
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // User might not have permission to query user table
            null
        }
    }
}