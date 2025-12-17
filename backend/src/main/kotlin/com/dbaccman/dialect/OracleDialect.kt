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
            s.USERNAME as "user",
            s.MACHINE as host,
            s.SCHEMANAME as database_name,
            s.STATUS as command,
            ROUND((SYSDATE - s.LOGON_TIME) * 24 * 60 * 60) as time,
            s.STATE as state,
            q.SQL_TEXT as query
        FROM V${'$'}SESSION s
        LEFT JOIN V${'$'}SQL q ON s.SQL_ID = q.SQL_ID
        WHERE s.TYPE = 'USER'
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
    """.trimIndent()

    override fun getLongRunningQueriesQuery(): String = """
        SELECT
            s.SID as pid,
            s.USERNAME as "user",
            s.MACHINE as host,
            s.SCHEMANAME as database_name,
            s.STATUS as command,
            ROUND((SYSDATE - s.LOGON_TIME) * 24 * 60 * 60) as time,
            s.STATE as state,
            q.SQL_TEXT as query
        FROM V${'$'}SESSION s
        LEFT JOIN V${'$'}SQL q ON s.SQL_ID = q.SQL_ID
        WHERE s.TYPE = 'USER'
        AND s.STATUS = 'ACTIVE'
        AND (SYSDATE - s.LOGON_TIME) * 24 * 60 * 60 > ?
        ORDER BY s.LOGON_TIME ASC
    """.trimIndent()

    override fun getKillSessionSql(pid: Long): String {
        // Note: In Oracle, we need SID and SERIAL# to kill a session
        // This is a simplified version - actual implementation may need adjustment
        return "ALTER SYSTEM KILL SESSION '$pid,@serial#' IMMEDIATE"
    }

    override fun getKillQuerySql(pid: Long): String {
        return "ALTER SYSTEM CANCEL SQL '$pid,@serial#'"
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

    override fun getCreateUserSql(username: String, host: String, password: String): String {
        return "CREATE USER ? IDENTIFIED BY ?"
    }

    override fun getAlterUserPasswordExpireSql(username: String, host: String, expireDays: Int): String {
        // Oracle uses profiles for password expiry
        return "ALTER USER ? PROFILE DEFAULT"
    }

    override fun getAlterUserPasswordSql(username: String, host: String, newPassword: String): String {
        return "ALTER USER ? IDENTIFIED BY ?"
    }

    override fun getExpirePasswordSql(username: String, host: String): String {
        return "ALTER USER ? PASSWORD EXPIRE"
    }

    override fun getDropUserSql(username: String, host: String): String {
        return "DROP USER ? CASCADE"
    }

    override fun getUnlockAccountSql(username: String, host: String): String {
        return "ALTER USER ? ACCOUNT UNLOCK"
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

    // ==================== Permission Queries ====================

    override fun getSchemaPrivilegesQuery(): String = """
        SELECT
            GRANTEE as grantee,
            OWNER as db,
            PRIVILEGE as privilege,
            CASE WHEN GRANTABLE = 'YES' THEN 'YES' ELSE 'NO' END as is_grantable
        FROM DBA_TAB_PRIVS
        WHERE GRANTEE = ?
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
        return if (table == "*") {
            "GRANT $privList ON $target TO ?"
        } else {
            "GRANT $privList ON $target TO ?"
        }
    }

    override fun getRevokeSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val privList = privileges.joinToString(", ")
        val target = if (table == "*") {
            "ANY TABLE"
        } else {
            "${quoteIdentifier(database)}.${quoteIdentifier(table)}"
        }
        return if (table == "*") {
            "REVOKE $privList ON $target FROM ?"
        } else {
            "REVOKE $privList ON $target FROM ?"
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
            t.TABLESPACE_NAME as name,
            0 as table_count,
            NVL((SELECT SUM(BYTES) FROM DBA_DATA_FILES df WHERE df.TABLESPACE_NAME = t.TABLESPACE_NAME), 0) as "size"
        FROM DBA_TABLESPACES t
        WHERE t.TABLESPACE_NAME NOT IN (${getSystemSchemas().joinToString { "'$it'" }})
        ORDER BY t.TABLESPACE_NAME
    """.trimIndent()

    override fun getTablesQuery(): String = """
        SELECT
            TABLE_NAME as name,
            'Oracle' as engine,
            NUM_ROWS as "rows",
            BYTES as "size",
            TO_CHAR(CREATED, 'YYYY-MM-DD HH24:MI:SS') as create_time
        FROM (
            SELECT t.TABLE_NAME, t.NUM_ROWS, s.BYTES, o.CREATED
            FROM DBA_TABLES t
            LEFT JOIN DBA_SEGMENTS s ON t.TABLE_NAME = s.SEGMENT_NAME AND t.OWNER = s.OWNER
            LEFT JOIN DBA_OBJECTS o ON t.TABLE_NAME = o.OBJECT_NAME AND t.OWNER = o.OWNER AND o.OBJECT_TYPE = 'TABLE'
            WHERE t.OWNER = UPPER(?)
        )
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
        FROM DBA_TAB_COLUMNS
        WHERE OWNER = UPPER(?)
        AND TABLE_NAME = UPPER(?)
        ORDER BY COLUMN_ID
    """.trimIndent()

    override fun getIndexesQuery(): String = """
        SELECT
            INDEX_NAME as name,
            LISTAGG(COLUMN_NAME, ',') WITHIN GROUP (ORDER BY COLUMN_POSITION) as columns,
            CASE WHEN UNIQUENESS = 'UNIQUE' THEN 1 ELSE 0 END as is_unique,
            INDEX_TYPE as type
        FROM DBA_IND_COLUMNS ic
        JOIN DBA_INDEXES i ON ic.INDEX_NAME = i.INDEX_NAME AND ic.INDEX_OWNER = i.OWNER
        WHERE ic.TABLE_OWNER = UPPER(?)
        AND ic.TABLE_NAME = UPPER(?)
        GROUP BY ic.INDEX_NAME, i.UNIQUENESS, i.INDEX_TYPE
        ORDER BY ic.INDEX_NAME
    """.trimIndent()

    override fun getCreateIndexSql(database: String, table: String, indexName: String, columns: List<String>, unique: Boolean): String {
        val cols = columns.joinToString(", ") { quoteIdentifier(it) }
        val uniqueKeyword = if (unique) "UNIQUE " else ""
        return "CREATE ${uniqueKeyword}INDEX ${quoteIdentifier(indexName)} ON ${quoteIdentifier(database)}.${quoteIdentifier(table)} ($cols)"
    }

    override fun getDropIndexSql(database: String, table: String, indexName: String): String {
        return "DROP INDEX ${quoteIdentifier(database)}.${quoteIdentifier(indexName)}"
    }

    // ==================== Tablespace Queries ====================

    override fun getTablespacesQuery(): String = """
        SELECT
            TABLESPACE_NAME as name,
            CONTENTS as space_type,
            NVL((SELECT SUM(BYTES) FROM DBA_DATA_FILES WHERE TABLESPACE_NAME = t.TABLESPACE_NAME), 0) as file_size,
            NVL((SELECT SUM(BYTES) FROM DBA_SEGMENTS WHERE TABLESPACE_NAME = t.TABLESPACE_NAME), 0) as allocated_size,
            STATUS as state
        FROM DBA_TABLESPACES t
        ORDER BY TABLESPACE_NAME
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
            OWNER as db_name,
            SEGMENT_NAME as table_name,
            'Oracle' as engine,
            0 as "rows",
            BYTES as "size",
            '' as create_time
        FROM DBA_SEGMENTS
        WHERE TABLESPACE_NAME = UPPER(?)
        AND SEGMENT_TYPE = 'TABLE'
        ORDER BY OWNER, SEGMENT_NAME
    """.trimIndent()

    override fun getMoveTableToTablespaceSql(database: String, tableName: String, tablespaceName: String): String {
        return "ALTER TABLE ${quoteIdentifier(database)}.${quoteIdentifier(tableName)} MOVE TABLESPACE ${quoteIdentifier(tablespaceName)}"
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
        "ORACLE_OCM"
    )
}