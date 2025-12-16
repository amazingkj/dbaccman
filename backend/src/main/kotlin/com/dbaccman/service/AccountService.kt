package com.dbaccman.service

import com.dbaccman.config.useSessionConnection
import com.dbaccman.model.Account
import com.dbaccman.model.CreateAccountRequest
import com.dbaccman.model.ExpiringAccount
import com.dbaccman.model.ChangePasswordRequest
import com.dbaccman.util.AuditLogger
import java.sql.ResultSet

class AccountService {

    fun getAllAccounts(sessionId: String): List<Account> {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    user as username,
                    host,
                    IFNULL(DATE_FORMAT(password_last_changed, '%Y-%m-%d %H:%i:%s'), '') as password_last_changed,
                    IFNULL(password_lifetime, 0) as password_lifetime,
                    account_locked = 'Y' as account_locked
                FROM mysql.user
                WHERE user NOT IN ('mysql.sys', 'mysql.session', 'mysql.infoschema')
                ORDER BY user, host
            """.trimIndent()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val accounts = mutableListOf<Account>()
                    while (rs.next()) {
                        accounts.add(mapResultSetToAccount(rs))
                    }
                    accounts
                }
            }
        }
    }

    fun createAccount(sessionId: String, request: CreateAccountRequest): Account {
        return useSessionConnection(sessionId) { conn ->
            // Use prepared statement to prevent SQL injection
            val createSql = "CREATE USER ?@? IDENTIFIED BY ?"
            conn.prepareStatement(createSql).use { stmt ->
                stmt.setString(1, request.username)
                stmt.setString(2, request.host)
                stmt.setString(3, request.password)
                stmt.execute()
            }

            // Set password expiration
            if (request.expireDays > 0) {
                val alterSql = "ALTER USER ?@? PASSWORD EXPIRE INTERVAL ? DAY"
                conn.prepareStatement(alterSql).use { stmt ->
                    stmt.setString(1, request.username)
                    stmt.setString(2, request.host)
                    stmt.setInt(3, request.expireDays)
                    stmt.execute()
                }
            }

            conn.createStatement().execute("FLUSH PRIVILEGES")

            AuditLogger.log("CREATE_ACCOUNT", "Created account ${request.username}@${request.host}")

            Account(
                username = request.username,
                host = request.host,
                passwordLifetime = request.expireDays
            )
        }
    }

    fun changePassword(sessionId: String, username: String, host: String, newPassword: String, expireImmediately: Boolean = false) {
        useSessionConnection(sessionId) { conn ->
            val sql = "ALTER USER ?@? IDENTIFIED BY ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, host)
                stmt.setString(3, newPassword)
                stmt.execute()
            }

            if (expireImmediately) {
                val expireSql = "ALTER USER ?@? PASSWORD EXPIRE"
                conn.prepareStatement(expireSql).use { stmt ->
                    stmt.setString(1, username)
                    stmt.setString(2, host)
                    stmt.execute()
                }
            }

            conn.createStatement().execute("FLUSH PRIVILEGES")

            AuditLogger.log("CHANGE_PASSWORD", "Changed password for $username@$host")
        }
    }

    fun deleteAccount(sessionId: String, username: String, host: String) {
        useSessionConnection(sessionId) { conn ->
            val sql = "DROP USER ?@?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, host)
                stmt.execute()
            }
            conn.createStatement().execute("FLUSH PRIVILEGES")

            AuditLogger.log("DELETE_ACCOUNT", "Deleted account $username@$host")
        }
    }

    fun unlockAccount(sessionId: String, username: String, host: String) {
        useSessionConnection(sessionId) { conn ->
            val sql = "ALTER USER ?@? ACCOUNT UNLOCK"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, host)
                stmt.execute()
            }
            conn.createStatement().execute("FLUSH PRIVILEGES")

            AuditLogger.log("UNLOCK_ACCOUNT", "Unlocked account $username@$host")
        }
    }

    fun getExpiringAccounts(sessionId: String, days: Int = 30): List<ExpiringAccount> {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    user as username,
                    host,
                    DATEDIFF(
                        DATE_ADD(password_last_changed, INTERVAL IFNULL(password_lifetime, 0) DAY),
                        NOW()
                    ) as days_until_expiry
                FROM mysql.user
                WHERE password_lifetime > 0
                AND password_last_changed IS NOT NULL
                AND DATEDIFF(
                    DATE_ADD(password_last_changed, INTERVAL password_lifetime DAY),
                    NOW()
                ) < ?
                AND DATEDIFF(
                    DATE_ADD(password_last_changed, INTERVAL password_lifetime DAY),
                    NOW()
                ) >= 0
                ORDER BY days_until_expiry ASC
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, days)
                stmt.executeQuery().use { rs ->
                    val accounts = mutableListOf<ExpiringAccount>()
                    while (rs.next()) {
                        accounts.add(
                            ExpiringAccount(
                                username = rs.getString("username"),
                                host = rs.getString("host"),
                                daysUntilExpiry = rs.getInt("days_until_expiry")
                            )
                        )
                    }
                    accounts
                }
            }
        }
    }

    private fun mapResultSetToAccount(rs: ResultSet): Account {
        return Account(
            username = rs.getString("username"),
            host = rs.getString("host"),
            passwordLastChanged = rs.getString("password_last_changed").ifEmpty { null },
            passwordLifetime = rs.getInt("password_lifetime").takeIf { it > 0 },
            accountLocked = rs.getBoolean("account_locked")
        )
    }
}
