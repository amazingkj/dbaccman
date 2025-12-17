package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.model.Account
import com.dbaccman.model.CreateAccountRequest
import com.dbaccman.model.ExpiringAccount
import com.dbaccman.util.AuditLogger
import java.sql.ResultSet

class AccountService {

    fun getAllAccounts(sessionId: String): List<Account> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getAllAccountsQuery()

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
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            // Create user
            val createSql = dialect.getCreateUserSql(request.username, request.host, request.password)
            conn.prepareStatement(createSql).use { stmt ->
                stmt.setString(1, request.username)
                stmt.setString(2, request.host)
                stmt.setString(3, request.password)
                stmt.execute()
            }

            // Set password expiration
            if (request.expireDays > 0) {
                val alterSql = dialect.getAlterUserPasswordExpireSql(request.username, request.host, request.expireDays)
                conn.prepareStatement(alterSql).use { stmt ->
                    stmt.setString(1, request.username)
                    stmt.setString(2, request.host)
                    stmt.setInt(3, request.expireDays)
                    stmt.execute()
                }
            }

            // Flush privileges if required
            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            AuditLogger.log("CREATE_ACCOUNT", "Created account ${request.username}@${request.host}")

            Account(
                username = request.username,
                host = request.host,
                passwordLifetime = request.expireDays
            )
        }
    }

    fun changePassword(sessionId: String, username: String, host: String, newPassword: String, expireImmediately: Boolean = false) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getAlterUserPasswordSql(username, host, newPassword)
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, host)
                stmt.setString(3, newPassword)
                stmt.execute()
            }

            if (expireImmediately) {
                val expireSql = dialect.getExpirePasswordSql(username, host)
                conn.prepareStatement(expireSql).use { stmt ->
                    stmt.setString(1, username)
                    stmt.setString(2, host)
                    stmt.execute()
                }
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            AuditLogger.log("CHANGE_PASSWORD", "Changed password for $username@$host")
        }
    }

    fun deleteAccount(sessionId: String, username: String, host: String) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getDropUserSql(username, host)
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, host)
                stmt.execute()
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            AuditLogger.log("DELETE_ACCOUNT", "Deleted account $username@$host")
        }
    }

    fun unlockAccount(sessionId: String, username: String, host: String) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getUnlockAccountSql(username, host)
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, username)
                stmt.setString(2, host)
                stmt.execute()
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            AuditLogger.log("UNLOCK_ACCOUNT", "Unlocked account $username@$host")
        }
    }

    fun getExpiringAccounts(sessionId: String, days: Int = 30): List<ExpiringAccount> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getExpiringAccountsQuery()

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
            passwordLastChanged = rs.getString("password_last_changed")?.ifEmpty { null },
            passwordLifetime = rs.getObject("password_lifetime")?.let { (it as Number).toInt() }?.takeIf { it > 0 },
            accountLocked = rs.getBoolean("account_locked")
        )
    }
}