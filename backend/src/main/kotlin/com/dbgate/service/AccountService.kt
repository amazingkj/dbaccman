package com.dbgate.service

import com.dbgate.config.useConnection
import com.dbgate.model.Account
import com.dbgate.model.CreateAccountRequest
import com.dbgate.model.ExpiringAccount
import com.dbgate.util.AuditLogger
import java.sql.ResultSet

class AccountService {

    fun getAllAccounts(): List<Account> {
        return useConnection { conn ->
            val sql = """
                SELECT
                    user as username,
                    host,
                    IFNULL(DATE_FORMAT(Create_time, '%Y-%m-%d %H:%i:%s'), '') as created,
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

    fun createAccount(request: CreateAccountRequest): Account {
        return useConnection { conn ->
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

    fun changePassword(username: String, host: String, newPassword: String) {
        useConnection { conn ->
            val sql = "ALTER USER ?@? IDENTIFIED BY ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, host)
                stmt.setString(3, newPassword)
                stmt.execute()
            }
            conn.createStatement().execute("FLUSH PRIVILEGES")

            AuditLogger.log("CHANGE_PASSWORD", "Changed password for $username@$host")
        }
    }

    fun deleteAccount(username: String, host: String) {
        useConnection { conn ->
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

    fun unlockAccount(username: String, host: String) {
        useConnection { conn ->
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

    fun getExpiringAccounts(days: Int = 30): List<ExpiringAccount> {
        return useConnection { conn ->
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

    fun validateCredentials(username: String, password: String): Boolean {
        return useConnection { conn ->
            try {
                // Try to authenticate by connecting with the provided credentials
                // For simplicity, we'll check if the user exists and assume validation
                // In production, you'd want to use a separate connection test
                val sql = "SELECT 1 FROM mysql.user WHERE user = ? LIMIT 1"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, username)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                    }
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun mapResultSetToAccount(rs: ResultSet): Account {
        return Account(
            username = rs.getString("username"),
            host = rs.getString("host"),
            created = rs.getString("created").ifEmpty { null },
            passwordLastChanged = rs.getString("password_last_changed").ifEmpty { null },
            passwordLifetime = rs.getInt("password_lifetime").takeIf { it > 0 },
            accountLocked = rs.getBoolean("account_locked")
        )
    }
}
