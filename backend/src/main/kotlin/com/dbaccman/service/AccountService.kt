package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.model.Account
import com.dbaccman.model.CreateAccountRequest
import com.dbaccman.model.ExpiringAccount
import com.dbaccman.util.AuditLogger
import java.sql.ResultSet
import java.sql.SQLException

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
            // Create user - DDL statement, use createStatement (no prepared statement for DDL)
            val createSql = dialect.getCreateUserSql(request.username, request.host, request.password)

            try {
                conn.createStatement().use { stmt ->
                    stmt.execute(createSql)
                }
            } catch (e: SQLException) {
                // ORA-01920: User name conflicts with another user or role name (user already exists)
                // MySQL error 1396: Operation CREATE USER failed (user already exists)
                // PostgreSQL SQLSTATE 42710: duplicate_object (role already exists)
                val isUserExists = e.errorCode == 1920 || e.errorCode == 1396 || e.sqlState == "42710"
                if (isUserExists) {
                    throw SQLException(
                        "User '${request.username}' already exists. Please choose a different username.",
                        e.sqlState,
                        e.errorCode
                    )
                }
                // ORA-65048: error processing DDL in PDB - user might already exist as common user
                if (e.errorCode == 65048 && dialect is OracleDialect) {
                    throw SQLException(
                        "Cannot create user '${request.username}' in this PDB. " +
                        "A common user with the same name may already exist in CDB\$ROOT. " +
                        "Please use a different username or connect to CDB\$ROOT to manage common users.",
                        e.sqlState,
                        e.errorCode
                    )
                }
                // ORA-65096: Common user/role name is invalid - happens in Oracle CDB root
                if (e.errorCode == 65096 && dialect is OracleDialect) {
                    // Check if we're in CDB$ROOT (not a PDB)
                    val containerName = conn.createStatement().use { stmt ->
                        stmt.executeQuery(dialect.getContainerNameSql()).use { rs ->
                            if (rs.next()) rs.getString(1) else null
                        }
                    }

                    // Only use _ORACLE_SCRIPT workaround in CDB$ROOT, not in PDBs
                    if (containerName == "CDB\$ROOT") {
                        conn.createStatement().use { stmt ->
                            stmt.execute(dialect.getEnableLocalUserSql())
                        }
                        try {
                            conn.createStatement().use { stmt ->
                                stmt.execute(createSql)
                            }
                        } finally {
                            try {
                                conn.createStatement().use { stmt ->
                                    stmt.execute(dialect.getDisableLocalUserSql())
                                }
                            } catch (_: Exception) {
                                // Ignore cleanup errors
                            }
                        }
                    } else {
                        // In PDB, ORA-65096 shouldn't happen - rethrow with helpful message
                        throw SQLException(
                            "Cannot create user '${request.username}' in PDB '$containerName'. " +
                            "In a PDB, user names should not start with 'C##'. Original error: ${e.message}",
                            e.sqlState,
                            e.errorCode
                        )
                    }
                } else {
                    throw e
                }
            }

            // Set password expiration
            if (request.expireDays > 0) {
                val alterSql = dialect.getAlterUserPasswordExpireSql(request.username, request.host, request.expireDays)
                try {
                    conn.createStatement().use { stmt ->
                        stmt.execute(alterSql)
                    }
                } catch (e: SQLException) {
                    // ORA-65048: In Oracle PDB, profile management may not work for local users
                    // The user is already created, so just log this as a warning and continue
                    if (e.errorCode == 65048 && dialect is OracleDialect) {
                        AuditLogger.log(
                            "CREATE_ACCOUNT_WARNING",
                            "User ${request.username} created but password expiry could not be set in PDB"
                        )
                        // Continue - user was created successfully
                    } else {
                        throw e
                    }
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
            // DDL statement - use createStatement
            val sql = dialect.getAlterUserPasswordSql(username, host, newPassword)
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }
            } catch (e: SQLException) {
                handleOracleUserModifyError(e, username, "change password for")
            }

            if (expireImmediately) {
                val expireSql = dialect.getExpirePasswordSql(username, host)
                try {
                    conn.createStatement().use { stmt ->
                        stmt.execute(expireSql)
                    }
                } catch (e: SQLException) {
                    handleOracleUserModifyError(e, username, "expire password for")
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
            // DDL statement - use createStatement
            val sql = dialect.getDropUserSql(username, host)
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }
            } catch (e: SQLException) {
                handleOracleUserModifyError(e, username, "delete")
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            AuditLogger.log("DELETE_ACCOUNT", "Deleted account $username@$host")
        }
    }

    fun unlockAccount(sessionId: String, username: String, host: String) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            // DDL statement - use createStatement
            val sql = dialect.getUnlockAccountSql(username, host)
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }
            } catch (e: SQLException) {
                handleOracleUserModifyError(e, username, "unlock")
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            AuditLogger.log("UNLOCK_ACCOUNT", "Unlocked account $username@$host")
        }
    }

    private fun handleOracleUserModifyError(e: SQLException, username: String, operation: String) {
        // ORA-65048: error processing DDL in PDB
        if (e.errorCode == 65048) {
            throw SQLException(
                "Cannot $operation user '$username' in this PDB. " +
                "The user may be a common user (C##) or doesn't exist in this container. " +
                "Please connect directly to CDB\$ROOT to manage common users.",
                e.sqlState,
                e.errorCode
            )
        }
        // ORA-01918: user does not exist
        if (e.errorCode == 1918) {
            throw SQLException(
                "User '$username' does not exist.",
                e.sqlState,
                e.errorCode
            )
        }
        throw e
    }

    fun setDefaultTablespace(sessionId: String, username: String, host: String, tablespace: String, quota: String? = null) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            // Set default tablespace
            val setDefaultSql = dialect.getSetDefaultTablespaceSql(username, host, tablespace)
            if (setDefaultSql != null) {
                try {
                    conn.createStatement().use { stmt ->
                        stmt.execute(setDefaultSql)
                    }
                } catch (e: SQLException) {
                    // ORA-65048: error processing DDL in PDB
                    if (e.errorCode == 65048) {
                        throw SQLException(
                            "Cannot modify user '$username' in this PDB. " +
                            "The user may be a common user (C##) or doesn't exist in this container. " +
                            "Please connect directly to CDB\$ROOT to modify common users.",
                            e.sqlState,
                            e.errorCode
                        )
                    }
                    // ORA-01918: user does not exist
                    if (e.errorCode == 1918) {
                        throw SQLException(
                            "User '$username' does not exist.",
                            e.sqlState,
                            e.errorCode
                        )
                    }
                    // ORA-00959: tablespace does not exist
                    if (e.errorCode == 959) {
                        throw SQLException(
                            "Tablespace '$tablespace' does not exist.",
                            e.sqlState,
                            e.errorCode
                        )
                    }
                    throw e
                }
                AuditLogger.log("SET_DEFAULT_TABLESPACE", "Set default tablespace $tablespace for $username")
            } else {
                throw UnsupportedOperationException("This database does not support user-level default tablespace")
            }

            // Set quota if specified
            if (quota != null) {
                val setQuotaSql = dialect.getSetTablespaceQuotaSql(username, host, tablespace, quota)
                if (setQuotaSql != null) {
                    try {
                        conn.createStatement().use { stmt ->
                            stmt.execute(setQuotaSql)
                        }
                    } catch (e: SQLException) {
                        // ORA-65048: error processing DDL in PDB
                        if (e.errorCode == 65048) {
                            throw SQLException(
                                "Cannot set quota for user '$username' in this PDB. " +
                                "The user may be a common user (C##) or doesn't exist in this container.",
                                e.sqlState,
                                e.errorCode
                            )
                        }
                        throw e
                    }
                    AuditLogger.log("SET_TABLESPACE_QUOTA", "Set quota $quota on $tablespace for $username")
                }
            }
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