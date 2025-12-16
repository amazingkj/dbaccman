package com.dbaccman.service

import com.dbaccman.config.useSessionConnection
import com.dbaccman.model.PasswordExpiryInfo

class PrivilegeService {

    /**
     * Checks if the current session user has admin privileges (CREATE USER).
     */
    fun isAdmin(sessionId: String): Boolean {
        return useSessionConnection(sessionId) { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SHOW GRANTS FOR CURRENT_USER()").use { rs ->
                    while (rs.next()) {
                        val grant = rs.getString(1).uppercase()
                        // Check for admin-level privileges
                        if (grant.contains("ALL PRIVILEGES ON *.*") ||
                            grant.contains("CREATE USER") ||
                            (grant.contains("ALL PRIVILEGES") && grant.contains("WITH GRANT OPTION"))) {
                            return@useSessionConnection true
                        }
                    }
                    false
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
     */
    fun getMyPasswordExpiry(sessionId: String, username: String, host: String): PasswordExpiryInfo {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    user,
                    host,
                    password_lifetime,
                    password_last_changed,
                    CASE
                        WHEN password_expired = 'Y' THEN true
                        ELSE false
                    END as is_expired,
                    CASE
                        WHEN password_lifetime IS NULL OR password_lifetime = 0 THEN NULL
                        ELSE DATEDIFF(
                            DATE_ADD(password_last_changed, INTERVAL password_lifetime DAY),
                            NOW()
                        )
                    END as days_until_expiry
                FROM mysql.user
                WHERE user = ? AND host = ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, host)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        PasswordExpiryInfo(
                            username = rs.getString("user"),
                            host = rs.getString("host"),
                            daysUntilExpiry = rs.getObject("days_until_expiry")?.let {
                                (it as Number).toInt()
                            },
                            passwordLastChanged = rs.getString("password_last_changed"),
                            isExpired = rs.getBoolean("is_expired")
                        )
                    } else {
                        // If can't query mysql.user, return basic info
                        PasswordExpiryInfo(
                            username = username,
                            host = host,
                            daysUntilExpiry = null,
                            passwordLastChanged = null,
                            isExpired = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Gets password expiry days for a user (for login response).
     * Returns null if no expiration is set or can't be determined.
     */
    fun getPasswordExpiryDays(sessionId: String, username: String): Int? {
        return try {
            useSessionConnection(sessionId) { conn ->
                val sql = """
                    SELECT
                        CASE
                            WHEN password_lifetime IS NULL OR password_lifetime = 0 THEN NULL
                            ELSE DATEDIFF(
                                DATE_ADD(password_last_changed, INTERVAL password_lifetime DAY),
                                NOW()
                            )
                        END as days_until_expiry
                    FROM mysql.user
                    WHERE user = ?
                    LIMIT 1
                """.trimIndent()

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
            // User might not have permission to query mysql.user
            null
        }
    }
}
