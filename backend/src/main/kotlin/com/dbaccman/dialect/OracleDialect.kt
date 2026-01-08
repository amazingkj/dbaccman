package com.dbaccman.dialect

/**
 * Oracle-specific implementation of DatabaseDialect.
 * Supports Oracle 12c+ features.
 */
class OracleDialect : DatabaseDialect {

    override val type: DatabaseType = DatabaseType.ORACLE

    // ==================== Connection Settings ====================

    override fun getJdbcUrl(host: String, port: Int, database: String?): String {
        // Oracle uses SID or Service Name - default to ORCL if not specified
        val serviceName = database ?: "ORCL"
        return "jdbc:oracle:thin:@//$host:$port/$serviceName"
    }

    override fun getDriverClassName(): String = "oracle.jdbc.OracleDriver"

    override fun getConnectionTestQuery(): String = "SELECT 1 FROM DUAL"

    // ==================== Identifier Quoting ====================

    override fun quoteIdentifier(identifier: String): String {
        return "\"${identifier.replace("\"", "\"\"")}\""
    }

    // ==================== Session Queries ====================

    override fun getActiveSessionsQuery(): String = """
        SELECT
            s.SID as pid,
            s.SERIAL# as serial_num,
            s.USERNAME as sess_user,
            s.MACHINE as host,
            s.SCHEMANAME as database_name,
            s.STATUS as command,
            ROUND((SYSDATE - s.LOGON_TIME) * 24 * 60 * 60) as time,
            s.STATE as state,
            q.SQL_TEXT as query
        FROM V${'$'}SESSION s
        LEFT JOIN V${'$'}SQLAREA q ON s.SQL_ID = q.SQL_ID
        WHERE s.TYPE = 'USER'
        AND s.SID != SYS_CONTEXT('USERENV', 'SID')
        ORDER BY s.LOGON_TIME DESC
    """.trimIndent()

    override fun getSessionStatsQuery(): String = """
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN STATUS = 'ACTIVE' THEN 1 ELSE 0 END) as active,
            SUM(CASE WHEN STATUS = 'INACTIVE' THEN 1 ELSE 0 END) as sleeping,
            SUM(CASE WHEN STATUS = 'ACTIVE' AND (SYSDATE - LOGON_TIME) * 24 * 60 > 1 THEN 1 ELSE 0 END) as long_running
        FROM V${'$'}SESSION
        WHERE TYPE = 'USER'
        AND SID != SYS_CONTEXT('USERENV', 'SID')
    """.trimIndent()

    override fun getLongRunningQueriesQuery(): String = """
        SELECT
            s.SID as pid,
            s.SERIAL# as serial_num,
            s.USERNAME as sess_user,
            s.MACHINE as host,
            s.SCHEMANAME as database_name,
            s.STATUS as command,
            ROUND((SYSDATE - s.LOGON_TIME) * 24 * 60 * 60) as time,
            s.STATE as state,
            q.SQL_TEXT as query
        FROM V${'$'}SESSION s
        LEFT JOIN V${'$'}SQLAREA q ON s.SQL_ID = q.SQL_ID
        WHERE s.TYPE = 'USER'
        AND s.SID != SYS_CONTEXT('USERENV', 'SID')
        AND s.STATUS = 'ACTIVE'
        AND (SYSDATE - s.LOGON_TIME) * 24 * 60 * 60 > ?
        ORDER BY s.LOGON_TIME ASC
    """.trimIndent()

    override fun getKillSessionSql(pid: Long, serialNum: Long?): String {
        // Oracle requires both SID and SERIAL# to kill a session
        requireNotNull(serialNum) { "Oracle requires SERIAL# to kill a session" }
        return "ALTER SYSTEM KILL SESSION '$pid,$serialNum' IMMEDIATE"
    }

    override fun getKillQuerySql(pid: Long, serialNum: Long?): String {
        // Oracle requires both SID and SERIAL# to cancel a query
        requireNotNull(serialNum) { "Oracle requires SERIAL# to cancel a query" }
        return "ALTER SYSTEM CANCEL SQL '$pid,$serialNum'"
    }

    // ==================== Account Queries ====================

    override fun getAllAccountsQuery(): String = """
        SELECT
            USERNAME as username,
            'localhost' as host,
            TO_CHAR(PASSWORD_CHANGE_DATE, 'YYYY-MM-DD HH24:MI:SS') as password_last_changed,
            TRUNC(EXPIRY_DATE - PASSWORD_CHANGE_DATE) as password_lifetime,
            CASE WHEN ACCOUNT_STATUS LIKE '%LOCKED%' THEN 1 ELSE 0 END as account_locked
        FROM DBA_USERS
        WHERE USERNAME NOT IN (${getSystemUsers().joinToString { "'$it'" }})
        ORDER BY USERNAME
    """.trimIndent()

    override fun getAccountCountQuery(): String = """
        SELECT COUNT(*) as count
        FROM DBA_USERS
        WHERE USERNAME NOT IN (${getSystemUsers().joinToString { "'$it'" }})
    """.trimIndent()

    override fun getLockedAccountsWhereClause(): String = "AND ACCOUNT_STATUS LIKE '%LOCKED%'"

    override fun getExpiringAccountsWhereClause(days: Int): String =
        "AND EXPIRY_DATE IS NOT NULL AND EXPIRY_DATE <= CURRENT_DATE + $days AND ACCOUNT_STATUS NOT LIKE '%LOCKED%'"

    override fun getPaginatedAccountsQuery(orderByClause: String, filterClause: String): String {
        val orderBy = orderByClause.ifEmpty { "ORDER BY USERNAME" }
        return """
            SELECT
                USERNAME as username,
                'localhost' as host,
                TO_CHAR(PASSWORD_CHANGE_DATE, 'YYYY-MM-DD HH24:MI:SS') as password_last_changed,
                TRUNC(EXPIRY_DATE - PASSWORD_CHANGE_DATE) as password_lifetime,
                CASE WHEN ACCOUNT_STATUS LIKE '%LOCKED%' THEN 1 ELSE 0 END as account_locked
            FROM DBA_USERS
            WHERE USERNAME NOT IN (${getSystemUsers().joinToString { "'$it'" }})
            $filterClause
            $orderBy
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """.trimIndent()
    }

    override fun getOptimizedPaginatedAccountsQuery(orderByClause: String, filterClause: String): String {
        val orderBy = orderByClause.ifEmpty { "ORDER BY USERNAME" }
        return """
            SELECT * FROM (
                SELECT
                    USERNAME as username,
                    'localhost' as host,
                    TO_CHAR(PASSWORD_CHANGE_DATE, 'YYYY-MM-DD HH24:MI:SS') as password_last_changed,
                    TRUNC(EXPIRY_DATE - PASSWORD_CHANGE_DATE) as password_lifetime,
                    CASE WHEN ACCOUNT_STATUS LIKE '%LOCKED%' THEN 1 ELSE 0 END as account_locked,
                    COUNT(*) OVER() as total_count,
                    SUM(CASE WHEN ACCOUNT_STATUS LIKE '%LOCKED%' THEN 1 ELSE 0 END) OVER() as locked_count
                FROM DBA_USERS
                WHERE USERNAME NOT IN (${getSystemUsers().joinToString { "'$it'" }})
                $filterClause
                $orderBy
            )
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """.trimIndent()
    }

    override fun getCreateUserSql(username: String, host: String, password: String): String {
        // Oracle usernames should be uppercase and unquoted for standard behavior
        // Quoting makes them case-sensitive which causes issues
        val oracleUsername = formatOracleUsername(username)
        return "CREATE USER $oracleUsername IDENTIFIED BY \"${escapePassword(password)}\""
    }

    /**
     * Creates a local user in PDB.
     * In PDB context, users are automatically local users (no C## prefix needed).
     */
    fun getCreateLocalUserSql(username: String, password: String): String {
        val oracleUsername = formatOracleUsername(username)
        return "CREATE USER $oracleUsername IDENTIFIED BY \"${escapePassword(password)}\""
    }

    /**
     * Formats username for Oracle: uppercase and only quote if contains special chars.
     */
    private fun formatOracleUsername(username: String): String {
        val upperUsername = username.uppercase()
        // Only quote if contains special characters (not alphanumeric, _, $, #)
        return if (upperUsername.matches(Regex("^[A-Z][A-Z0-9_\$#]*$"))) {
            upperUsername
        } else {
            quoteIdentifier(upperUsername)
        }
    }

    /**
     * Returns SQL to check if connected to CDB root (not a PDB).
     * Returns 'CDB$ROOT' if in CDB root, PDB name otherwise.
     */
    fun getContainerNameSql(): String = "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') FROM DUAL"

    /**
     * Returns SQL to check if this is a container database.
     * Returns 'YES' if CDB, 'NO' if not.
     */
    fun getIsCdbSql(): String = "SELECT CDB FROM V\$DATABASE"

    /**
     * Formats username for CDB root - adds C## prefix if needed.
     */
    fun formatCdbUsername(username: String, isContainerRoot: Boolean): String {
        return if (isContainerRoot && !username.uppercase().startsWith("C##")) {
            "C##$username"
        } else {
            username
        }
    }

    /**
     * Removes C## prefix from username for display.
     */
    fun stripCdbPrefix(username: String): String {
        return if (username.uppercase().startsWith("C##")) {
            username.substring(3)
        } else {
            username
        }
    }

    /**
     * Returns SQL to enable local user creation in CDB root environment.
     * Should only be executed when connected to CDB$ROOT, not in a PDB.
     */
    fun getEnableLocalUserSql(): String = "ALTER SESSION SET \"_ORACLE_SCRIPT\"=true"

    /**
     * Returns SQL to disable local user creation mode.
     */
    fun getDisableLocalUserSql(): String = "ALTER SESSION SET \"_ORACLE_SCRIPT\"=false"

    override fun getAlterUserPasswordExpireSql(username: String, host: String, expireDays: Int): String {
        // Oracle uses profiles for password expiry
        // We assign a profile with the naming convention DBACCMAN_<days>D
        val profileName = "DBACCMAN_${expireDays}D"
        val oracleUsername = formatOracleUsername(username)
        return "ALTER USER $oracleUsername PROFILE $profileName"
    }

    /**
     * Returns SQL to alter user profile with optional CONTAINER=CURRENT for PDB.
     */
    fun getAlterUserProfileSql(username: String, expireDays: Int, isPdb: Boolean = false): String {
        val profileName = "DBACCMAN_${expireDays}D"
        val containerClause = if (isPdb) " CONTAINER=CURRENT" else ""
        val oracleUsername = formatOracleUsername(username)
        return "ALTER USER $oracleUsername PROFILE $profileName$containerClause"
    }

    /**
     * Returns SQL to create a profile with specific password lifetime.
     */
    fun getCreateProfileSql(expireDays: Int, isPdb: Boolean = false): String {
        val profileName = "DBACCMAN_${expireDays}D"
        val containerClause = if (isPdb) " CONTAINER=CURRENT" else ""
        return "CREATE PROFILE $profileName LIMIT PASSWORD_LIFE_TIME $expireDays$containerClause"
    }

    /**
     * Returns SQL to check if a profile exists.
     */
    fun getCheckProfileExistsSql(): String {
        return "SELECT COUNT(*) FROM DBA_PROFILES WHERE PROFILE = ? AND RESOURCE_NAME = 'PASSWORD_LIFE_TIME'"
    }

    /**
     * Returns the profile name for a given expiry days.
     */
    fun getProfileName(expireDays: Int): String = "DBACCMAN_${expireDays}D"

    override fun getAlterUserPasswordSql(username: String, host: String, newPassword: String): String {
        val oracleUsername = formatOracleUsername(username)
        return "ALTER USER $oracleUsername IDENTIFIED BY \"${escapePassword(newPassword)}\""
    }

    override fun getExpirePasswordSql(username: String, host: String): String {
        val oracleUsername = formatOracleUsername(username)
        return "ALTER USER $oracleUsername PASSWORD EXPIRE"
    }

    override fun getDropUserSql(username: String, host: String): String {
        val oracleUsername = formatOracleUsername(username)
        return "DROP USER $oracleUsername CASCADE"
    }

    override fun getUnlockAccountSql(username: String, host: String): String {
        val oracleUsername = formatOracleUsername(username)
        return "ALTER USER $oracleUsername ACCOUNT UNLOCK"
    }

    private fun escapePassword(password: String): String {
        // Escape double quotes in password for Oracle
        return password.replace("\"", "\\\"")
    }

    override fun getExpiringAccountsQuery(): String = """
        SELECT
            USERNAME as username,
            'localhost' as host,
            TRUNC(EXPIRY_DATE - CURRENT_DATE) as days_until_expiry
        FROM DBA_USERS
        WHERE EXPIRY_DATE IS NOT NULL
        AND EXPIRY_DATE > CURRENT_DATE
        AND TRUNC(EXPIRY_DATE - CURRENT_DATE) < ?
        ORDER BY EXPIRY_DATE ASC
    """.trimIndent()

    override fun getFlushPrivilegesSql(): String? = null // Oracle doesn't need flush

    override fun getSetDefaultTablespaceSql(username: String, host: String, tablespace: String): String {
        val oracleUsername = formatOracleUsername(username)
        return "ALTER USER $oracleUsername DEFAULT TABLESPACE ${quoteIdentifier(tablespace)}"
    }

    override fun getSetTablespaceQuotaSql(username: String, host: String, tablespace: String, quota: String): String {
        // quota can be "UNLIMITED" or a size like "100M", "1G"
        val oracleUsername = formatOracleUsername(username)
        return "ALTER USER $oracleUsername QUOTA $quota ON ${quoteIdentifier(tablespace)}"
    }

    // ==================== Permission Queries ====================

    override fun formatGrantee(username: String, host: String): String {
        // Oracle uses just the username (uppercase) as grantee
        return username.uppercase()
    }

    override fun getSchemaPrivilegesQuery(): String = """
        SELECT * FROM (
            SELECT
                GRANTEE as grantee,
                'SYSTEM' as db,
                PRIVILEGE as privilege,
                CASE WHEN ADMIN_OPTION = 'YES' THEN 'YES' ELSE 'NO' END as is_grantable
            FROM DBA_SYS_PRIVS
            UNION ALL
            SELECT
                GRANTEE as grantee,
                GRANTED_ROLE as db,
                'ROLE' as privilege,
                CASE WHEN ADMIN_OPTION = 'YES' THEN 'YES' ELSE 'NO' END as is_grantable
            FROM DBA_ROLE_PRIVS
        ) WHERE GRANTEE = ?
    """.trimIndent()

    override fun getTablePrivilegesQuery(): String = """
        SELECT
            GRANTEE as grantee,
            OWNER as db,
            TABLE_NAME as tbl,
            PRIVILEGE as privilege,
            CASE WHEN GRANTABLE = 'YES' THEN 'YES' ELSE 'NO' END as is_grantable
        FROM DBA_TAB_PRIVS
        WHERE GRANTEE = ?
    """.trimIndent()

    override fun getAllSchemaPrivilegesQuery(): String = """
        SELECT
            GRANTEE as grantee,
            'SYSTEM' as db,
            PRIVILEGE as privilege,
            CASE WHEN ADMIN_OPTION = 'YES' THEN 'YES' ELSE 'NO' END as is_grantable
        FROM DBA_SYS_PRIVS
        WHERE GRANTEE NOT IN (${getSystemUsers().joinToString { "'$it'" }})
        UNION ALL
        SELECT
            GRANTEE as grantee,
            GRANTED_ROLE as db,
            'ROLE' as privilege,
            CASE WHEN ADMIN_OPTION = 'YES' THEN 'YES' ELSE 'NO' END as is_grantable
        FROM DBA_ROLE_PRIVS
        WHERE GRANTEE NOT IN (${getSystemUsers().joinToString { "'$it'" }})
    """.trimIndent()

    override fun getAllTablePrivilegesQuery(): String = """
        SELECT
            GRANTEE as grantee,
            OWNER as db,
            TABLE_NAME as tbl,
            PRIVILEGE as privilege,
            CASE WHEN GRANTABLE = 'YES' THEN 'YES' ELSE 'NO' END as is_grantable
        FROM DBA_TAB_PRIVS
        WHERE GRANTEE NOT IN (${getSystemUsers().joinToString { "'$it'" }})
    """.trimIndent()

    // Oracle object privileges (can be granted on specific tables)
    private val oracleObjectPrivileges = setOf("SELECT", "INSERT", "UPDATE", "DELETE", "ALTER", "INDEX", "REFERENCES", "EXECUTE")

    // Oracle system privileges with ANY TABLE (for granting on all tables)
    private val oracleAnyTablePrivileges = setOf("SELECT", "INSERT", "UPDATE", "DELETE", "ALTER", "DROP")

    // Oracle direct system privileges (granted TO user without ON clause)
    private val oracleDirectSystemPrivileges = setOf(
        "CREATE SESSION",
        "CREATE TABLE",
        "CREATE VIEW",
        "CREATE PROCEDURE",
        "CREATE SEQUENCE",
        "CREATE TRIGGER",
        "CREATE SYNONYM",
        "CREATE TYPE",
        "UNLIMITED TABLESPACE",
        "CREATE ANY TABLE",
        "DROP ANY TABLE",
        "ALTER ANY TABLE",
        "SELECT ANY TABLE",
        "INSERT ANY TABLE",
        "UPDATE ANY TABLE",
        "DELETE ANY TABLE"
    )

    // Map MySQL privileges to Oracle equivalents for object-level grants
    private fun mapToOracleObjectPrivilege(privilege: String): String? {
        val upper = privilege.uppercase()
        return if (upper in oracleObjectPrivileges) upper else null
    }

    // Map to Oracle ANY TABLE system privileges
    private fun mapToOracleAnyTablePrivilege(privilege: String): String? {
        val upper = privilege.uppercase()
        return if (upper in oracleAnyTablePrivileges) "${upper} ANY TABLE" else null
    }

    // Check if privilege is a direct system privilege
    private fun isDirectSystemPrivilege(privilege: String): Boolean {
        return privilege.uppercase() in oracleDirectSystemPrivileges
    }

    override fun getGrantSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val oracleUsername = formatOracleUsername(username)

        // Separate direct system privileges from object/ANY TABLE privileges
        val directSystemPrivs = privileges.filter { isDirectSystemPrivilege(it) }.map { it.uppercase() }
        val otherPrivs = privileges.filterNot { isDirectSystemPrivilege(it) }

        // If all privileges are direct system privileges, grant them directly
        if (directSystemPrivs.isNotEmpty() && otherPrivs.isEmpty()) {
            return "GRANT ${directSystemPrivs.joinToString(", ")} TO $oracleUsername"
        }

        // If there are direct system privileges mixed with other privileges, combine them
        if (directSystemPrivs.isNotEmpty() && otherPrivs.isNotEmpty()) {
            // This case shouldn't happen with proper UI, but handle it gracefully
            // Grant system privileges separately from object privileges
            val systemGrant = "GRANT ${directSystemPrivs.joinToString(", ")} TO $oracleUsername"
            if (table == "*") {
                val validPrivileges = otherPrivs.mapNotNull { mapToOracleAnyTablePrivilege(it) }.distinct()
                return if (validPrivileges.isNotEmpty()) {
                    "$systemGrant; GRANT ${validPrivileges.joinToString(", ")} TO $oracleUsername"
                } else {
                    systemGrant
                }
            } else {
                val validPrivileges = otherPrivs.mapNotNull { mapToOracleObjectPrivilege(it) }.distinct()
                return if (validPrivileges.isNotEmpty()) {
                    "$systemGrant; GRANT ${validPrivileges.joinToString(", ")} ON ${quoteIdentifier(database)}.${quoteIdentifier(table)} TO $oracleUsername"
                } else {
                    systemGrant
                }
            }
        }

        // No direct system privileges - handle object/ANY TABLE privileges
        return if (table == "*") {
            // ANY TABLE system privileges: GRANT SELECT ANY TABLE, INSERT ANY TABLE TO user
            val validPrivileges = privileges.mapNotNull { mapToOracleAnyTablePrivilege(it) }.distinct()
            if (validPrivileges.isEmpty()) {
                throw IllegalArgumentException("No valid Oracle system privileges specified. Valid: SELECT, INSERT, UPDATE, DELETE, ALTER, DROP")
            }
            "GRANT ${validPrivileges.joinToString(", ")} TO $oracleUsername"
        } else {
            // Object privileges: GRANT SELECT, INSERT ON schema.table TO user
            val validPrivileges = privileges.mapNotNull { mapToOracleObjectPrivilege(it) }.distinct()
            if (validPrivileges.isEmpty()) {
                throw IllegalArgumentException("No valid Oracle object privileges specified. Valid: SELECT, INSERT, UPDATE, DELETE, ALTER, INDEX, REFERENCES, EXECUTE")
            }
            "GRANT ${validPrivileges.joinToString(", ")} ON ${quoteIdentifier(database)}.${quoteIdentifier(table)} TO $oracleUsername"
        }
    }

    override fun getRevokeSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val oracleUsername = formatOracleUsername(username)

        // Separate direct system privileges from object/ANY TABLE privileges
        val directSystemPrivs = privileges.filter { isDirectSystemPrivilege(it) }.map { it.uppercase() }
        val otherPrivs = privileges.filterNot { isDirectSystemPrivilege(it) }

        // If all privileges are direct system privileges, revoke them directly
        if (directSystemPrivs.isNotEmpty() && otherPrivs.isEmpty()) {
            return "REVOKE ${directSystemPrivs.joinToString(", ")} FROM $oracleUsername"
        }

        // If there are direct system privileges mixed with other privileges, combine them
        if (directSystemPrivs.isNotEmpty() && otherPrivs.isNotEmpty()) {
            val systemRevoke = "REVOKE ${directSystemPrivs.joinToString(", ")} FROM $oracleUsername"
            if (table == "*") {
                val validPrivileges = otherPrivs.mapNotNull { mapToOracleAnyTablePrivilege(it) }.distinct()
                return if (validPrivileges.isNotEmpty()) {
                    "$systemRevoke; REVOKE ${validPrivileges.joinToString(", ")} FROM $oracleUsername"
                } else {
                    systemRevoke
                }
            } else {
                val validPrivileges = otherPrivs.mapNotNull { mapToOracleObjectPrivilege(it) }.distinct()
                return if (validPrivileges.isNotEmpty()) {
                    "$systemRevoke; REVOKE ${validPrivileges.joinToString(", ")} ON ${quoteIdentifier(database)}.${quoteIdentifier(table)} FROM $oracleUsername"
                } else {
                    systemRevoke
                }
            }
        }

        // No direct system privileges - handle object/ANY TABLE privileges
        return if (table == "*") {
            // ANY TABLE system privileges: REVOKE SELECT ANY TABLE, INSERT ANY TABLE FROM user
            val validPrivileges = privileges.mapNotNull { mapToOracleAnyTablePrivilege(it) }.distinct()
            if (validPrivileges.isEmpty()) {
                throw IllegalArgumentException("No valid Oracle system privileges specified")
            }
            "REVOKE ${validPrivileges.joinToString(", ")} FROM $oracleUsername"
        } else {
            // Object privileges: REVOKE SELECT, INSERT ON schema.table FROM user
            val validPrivileges = privileges.mapNotNull { mapToOracleObjectPrivilege(it) }.distinct()
            if (validPrivileges.isEmpty()) {
                throw IllegalArgumentException("No valid Oracle object privileges specified")
            }
            "REVOKE ${validPrivileges.joinToString(", ")} ON ${quoteIdentifier(database)}.${quoteIdentifier(table)} FROM $oracleUsername"
        }
    }

    override fun getShowDatabasesQuery(): String = """
        SELECT USERNAME FROM DBA_USERS WHERE USERNAME NOT IN (${getSystemSchemas().joinToString { "'$it'" }})
    """.trimIndent()

    override fun getAdminCheckQuery(): String = """
        SELECT GRANTED_ROLE FROM USER_ROLE_PRIVS WHERE GRANTED_ROLE = 'DBA'
    """.trimIndent()

    // ==================== Table/Database Queries ====================

    override fun getDatabasesQuery(): String = """
        SELECT
            u.USERNAME as name,
            NVL(t.table_count, 0) as table_count,
            NVL(t.total_rows, 0) as total_rows,
            NVL(s.total_size, 0) as total_size
        FROM DBA_USERS u
        LEFT JOIN (
            SELECT OWNER,
                   COUNT(*) as table_count,
                   SUM(NVL(NUM_ROWS, 0)) as total_rows
            FROM DBA_TABLES
            GROUP BY OWNER
        ) t ON u.USERNAME = t.OWNER
        LEFT JOIN (
            SELECT OWNER, SUM(BYTES) as total_size
            FROM DBA_SEGMENTS
            WHERE SEGMENT_TYPE = 'TABLE'
            GROUP BY OWNER
        ) s ON u.USERNAME = s.OWNER
        WHERE u.USERNAME NOT IN (${getSystemUsers().joinToString { "'$it'" }})
        ORDER BY u.USERNAME
    """.trimIndent()

    override fun getTablesQuery(): String = """
        SELECT
            t.TABLE_NAME as name,
            'Oracle' as engine,
            NVL(t.NUM_ROWS, 0) as row_count,
            NVL(s.BYTES, 0) as table_size,
            NVL(TO_CHAR(t.LAST_ANALYZED, 'YYYY-MM-DD HH24:MI:SS'), '') as create_time
        FROM DBA_TABLES t
        LEFT JOIN DBA_SEGMENTS s ON t.OWNER = s.OWNER AND t.TABLE_NAME = s.SEGMENT_NAME AND s.SEGMENT_TYPE = 'TABLE'
        WHERE t.OWNER = UPPER(?)
        ORDER BY t.TABLE_NAME
    """.trimIndent()

    override fun getTableColumnsQuery(): String = """
        SELECT
            COLUMN_NAME as name,
            DATA_TYPE as type,
            CASE WHEN NULLABLE = 'Y' THEN 1 ELSE 0 END as nullable,
            '' as col_key,
            DATA_DEFAULT as default_value,
            '' as extra
        FROM ALL_TAB_COLUMNS
        WHERE OWNER = UPPER(?)
        AND TABLE_NAME = UPPER(?)
        ORDER BY COLUMN_ID
    """.trimIndent()

    override fun getIndexesQuery(): String = """
        SELECT
            i.INDEX_NAME as name,
            LISTAGG(ic.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY ic.COLUMN_POSITION) as columns,
            CASE WHEN i.UNIQUENESS = 'UNIQUE' THEN 1 ELSE 0 END as is_unique,
            i.INDEX_TYPE as type
        FROM ALL_IND_COLUMNS ic
        JOIN ALL_INDEXES i ON ic.INDEX_NAME = i.INDEX_NAME AND ic.INDEX_OWNER = i.OWNER
        WHERE i.TABLE_OWNER = UPPER(?)
        AND ic.TABLE_NAME = UPPER(?)
        GROUP BY i.INDEX_NAME, i.UNIQUENESS, i.INDEX_TYPE
        ORDER BY i.INDEX_NAME
    """.trimIndent()

    override fun getCreateIndexSql(database: String, table: String, indexName: String, columns: List<String>, unique: Boolean): String {
        // Oracle: database parameter is tablespace name, not schema
        // USER_TABLES only shows current user's tables, so no schema prefix needed
        val cols = columns.joinToString(", ") { quoteIdentifier(it) }
        val uniqueKeyword = if (unique) "UNIQUE " else ""
        return "CREATE ${uniqueKeyword}INDEX ${quoteIdentifier(indexName)} ON ${quoteIdentifier(table)} ($cols)"
    }

    override fun getDropIndexSql(database: String, table: String, indexName: String): String {
        // Oracle: database parameter is tablespace name, not schema
        // indexName may already contain owner prefix (e.g., "OWNER.INDEX_NAME")
        // For current user's indexes, no schema prefix needed
        return if (indexName.contains(".")) {
            val parts = indexName.split(".", limit = 2)
            "DROP INDEX ${quoteIdentifier(parts[0])}.${quoteIdentifier(parts[1])}"
        } else {
            "DROP INDEX ${quoteIdentifier(indexName)}"
        }
    }

    override fun getSelectWithLimitSql(quotedTable: String, limit: Int): String {
        // Oracle 12c+ supports FETCH FIRST syntax
        return "SELECT * FROM $quotedTable FETCH FIRST $limit ROWS ONLY"
    }

    override fun getGatherStatsSql(schema: String, table: String?): String? {
        // Minimal stats gathering to avoid system overload:
        // - estimate_percent => 1: Only 1% sample
        // - degree => 1: No parallelism (single thread)
        // - method_opt => 'FOR ALL COLUMNS SIZE 1': Skip histogram collection
        // - no_invalidate => TRUE: Don't invalidate cursors
        // - options => 'GATHER AUTO': Only gather stale stats
        return if (table != null) {
            """BEGIN DBMS_STATS.GATHER_TABLE_STATS(
                ownname => '${schema.uppercase()}',
                tabname => '${table.uppercase()}',
                estimate_percent => 1,
                degree => 1,
                method_opt => 'FOR ALL COLUMNS SIZE 1',
                no_invalidate => TRUE
            ); END;""".replace("\n", " ")
        } else {
            """BEGIN DBMS_STATS.GATHER_SCHEMA_STATS(
                ownname => '${schema.uppercase()}',
                estimate_percent => 1,
                degree => 1,
                method_opt => 'FOR ALL COLUMNS SIZE 1',
                no_invalidate => TRUE,
                options => 'GATHER AUTO'
            ); END;""".replace("\n", " ")
        }
    }

    // ==================== Tablespace Queries ====================

    override fun getTablespacesQuery(): String = """
        SELECT
            t.TABLESPACE_NAME as name,
            t.CONTENTS as space_type,
            NVL(df.file_size, 0) as file_size,
            NVL(df.file_size - fs.free_size, 0) as allocated_size,
            t.STATUS as state
        FROM DBA_TABLESPACES t
        LEFT JOIN (
            SELECT TABLESPACE_NAME, SUM(BYTES) as file_size
            FROM DBA_DATA_FILES
            GROUP BY TABLESPACE_NAME
        ) df ON t.TABLESPACE_NAME = df.TABLESPACE_NAME
        LEFT JOIN (
            SELECT TABLESPACE_NAME, SUM(BYTES) as free_size
            FROM DBA_FREE_SPACE
            GROUP BY TABLESPACE_NAME
        ) fs ON t.TABLESPACE_NAME = fs.TABLESPACE_NAME
        ORDER BY t.TABLESPACE_NAME
    """.trimIndent()

    override fun getCreateTablespaceSql(name: String, dataFile: String?, engine: String?): String {
        val file = dataFile ?: "${name.lowercase()}.dbf"
        return "CREATE TABLESPACE ${quoteIdentifier(name)} DATAFILE '$file' SIZE 100M AUTOEXTEND ON"
    }

    override fun getDropTablespaceSql(name: String): String {
        return "DROP TABLESPACE ${quoteIdentifier(name)} INCLUDING CONTENTS AND DATAFILES"
    }

    override fun getTablesInTablespaceQuery(): String = """
        SELECT
            t.OWNER as db_name,
            t.TABLE_NAME as table_name,
            'Oracle' as engine,
            NVL(t.NUM_ROWS, 0) as "rows",
            NVL(s.BYTES, 0) as "size",
            NVL(TO_CHAR(t.LAST_ANALYZED, 'YYYY-MM-DD HH24:MI:SS'), '') as create_time
        FROM DBA_TABLES t
        LEFT JOIN DBA_SEGMENTS s ON t.OWNER = s.OWNER AND t.TABLE_NAME = s.SEGMENT_NAME AND s.SEGMENT_TYPE = 'TABLE'
        WHERE t.TABLESPACE_NAME = UPPER(?)
        ORDER BY t.OWNER, t.TABLE_NAME
    """.trimIndent()

    override fun getMoveTableToTablespaceSql(database: String, tableName: String, tablespaceName: String): String {
        // Oracle: database parameter is actually tablespace name (from getDatabasesQuery), not schema
        // USER_TABLES only shows current user's tables, so no schema prefix needed
        return "ALTER TABLE ${quoteIdentifier(tableName)} MOVE TABLESPACE ${quoteIdentifier(tablespaceName)}"
    }

    // ==================== Password Expiry Queries ====================

    override fun getPasswordExpiryQuery(): String = """
        SELECT
            USERNAME as "user",
            'localhost' as host,
            TRUNC(EXPIRY_DATE - PASSWORD_CHANGE_DATE) as password_lifetime,
            TO_CHAR(PASSWORD_CHANGE_DATE, 'YYYY-MM-DD HH24:MI:SS') as password_last_changed,
            CASE WHEN ACCOUNT_STATUS LIKE '%EXPIRED%' THEN 1 ELSE 0 END as is_expired,
            TRUNC(EXPIRY_DATE - CURRENT_DATE) as days_until_expiry
        FROM DBA_USERS
        WHERE USERNAME = UPPER(?)
        AND NVL(?, 'localhost') IS NOT NULL
    """.trimIndent()

    /**
     * Returns password expiry query for regular users (uses USER_USERS instead of DBA_USERS).
     */
    fun getMyPasswordExpiryQuery(): String = """
        SELECT
            USERNAME as "user",
            'localhost' as host,
            TRUNC(EXPIRY_DATE - PASSWORD_CHANGE_DATE) as password_lifetime,
            TO_CHAR(PASSWORD_CHANGE_DATE, 'YYYY-MM-DD HH24:MI:SS') as password_last_changed,
            CASE WHEN ACCOUNT_STATUS LIKE '%EXPIRED%' THEN 1 ELSE 0 END as is_expired,
            TRUNC(EXPIRY_DATE - CURRENT_DATE) as days_until_expiry
        FROM USER_USERS
        WHERE NVL(?, 'localhost') IS NOT NULL
    """.trimIndent()

    override fun getPasswordExpiryDaysQuery(): String = """
        SELECT
            TRUNC(EXPIRY_DATE - CURRENT_DATE) as days_until_expiry
        FROM DBA_USERS
        WHERE USERNAME = UPPER(?)
    """.trimIndent()

    // ==================== System Schema Filter ====================

    override fun getSystemSchemas(): List<String> = listOf(
        "SYSTEM",
        "SYS",
        "SYSAUX",
        "UNDOTBS1",
        "TEMP",
        "USERS",
        "DBFS_DATA",
        "APEX_040200",
        "APEX_PUBLIC_USER"
    )

    override fun getSystemUsers(): List<String> = listOf(
        // Core Oracle system accounts
        "SYS",
        "SYSTEM",
        "DBSNMP",
        "SYSMAN",
        "OUTLN",
        // Metadata and schema management
        "MDSYS",
        "ORDSYS",
        "EXFSYS",
        "WMSYS",
        "CTXSYS",
        "OLAPSYS",
        "LBACSYS",
        "DVSYS",
        "DVF",
        "AUDSYS",
        // Application and web services
        "APPQOSSYS",
        "APEX_PUBLIC_USER",
        "APEX_040200",
        "APEX_050000",
        "APEX_INSTANCE_ADMIN_USER",
        "APEX_LISTENER",
        "APEX_REST_PUBLIC_USER",
        "ORDS_PUBLIC_USER",
        "ORDS_METADATA",
        "FLOWS_FILES",
        // Internal Oracle accounts
        "DIP",
        "ANONYMOUS",
        "XDB",
        "XS${'$'}NULL",
        "ORACLE_OCM",
        // Global data services
        "GSMADMIN_INTERNAL",
        "GSMCATUSER",
        "GSMUSER",
        "GGSYS",
        // Security and backup
        "DBSFWUSER",
        "REMOTE_SCHEDULER_AGENT",
        "SYSBACKUP",
        "SYSDG",
        "SYSKM",
        "SYSRAC",
        "SYS${'$'}UMF",
        // Java and development
        "OJVMSYS",
        "SI_INFORMTN_SCHEMA",
        "ORDDATA",
        "ORDPLUGINS",
        // Spatial and multimedia
        "SPATIAL_CSW_ADMIN_USR",
        "SPATIAL_WFS_ADMIN_USR",
        // CDB/PDB management
        "PDBADMIN",
        "C##CLOUD${'$'}SERVICE",
        // Additional Oracle internal accounts
        "MDDATA",
        "SCOTT",
        "HR",
        "OE",
        "PM",
        "IX",
        "SH",
        "BI"
    )

    // ==================== Schema/User Context ====================

    // Oracle: For SQL Console, we DO want to switch schema context
    // Note: Don't quote the schema name - Oracle schemas are typically uppercase
    // and quoting makes it case-sensitive which would fail for "myschema" vs MYSCHEMA
    override fun getSwitchSchemaSql(schema: String): String {
        return "ALTER SESSION SET CURRENT_SCHEMA = ${schema.uppercase()}"
    }

    override fun getCurrentSchemaQuery(): String =
        "SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS current_schema FROM DUAL"

    override fun getAvailableSchemasQuery(): String = """
        SELECT u.USERNAME as schema_name
        FROM ALL_USERS u
        INNER JOIN DBA_USERS d ON u.USERNAME = d.USERNAME
        WHERE u.USERNAME NOT IN (${getSystemUsers().joinToString { "'$it'" }})
        AND d.ACCOUNT_STATUS = 'OPEN'
        ORDER BY u.USERNAME
    """.trimIndent()

    // ==================== Role Management ====================

    /**
     * Returns all available roles in the database.
     */
    fun getAllRolesQuery(): String = """
        SELECT
            ROLE as name,
            CASE WHEN ROLE IN ('DBA', 'SYSDBA', 'SYSOPER', 'SYSBACKUP', 'SYSDG', 'SYSKM', 'SYSRAC')
                 THEN 1 ELSE 0 END as is_admin
        FROM DBA_ROLES
        ORDER BY ROLE
    """.trimIndent()

    /**
     * Returns roles granted to a specific user.
     */
    fun getUserRolesQuery(): String = """
        SELECT
            GRANTEE as username,
            GRANTED_ROLE as role_name,
            CASE WHEN DEFAULT_ROLE = 'YES' THEN 1 ELSE 0 END as is_default,
            CASE WHEN ADMIN_OPTION = 'YES' THEN 1 ELSE 0 END as is_admin
        FROM DBA_ROLE_PRIVS
        WHERE GRANTEE = UPPER(?)
        ORDER BY GRANTED_ROLE
    """.trimIndent()

    /**
     * Returns SQL to grant a role to a user.
     */
    fun getGrantRoleSql(username: String, role: String, withAdminOption: Boolean = false): String {
        val adminOption = if (withAdminOption) " WITH ADMIN OPTION" else ""
        val oracleUsername = formatOracleUsername(username)
        val oracleRole = role.uppercase()  // Roles are also uppercase in Oracle
        return "GRANT $oracleRole TO $oracleUsername$adminOption"
    }

    /**
     * Returns SQL to revoke a role from a user.
     */
    fun getRevokeRoleSql(username: String, role: String): String {
        val oracleUsername = formatOracleUsername(username)
        val oracleRole = role.uppercase()
        return "REVOKE $oracleRole FROM $oracleUsername"
    }

    /**
     * Returns common roles that are safe to grant.
     */
    fun getCommonRoles(): List<String> = listOf(
        "CONNECT",
        "RESOURCE",
        "DBA",
        "EXP_FULL_DATABASE",
        "IMP_FULL_DATABASE",
        "SELECT_CATALOG_ROLE",
        "EXECUTE_CATALOG_ROLE",
        "DELETE_CATALOG_ROLE",
        "RECOVERY_CATALOG_OWNER"
    )

    // ==================== PDB Management ====================

    /**
     * Returns all PDBs in the CDB.
     */
    fun getPdbListQuery(): String = """
        SELECT
            NAME as name,
            OPEN_MODE as open_mode,
            CASE WHEN RESTRICTED = 'YES' THEN 1 ELSE 0 END as restricted
        FROM V${'$'}PDBS
        ORDER BY NAME
    """.trimIndent()

    /**
     * Returns SQL to switch to a specific PDB.
     */
    fun getSwitchPdbSql(pdbName: String): String {
        return "ALTER SESSION SET CONTAINER = ${pdbName.uppercase()}"
    }

    /**
     * Returns SQL to get current container name.
     */
    fun getCurrentContainerQuery(): String =
        "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') as container_name FROM DUAL"

    /**
     * Returns SQL to check if current database is a CDB.
     */
    fun getIsCdbQuery(): String = "SELECT CDB FROM V\$DATABASE"

    // ==================== User-specific Queries (Own Schema Only) ====================

    /**
     * Returns tables owned by the current user (using USER_TABLES).
     */
    fun getMyTablesQuery(): String = """
        SELECT
            USER as schema_name,
            TABLE_NAME as table_name,
            NUM_ROWS as row_count,
            TO_CHAR(LAST_ANALYZED, 'YYYY-MM-DD HH24:MI:SS') as last_analyzed,
            TABLESPACE_NAME as tablespace_name
        FROM USER_TABLES
        ORDER BY TABLE_NAME
    """.trimIndent()

    /**
     * Returns columns for a table owned by the current user.
     */
    fun getMyTableColumnsQuery(): String = """
        SELECT
            COLUMN_NAME as column_name,
            DATA_TYPE ||
                CASE
                    WHEN DATA_TYPE IN ('VARCHAR2', 'CHAR', 'NVARCHAR2', 'NCHAR') THEN '(' || DATA_LENGTH || ')'
                    WHEN DATA_TYPE = 'NUMBER' AND DATA_PRECISION IS NOT NULL THEN '(' || DATA_PRECISION || ',' || NVL(DATA_SCALE, 0) || ')'
                    ELSE ''
                END as data_type,
            NULLABLE as is_nullable,
            COLUMN_ID as ordinal_position,
            DATA_DEFAULT as column_default
        FROM USER_TAB_COLUMNS
        WHERE TABLE_NAME = UPPER(?)
        ORDER BY COLUMN_ID
    """.trimIndent()

    /**
     * Returns indexes for a table owned by the current user.
     */
    fun getMyTableIndexesQuery(): String = """
        SELECT
            i.INDEX_NAME as index_name,
            i.INDEX_TYPE as index_type,
            CASE WHEN i.UNIQUENESS = 'UNIQUE' THEN 1 ELSE 0 END as is_unique,
            LISTAGG(c.COLUMN_NAME, ', ') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) as columns
        FROM USER_INDEXES i
        LEFT JOIN USER_IND_COLUMNS c ON i.INDEX_NAME = c.INDEX_NAME
        WHERE i.TABLE_NAME = UPPER(?)
        GROUP BY i.INDEX_NAME, i.INDEX_TYPE, i.UNIQUENESS
        ORDER BY i.INDEX_NAME
    """.trimIndent()

    /**
     * Returns tablespace quotas for the current user.
     * Includes: explicit quotas, default tablespace, and temporary tablespace.
     */
    fun getMyTablespacesQuery(): String = """
        SELECT name, max_bytes, used_bytes FROM (
            -- Explicit quotas from USER_TS_QUOTAS
            SELECT
                TABLESPACE_NAME as name,
                CASE WHEN MAX_BYTES = -1 THEN 'UNLIMITED' ELSE TO_CHAR(MAX_BYTES) END as max_bytes,
                BYTES as used_bytes
            FROM USER_TS_QUOTAS
            UNION
            -- Default tablespace (even without explicit quota)
            SELECT
                u.DEFAULT_TABLESPACE as name,
                'UNLIMITED' as max_bytes,
                NVL((SELECT SUM(BYTES) FROM USER_SEGMENTS WHERE TABLESPACE_NAME = u.DEFAULT_TABLESPACE), 0) as used_bytes
            FROM USER_USERS u
            WHERE NOT EXISTS (SELECT 1 FROM USER_TS_QUOTAS q WHERE q.TABLESPACE_NAME = u.DEFAULT_TABLESPACE)
            UNION
            -- Temporary tablespace
            SELECT
                u.TEMPORARY_TABLESPACE as name,
                'TEMP' as max_bytes,
                0 as used_bytes
            FROM USER_USERS u
        )
        ORDER BY name
    """.trimIndent()

    /**
     * Returns the current user's default tablespace.
     */
    fun getMyDefaultTablespaceQuery(): String = """
        SELECT DEFAULT_TABLESPACE, TEMPORARY_TABLESPACE
        FROM USER_USERS
    """.trimIndent()

    /**
     * Returns tables in a specific tablespace owned by the current user.
     */
    fun getMyTablesInTablespaceQuery(): String = """
        SELECT
            USER as db_name,
            t.TABLE_NAME as table_name,
            'Oracle' as engine,
            NVL(t.NUM_ROWS, 0) as "rows",
            NVL(s.BYTES, 0) as "size",
            NVL(TO_CHAR(t.LAST_ANALYZED, 'YYYY-MM-DD HH24:MI:SS'), '') as create_time
        FROM USER_TABLES t
        LEFT JOIN USER_SEGMENTS s ON t.TABLE_NAME = s.SEGMENT_NAME AND s.SEGMENT_TYPE = 'TABLE'
        WHERE t.TABLESPACE_NAME = UPPER(?)
        ORDER BY t.TABLE_NAME
    """.trimIndent()
}