package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.config.useOracleScriptContext
import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.dialect.PostgreSQLDialect
import com.dbaccman.model.*
import com.dbaccman.util.AuditLogger
import com.dbaccman.util.InputValidator
import com.dbaccman.websocket.EventBroadcaster
import com.dbaccman.websocket.EventType
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.SQLException

class AccountService {
    private val logger = LoggerFactory.getLogger(AccountService::class.java)
    private val eventScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Broadcast an event asynchronously (fire-and-forget).
     */
    private fun broadcastEvent(type: EventType, data: Map<String, String>? = null) {
        eventScope.launch {
            try {
                EventBroadcaster.broadcast(type, data)
            } catch (e: Exception) {
                logger.warn("Failed to broadcast event $type: ${e.message}")
            }
        }
    }

    /**
     * Build ORDER BY clause based on sort parameters
     */
    private fun buildOrderByClause(sortBy: String?, sortOrder: String, dialect: DatabaseDialect): String {
        if (sortBy == null) return ""

        val direction = if (sortOrder.lowercase() == "desc") "DESC" else "ASC"

        // Map frontend field names to database column names
        // These must match the actual column names in the FROM clause tables
        val columnMap = when (dialect) {
            is OracleDialect -> mapOf(
                "username" to "USERNAME",
                "passwordLastChanged" to "PASSWORD_CHANGE_DATE",
                "passwordLifetime" to "TRUNC(EXPIRY_DATE - PASSWORD_CHANGE_DATE)",
                "accountLocked" to "CASE WHEN ACCOUNT_STATUS LIKE '%LOCKED%' THEN 1 ELSE 0 END",
                "profile" to "PROFILE"
            )
            is MySQLDialect -> mapOf(
                "username" to "user",
                "host" to "host",
                "passwordLastChanged" to "password_last_changed",
                "passwordLifetime" to "password_lifetime",
                "accountLocked" to "account_locked"
            )
            is PostgreSQLDialect -> mapOf(
                "username" to "usename",
                "passwordLastChanged" to "valuntil",
                "accountLocked" to "rolcanlogin"
            )
            else -> emptyMap()
        }

        val column = columnMap[sortBy] ?: return ""

        // Handle NULL values - put them at the end
        // For accountLocked, reverse logic for PostgreSQL (rolcanlogin=false means locked)
        return when (dialect) {
            is OracleDialect -> "ORDER BY $column $direction NULLS LAST"
            is PostgreSQLDialect -> "ORDER BY $column $direction NULLS LAST"
            else -> "ORDER BY $column IS NULL, $column $direction"
        }
    }

    fun getAccountCount(sessionId: String): Int {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getAccountCountQuery()
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    if (rs.next()) rs.getInt("count") else 0
                }
            }
        }
    }

    /**
     * Build filter WHERE clause based on filter parameter
     */
    private fun buildFilterClause(filter: String?, dialect: DatabaseDialect): String {
        return when (filter?.lowercase()) {
            "locked" -> dialect.getLockedAccountsWhereClause()
            "expiring" -> dialect.getExpiringAccountsWhereClause(30) // 30 days for expiring
            else -> ""
        }
    }

    /**
     * Build search WHERE clause for server-side search.
     * Returns a SQL fragment like "AND (LOWER(col1) LIKE ? OR LOWER(col2) LIKE ?)"
     * For "status" column, returns a static clause (no LIKE parameters).
     */
    private fun buildSearchClause(search: String?, searchColumn: String?, dialect: DatabaseDialect): String {
        if (search.isNullOrBlank()) return ""

        // Status search: match against locked/active state (no LIKE params needed)
        if (searchColumn == "status") {
            val isLocked = search.lowercase().let { it.contains("lock") || it.contains("잠") }
            return when (dialect) {
                is OracleDialect -> if (isLocked) "AND ACCOUNT_STATUS LIKE '%LOCKED%'" else "AND ACCOUNT_STATUS NOT LIKE '%LOCKED%'"
                is MySQLDialect -> if (isLocked) "AND account_locked = 'Y'" else "AND account_locked = 'N'"
                is PostgreSQLDialect -> if (isLocked) "AND rolcanlogin = false" else "AND rolcanlogin = true"
                else -> ""
            }
        }

        // Text search: build LIKE clause for searchable columns
        val columnMapping = when (dialect) {
            is OracleDialect -> mapOf("username" to "USERNAME", "profile" to "PROFILE")
            is MySQLDialect -> mapOf("username" to "user", "host" to "host")
            is PostgreSQLDialect -> mapOf("username" to "usename")
            else -> return ""
        }

        val columnsToSearch = when {
            searchColumn == null || searchColumn == "all" -> columnMapping.values.toList()
            columnMapping.containsKey(searchColumn) -> listOf(columnMapping[searchColumn]!!)
            else -> columnMapping.values.toList()
        }

        return "AND (" + columnsToSearch.joinToString(" OR ") { "LOWER($it) LIKE ?" } + ")"
    }

    /**
     * Get the number of LIKE parameters needed for a search clause.
     */
    private fun getSearchParamCount(search: String?, searchColumn: String?, dialect: DatabaseDialect): Int {
        if (search.isNullOrBlank()) return 0
        if (searchColumn == "status") return 0 // Status uses static clause

        val columnMapping = when (dialect) {
            is OracleDialect -> mapOf("username" to "USERNAME", "profile" to "PROFILE")
            is MySQLDialect -> mapOf("username" to "user", "host" to "host")
            is PostgreSQLDialect -> mapOf("username" to "usename")
            else -> return 0
        }

        return when {
            searchColumn == null || searchColumn == "all" -> columnMapping.size
            columnMapping.containsKey(searchColumn) -> 1
            else -> columnMapping.size
        }
    }

    /**
     * Get paginated accounts with stats.
     * Optimized: Uses single query with window functions to get data + counts together.
     */
    fun getPaginatedAccounts(
        sessionId: String,
        page: Int,
        pageSize: Int,
        sortBy: String? = null,
        sortOrder: String = "asc",
        filter: String? = null,
        search: String? = null,
        searchColumn: String? = null
    ): PaginatedAccountsResponse {
        val isContainerRoot = SessionConnectionManager.isContainerRoot(sessionId)

        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val offset = (page - 1) * pageSize
            val orderByClause = buildOrderByClause(sortBy, sortOrder, dialect)
            val filterClause = buildFilterClause(filter, dialect)
            val searchClause = buildSearchClause(search, searchColumn, dialect)

            // Combine filter and search clauses
            val combinedFilterClause = if (searchClause.isNotEmpty()) "$filterClause $searchClause" else filterClause

            // Use optimized query that returns total_count and locked_count via window functions
            val sql = dialect.getOptimizedPaginatedAccountsQuery(orderByClause, combinedFilterClause)

            var totalCount = 0
            var lockedCount = 0

            val accounts = conn.prepareStatement(sql).use { stmt ->
                var paramIndex = 1

                // Set search LIKE parameters
                val searchParamCount = getSearchParamCount(search, searchColumn, dialect)
                if (searchParamCount > 0) {
                    val searchPattern = "%${search!!.lowercase()}%"
                    repeat(searchParamCount) {
                        stmt.setString(paramIndex++, searchPattern)
                    }
                }

                // Oracle uses OFFSET first, then FETCH (limit)
                // MySQL/PostgreSQL use LIMIT first, then OFFSET
                if (dialect is OracleDialect) {
                    stmt.setInt(paramIndex++, offset)
                    stmt.setInt(paramIndex, pageSize)
                } else {
                    stmt.setInt(paramIndex++, pageSize)
                    stmt.setInt(paramIndex, offset)
                }

                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<Account>()
                    var firstRow = true
                    while (rs.next()) {
                        // Get counts from the first row (same for all rows due to window function)
                        if (firstRow) {
                            totalCount = rs.getInt("total_count")
                            lockedCount = rs.getInt("locked_count")
                            firstRow = false
                        }

                        val account = mapResultSetToAccount(rs)
                        // Strip C## prefix for display in CDB root
                        if (isContainerRoot && dialect is OracleDialect) {
                            result.add(account.copy(
                                username = dialect.stripCdbPrefix(account.username)
                            ))
                        } else {
                            result.add(account)
                        }
                    }
                    result
                }
            }

            PaginatedAccountsResponse(
                data = accounts,
                pagination = PaginationInfo.of(page, pageSize, totalCount),
                stats = AccountStats(
                    totalAccounts = totalCount,
                    lockedAccounts = lockedCount,
                    activeAccounts = totalCount - lockedCount
                )
            )
        }
    }

    fun getAllAccounts(sessionId: String): List<Account> {
        val isContainerRoot = SessionConnectionManager.isContainerRoot(sessionId)

        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getAllAccountsQuery()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val accounts = mutableListOf<Account>()
                    while (rs.next()) {
                        val account = mapResultSetToAccount(rs)
                        // Strip C## prefix for display in CDB root
                        if (isContainerRoot && dialect is OracleDialect) {
                            accounts.add(account.copy(
                                username = dialect.stripCdbPrefix(account.username)
                            ))
                        } else {
                            accounts.add(account)
                        }
                    }
                    accounts
                }
            }
        }
    }

    fun createAccount(sessionId: String, request: CreateAccountRequest): Account {
        // Validate inputs before SQL generation
        InputValidator.validateIdentifier(request.username, "username")
        InputValidator.validateHost(request.host)
        InputValidator.validatePassword(request.password)
        InputValidator.validateExpireDays(request.expireDays)

        return useOracleScriptContext(sessionId) { conn, dialect ->
            val createSql = dialect.getCreateUserSql(request.username, request.host, request.password)
            logger.info("Creating user with SQL: ${InputValidator.sanitizeForLogging(createSql)}")

            try {
                // 1. Create user
                conn.createStatement().use { stmt ->
                    stmt.execute(createSql)
                }

                // 2. Set password expiration
                if (request.expireDays > 0) {
                    if (dialect is OracleDialect) {
                        // Oracle uses profiles for password expiry
                        val profileName = dialect.getProfileName(request.expireDays)

                        // Check if profile exists
                        val profileExists = conn.prepareStatement(dialect.getCheckProfileExistsSql()).use { stmt ->
                            stmt.setString(1, profileName)
                            stmt.executeQuery().use { rs ->
                                rs.next() && rs.getInt(1) > 0
                            }
                        }

                        // Create profile if it doesn't exist
                        if (!profileExists) {
                            try {
                                conn.createStatement().use { stmt ->
                                    stmt.execute(dialect.getCreateProfileSql(request.expireDays, false))
                                }
                                logger.info("Created Oracle profile $profileName")
                            } catch (e: SQLException) {
                                if (e.errorCode != 2379) { // ORA-02379: profile already exists
                                    logger.warn("Could not create profile $profileName: ${e.message}")
                                }
                            }
                        }

                        // Assign profile to user
                        try {
                            val alterSql = dialect.getAlterUserPasswordExpireSql(request.username, request.host, request.expireDays)
                            logger.info("Assigning profile with SQL: $alterSql")
                            conn.createStatement().use { stmt ->
                                stmt.execute(alterSql)
                            }
                        } catch (e: SQLException) {
                            logger.warn("Could not assign profile to user ${request.username}: ${e.message}")
                        }
                    } else {
                        // Non-Oracle: set password expiry directly
                        val alterSql = dialect.getAlterUserPasswordExpireSql(request.username, request.host, request.expireDays)
                        conn.createStatement().use { stmt ->
                            stmt.execute(alterSql)
                        }
                    }
                }
            } catch (e: SQLException) {
                logger.error("Failed to create user. SQL: $createSql, Error: ${e.message}, ErrorCode: ${e.errorCode}")

                val isUserExists = e.errorCode == 1920 || e.errorCode == 1396 || e.sqlState == "42710"
                if (isUserExists) {
                    throw SQLException(
                        "User '${request.username}' already exists. Please choose a different username.",
                        e.sqlState,
                        e.errorCode
                    )
                }
                if (e.errorCode == 65048) {
                    throw SQLException(
                        "Cannot create user '${request.username}' (ORA-65048). Original error: ${e.message}",
                        e.sqlState,
                        e.errorCode
                    )
                }
                throw e
            }

            // Flush privileges if required
            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            AuditLogger.log("CREATE_ACCOUNT", "Created account ${request.username}@${request.host}")

            // Broadcast account created event
            broadcastEvent(EventType.ACCOUNT_CREATED, mapOf(
                "username" to request.username,
                "host" to request.host
            ))

            Account(
                username = request.username,
                host = request.host,
                passwordLifetime = request.expireDays
            )
        }
    }

    fun changePassword(sessionId: String, username: String, host: String, newPassword: String, expireImmediately: Boolean = false) {
        // Validate inputs
        InputValidator.validateIdentifier(username, "username")
        InputValidator.validateHost(host)
        InputValidator.validatePassword(newPassword)

        useOracleScriptContext(sessionId) { conn, dialect ->
            try {
                val sql = dialect.getAlterUserPasswordSql(username, host, newPassword)
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }

                if (expireImmediately) {
                    val expireSql = dialect.getExpirePasswordSql(username, host)
                    conn.createStatement().use { stmt ->
                        stmt.execute(expireSql)
                    }
                }
            } catch (e: SQLException) {
                handleOracleUserModifyError(e, username, "change password for")
            }

            dialect.getFlushPrivilegesSql()?.let { flushSql ->
                conn.createStatement().execute(flushSql)
            }

            AuditLogger.log("CHANGE_PASSWORD", "Changed password for $username@$host")
        }
    }

    fun deleteAccount(sessionId: String, username: String, host: String) {
        // Validate inputs
        InputValidator.validateIdentifier(username, "username")
        InputValidator.validateHost(host)

        useOracleScriptContext(sessionId) { conn, dialect ->
            try {
                val sql = dialect.getDropUserSql(username, host)
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

            // Broadcast account deleted event
            broadcastEvent(EventType.ACCOUNT_DELETED, mapOf(
                "username" to username,
                "host" to host
            ))
        }
    }

    fun unlockAccount(sessionId: String, username: String, host: String) {
        // Validate inputs
        InputValidator.validateIdentifier(username, "username")
        InputValidator.validateHost(host)

        useOracleScriptContext(sessionId) { conn, dialect ->
            try {
                val sql = dialect.getUnlockAccountSql(username, host)
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

            // Broadcast account unlocked event
            broadcastEvent(EventType.ACCOUNT_UNLOCKED, mapOf(
                "username" to username,
                "host" to host
            ))
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
        // For delete operations, this is acceptable (idempotent delete)
        if (e.errorCode == 1918) {
            if (operation == "delete") {
                // User already doesn't exist - treat as successful delete
                return
            }
            throw SQLException(
                "User '$username' does not exist.",
                e.sqlState,
                e.errorCode
            )
        }
        // ORA-28014: cannot drop administrative users or roles
        // This can occur if:
        // 1. The user is an Oracle-maintained/protected user
        // 2. Oracle Database Vault is enabled
        // 3. The user has been granted protected roles (DBA, SYSDBA, etc.)
        if (e.errorCode == 28014) {
            throw SQLException(
                "Cannot $operation user '$username'. " +
                "This error (ORA-28014) typically occurs when Oracle Database Vault is enabled, " +
                "or the user has administrative privileges. " +
                "Check: SELECT * FROM DBA_DV_STATUS; and SELECT * FROM DBA_ROLE_PRIVS WHERE GRANTEE = '${username.uppercase()}';",
                e.sqlState,
                e.errorCode
            )
        }
        // ORA-01940: cannot drop a user that is currently connected
        if (e.errorCode == 1940) {
            throw SQLException(
                "Cannot $operation user '$username' because the user is currently connected. " +
                "Please disconnect all sessions for this user first.",
                e.sqlState,
                e.errorCode
            )
        }
        throw e
    }

    fun setDefaultTablespace(sessionId: String, username: String, host: String, tablespace: String, quota: String? = null) {
        // Validate inputs
        InputValidator.validateIdentifier(username, "username")
        InputValidator.validateHost(host)
        InputValidator.validateIdentifier(tablespace, "tablespace")
        quota?.let { InputValidator.validateQuota(it) }

        useOracleScriptContext(sessionId) { conn, dialect ->
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
        // Try to get profile column (Oracle only) - returns null if column doesn't exist
        val profile = try {
            rs.getString("profile")
        } catch (e: SQLException) {
            null
        }

        return Account(
            username = rs.getString("username"),
            host = rs.getString("host"),
            passwordLastChanged = rs.getString("password_last_changed")?.ifEmpty { null },
            passwordLifetime = rs.getObject("password_lifetime")?.let { (it as Number).toInt() }?.takeIf { it > 0 },
            accountLocked = rs.getBoolean("account_locked"),
            profile = profile
        )
    }

    // ==================== Clone Account ====================

    fun cloneAccount(sessionId: String, request: CloneAccountRequest): Account {
        val permissionService = PermissionService()

        // Get source user's permissions
        val sourcePermissions = if (request.copyPermissions) {
            permissionService.getUserPermissions(sessionId, request.sourceUsername, request.sourceHost)
        } else {
            emptyList()
        }

        // Create new account
        val createRequest = CreateAccountRequest(
            username = request.newUsername,
            host = request.newHost,
            password = request.newPassword,
            expireDays = request.expireDays
        )
        val newAccount = createAccount(sessionId, createRequest)

        // Copy permissions using batch operation for efficiency
        if (request.copyPermissions && sourcePermissions.isNotEmpty()) {
            // Group permissions by database.table and create batch requests
            val permissionsByTarget = sourcePermissions.groupBy { "${it.database}.${it.table}" }

            val grantRequests = permissionsByTarget.map { (_, perms) ->
                val firstPerm = perms.first()
                val privileges = perms.map { it.privilege }
                GrantPermissionRequest(
                    username = request.newUsername,
                    host = request.newHost,
                    database = firstPerm.database,
                    table = firstPerm.table,
                    privileges = privileges
                )
            }

            // Execute all grants in a single batch (one connection, one flush)
            val (successCount, errors) = permissionService.batchGrantPermissions(sessionId, grantRequests)

            // Log any errors that occurred
            errors.forEach { error ->
                AuditLogger.log("CLONE_ACCOUNT_WARNING", "Could not copy permission: $error")
            }

            logger.info("Cloned permissions: $successCount succeeded, ${errors.size} failed")
        }

        AuditLogger.log(
            "CLONE_ACCOUNT",
            "Cloned account ${request.sourceUsername}@${request.sourceHost} to ${request.newUsername}@${request.newHost}" +
            if (request.copyPermissions) " with ${sourcePermissions.size} permissions" else ""
        )

        return newAccount
    }

    // ==================== Batch Operations ====================

    fun batchCreateAccounts(sessionId: String, request: BatchCreateAccountRequest): BatchOperationResult {
        val success = mutableListOf<String>()
        val failed = mutableListOf<BatchOperationError>()

        request.accounts.forEach { accountRequest ->
            val accountId = "${accountRequest.username}@${accountRequest.host}"
            try {
                createAccount(sessionId, accountRequest)
                success.add(accountId)
            } catch (e: Exception) {
                failed.add(BatchOperationError(accountId, e.message ?: "Unknown error"))
            }
        }

        AuditLogger.log(
            "BATCH_CREATE_ACCOUNTS",
            "Created ${success.size} accounts, ${failed.size} failed"
        )

        return BatchOperationResult(success, failed)
    }

    fun batchDeleteAccounts(sessionId: String, request: BatchDeleteRequest): BatchOperationResult {
        val success = mutableListOf<String>()
        val failed = mutableListOf<BatchOperationError>()

        request.accounts.forEach { account ->
            val accountId = "${account.username}@${account.host}"
            try {
                deleteAccount(sessionId, account.username, account.host)
                success.add(accountId)
            } catch (e: Exception) {
                failed.add(BatchOperationError(accountId, e.message ?: "Unknown error"))
            }
        }

        AuditLogger.log(
            "BATCH_DELETE_ACCOUNTS",
            "Deleted ${success.size} accounts, ${failed.size} failed"
        )

        return BatchOperationResult(success, failed)
    }

    fun batchUnlockAccounts(sessionId: String, request: BatchUnlockRequest): BatchOperationResult {
        val success = mutableListOf<String>()
        val failed = mutableListOf<BatchOperationError>()

        request.accounts.forEach { account ->
            val accountId = "${account.username}@${account.host}"
            try {
                unlockAccount(sessionId, account.username, account.host)
                success.add(accountId)
            } catch (e: Exception) {
                failed.add(BatchOperationError(accountId, e.message ?: "Unknown error"))
            }
        }

        AuditLogger.log(
            "BATCH_UNLOCK_ACCOUNTS",
            "Unlocked ${success.size} accounts, ${failed.size} failed"
        )

        return BatchOperationResult(success, failed)
    }

    // ==================== Export ====================

    fun exportAccountsToCsv(sessionId: String, includePermissions: Boolean = false): String {
        val accounts = getAllAccounts(sessionId)
        val sb = StringBuilder()

        if (includePermissions) {
            sb.appendLine("username,host,password_last_changed,password_lifetime,account_locked,database,table,privilege")

            // Optimized: Single batch query for all permissions (N+1 → 1 query)
            val permissionService = PermissionService()
            val allPermissions = try {
                permissionService.getAllUsersPermissions(sessionId)
            } catch (e: Exception) {
                logger.warn("Failed to fetch all permissions for export: ${e.message}")
                emptyMap()
            }

            accounts.forEach { account ->
                val grantee = "${account.username}@${account.host}"
                // Try different grantee formats (Oracle uses uppercase, MySQL uses 'user'@'host')
                val permissions = allPermissions[grantee]
                    ?: allPermissions[account.username.uppercase()]
                    ?: allPermissions["'${account.username}'@'${account.host}'"]
                    ?: emptyList()

                if (permissions.isEmpty()) {
                    sb.appendLine("${escapeCsv(account.username)},${escapeCsv(account.host)},${escapeCsv(account.passwordLastChanged ?: "")},${account.passwordLifetime ?: ""},${account.accountLocked},,,")
                } else {
                    permissions.forEach { perm ->
                        sb.appendLine("${escapeCsv(account.username)},${escapeCsv(account.host)},${escapeCsv(account.passwordLastChanged ?: "")},${account.passwordLifetime ?: ""},${account.accountLocked},${escapeCsv(perm.database)},${escapeCsv(perm.table)},${escapeCsv(perm.privilege)}")
                    }
                }
            }
        } else {
            sb.appendLine("username,host,password_last_changed,password_lifetime,account_locked")
            accounts.forEach { account ->
                sb.appendLine("${escapeCsv(account.username)},${escapeCsv(account.host)},${escapeCsv(account.passwordLastChanged ?: "")},${account.passwordLifetime ?: ""},${account.accountLocked}")
            }
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}