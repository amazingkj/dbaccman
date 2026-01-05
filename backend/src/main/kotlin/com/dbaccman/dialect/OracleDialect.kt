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
        LEFT JOIN V${'$'}SQL q ON s.SQL_ID = q.SQL_ID
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
        LEFT JOIN V${'$'}SQL q ON s.SQL_ID = q.SQL_ID
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

    override fun getPaginatedAccountsQuery(): String = """
        SELECT
            USERNAME as username,
            'localhost' as host,
            TO_CHAR(PASSWORD_CHANGE_DATE, 'YYYY-MM-DD HH24:MI:SS') as password_last_changed,
            TRUNC(EXPIRY_DATE - PASSWORD_CHANGE_DATE) as password_lifetime,
            CASE WHEN ACCOUNT_STATUS LIKE '%LOCKED%' THEN 1 ELSE 0 END as account_locked
        FROM DBA_USERS
        WHERE USERNAME NOT IN (${getSystemUsers().joinToString { "'$it'" }})
        ORDER BY USERNAME
        OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
    """.trimIndent()

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
            TRUNC(EXPIRY_DATE - SYSDATE) as days_until_expiry
        FROM DBA_USERS
        WHERE EXPIRY_DATE IS NOT NULL
        AND EXPIRY_DATE > SYSDATE
        AND TRUNC(EXPIRY_DATE - SYSDATE) < ?
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

    override fun getGrantSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val privList = privileges.joinToString(", ")
        val target = if (table == "*") {
            "ANY TABLE"
        } else {
            "${quoteIdentifier(database)}.${quoteIdentifier(table)}"
        }
        val oracleUsername = formatOracleUsername(username)
        return "GRANT $privList ON $target TO $oracleUsername"
    }

    override fun getRevokeSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val privList = privileges.joinToString(", ")
        val target = if (table == "*") {
            "ANY TABLE"
        } else {
            "${quoteIdentifier(database)}.${quoteIdentifier(table)}"
        }
        val oracleUsername = formatOracleUsername(username)
        return "REVOKE $privList ON $target FROM $oracleUsername"
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
            NVL(t.total_size, 0) as "size"
        FROM ALL_USERS u
        LEFT JOIN (
            SELECT OWNER,
                   COUNT(*) as table_count,
                   SUM(NVL(NUM_ROWS, 0)) as total_rows,
                   SUM(NVL(BLOCKS, 0) * 8192) as total_size
            FROM ALL_TABLES
            GROUP BY OWNER
        ) t ON u.USERNAME = t.OWNER
        WHERE u.USERNAME NOT IN (${getSystemUsers().joinToString { "'$it'" }})
        ORDER BY u.USERNAME
    """.trimIndent()

    override fun getTablesQuery(): String = """
        SELECT
            TABLE_NAME as name,
            'Oracle' as engine,
            NVL(NUM_ROWS, 0) as "rows",
            NVL(BLOCKS * 8192, 0) as "size",
            NVL(TO_CHAR(LAST_ANALYZED, 'YYYY-MM-DD HH24:MI:SS'), '') as create_time
        FROM ALL_TABLES
        WHERE OWNER = UPPER(?)
        ORDER BY TABLE_NAME
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
            TRUNC(EXPIRY_DATE - SYSDATE) as days_until_expiry
        FROM DBA_USERS
        WHERE USERNAME = UPPER(?)
        AND NVL(?, 'localhost') IS NOT NULL
    """.trimIndent()

    override fun getPasswordExpiryDaysQuery(): String = """
        SELECT
            TRUNC(EXPIRY_DATE - SYSDATE) as days_until_expiry
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
        "SYS",
        "SYSTEM",
        "DBSNMP",
        "SYSMAN",
        "OUTLN",
        "MDSYS",
        "ORDSYS",
        "EXFSYS",
        "WMSYS",
        "APPQOSSYS",
        "APEX_PUBLIC_USER",
        "DIP",
        "ANONYMOUS",
        "XDB",
        "XS${'$'}NULL",
        "ORACLE_OCM",
        "GSMADMIN_INTERNAL",
        "GSMCATUSER",
        "GSMUSER",
        "LBACSYS",
        "OLAPSYS",
        "CTXSYS",
        "DVSYS",
        "DVF",
        "AUDSYS",
        "DBSFWUSER",
        "GGSYS",
        "REMOTE_SCHEDULER_AGENT",
        "SYSBACKUP",
        "SYSDG",
        "SYSKM",
        "SYSRAC",
        "SYS${'$'}UMF",
        "OJVMSYS",
        "SI_INFORMTN_SCHEMA",
        "ORDDATA",
        "ORDPLUGINS"
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
}