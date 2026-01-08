package com.dbaccman.dialect

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * PostgreSQL-specific implementation of DatabaseDialect.
 * Supports PostgreSQL 12+ features.
 */
class PostgreSQLDialect : DatabaseDialect {

    override val type: DatabaseType = DatabaseType.POSTGRESQL

    // ==================== Connection Settings ====================

    override fun getJdbcUrl(host: String, port: Int, database: String?): String {
        val db = database ?: "postgres"
        return "jdbc:postgresql://$host:$port/$db"
    }

    override fun getDriverClassName(): String = "org.postgresql.Driver"

    override fun getConnectionTestQuery(): String = "SELECT 1"

    // ==================== Identifier Quoting ====================

    override fun quoteIdentifier(identifier: String): String {
        return "\"${identifier.replace("\"", "\"\"")}\""
    }

    // ==================== Session Queries ====================

    override fun getActiveSessionsQuery(): String = """
        SELECT
            pid,
            usename as sess_user,
            COALESCE(host(client_addr)::text, 'local') as host,
            datname as database_name,
            state as command,
            EXTRACT(EPOCH FROM (NOW() - backend_start))::integer as time,
            wait_event_type as state,
            query
        FROM pg_stat_activity
        WHERE backend_type = 'client backend'
        AND pid != pg_backend_pid()
        ORDER BY backend_start DESC
    """.trimIndent()

    override fun getSessionStatsQuery(): String = """
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN state = 'active' THEN 1 ELSE 0 END) as active,
            SUM(CASE WHEN state = 'idle' THEN 1 ELSE 0 END) as sleeping,
            SUM(CASE WHEN state = 'active' AND EXTRACT(EPOCH FROM (NOW() - query_start)) > 60 THEN 1 ELSE 0 END) as long_running
        FROM pg_stat_activity
        WHERE backend_type = 'client backend'
        AND pid != pg_backend_pid()
    """.trimIndent()

    override fun getLongRunningQueriesQuery(): String = """
        SELECT
            pid,
            usename as sess_user,
            COALESCE(host(client_addr)::text, 'local') as host,
            datname as database_name,
            state as command,
            EXTRACT(EPOCH FROM (NOW() - query_start))::integer as time,
            wait_event_type as state,
            query
        FROM pg_stat_activity
        WHERE backend_type = 'client backend'
        AND pid != pg_backend_pid()
        AND state = 'active'
        AND EXTRACT(EPOCH FROM (NOW() - query_start)) > ?
        ORDER BY query_start ASC
    """.trimIndent()

    override fun getKillSessionSql(pid: Long, serialNum: Long?): String = "SELECT pg_terminate_backend($pid)"

    override fun getKillQuerySql(pid: Long, serialNum: Long?): String = "SELECT pg_cancel_backend($pid)"

    // ==================== Account Queries ====================

    override fun getAllAccountsQuery(): String = """
        SELECT
            usename as username,
            'localhost' as host,
            COALESCE(TO_CHAR(valuntil, 'YYYY-MM-DD HH24:MI:SS'), '') as password_last_changed,
            0 as password_lifetime,
            CASE WHEN rolcanlogin = false THEN 1 ELSE 0 END as account_locked
        FROM pg_user
        JOIN pg_roles ON pg_user.usename = pg_roles.rolname
        WHERE usename NOT IN (${getSystemUsers().joinToString { "'$it'" }})
        ORDER BY usename
    """.trimIndent()

    override fun getAccountCountQuery(): String = """
        SELECT COUNT(*) as count
        FROM pg_user
        WHERE usename NOT IN (${getSystemUsers().joinToString { "'$it'" }})
    """.trimIndent()

    override fun getLockedAccountsWhereClause(): String = "AND rolcanlogin = false"

    override fun getExpiringAccountsWhereClause(days: Int): String =
        "AND valuntil IS NOT NULL AND valuntil <= NOW() + INTERVAL '$days days' AND rolcanlogin = true"

    override fun getPaginatedAccountsQuery(orderByClause: String, filterClause: String): String {
        val orderBy = orderByClause.ifEmpty { "ORDER BY usename" }
        return """
            SELECT
                usename as username,
                'localhost' as host,
                COALESCE(TO_CHAR(valuntil, 'YYYY-MM-DD HH24:MI:SS'), '') as password_last_changed,
                0 as password_lifetime,
                CASE WHEN rolcanlogin = false THEN 1 ELSE 0 END as account_locked
            FROM pg_user
            JOIN pg_roles ON pg_user.usename = pg_roles.rolname
            WHERE usename NOT IN (${getSystemUsers().joinToString { "'$it'" }})
            $filterClause
            $orderBy
            LIMIT ? OFFSET ?
        """.trimIndent()
    }

    override fun getOptimizedPaginatedAccountsQuery(orderByClause: String, filterClause: String): String {
        val orderBy = orderByClause.ifEmpty { "ORDER BY usename" }
        return """
            SELECT
                usename as username,
                'localhost' as host,
                COALESCE(TO_CHAR(valuntil, 'YYYY-MM-DD HH24:MI:SS'), '') as password_last_changed,
                0 as password_lifetime,
                CASE WHEN rolcanlogin = false THEN 1 ELSE 0 END as account_locked,
                COUNT(*) OVER() as total_count,
                SUM(CASE WHEN rolcanlogin = false THEN 1 ELSE 0 END) OVER() as locked_count
            FROM pg_user
            JOIN pg_roles ON pg_user.usename = pg_roles.rolname
            WHERE usename NOT IN (${getSystemUsers().joinToString { "'$it'" }})
            $filterClause
            $orderBy
            LIMIT ? OFFSET ?
        """.trimIndent()
    }

    override fun getCreateUserSql(username: String, host: String, password: String): String {
        // PostgreSQL DDL with quoted identifier and escaped password
        return "CREATE USER ${quoteIdentifier(username)} WITH PASSWORD '${escapePassword(password)}'"
    }

    override fun getAlterUserPasswordExpireSql(username: String, host: String, expireDays: Int): String {
        val expireDate = LocalDateTime.now().plusDays(expireDays.toLong())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return "ALTER USER ${quoteIdentifier(username)} VALID UNTIL '${expireDate.format(formatter)}'"
    }

    override fun getAlterUserPasswordSql(username: String, host: String, newPassword: String): String {
        return "ALTER USER ${quoteIdentifier(username)} WITH PASSWORD '${escapePassword(newPassword)}'"
    }

    override fun getExpirePasswordSql(username: String, host: String): String {
        return "ALTER USER ${quoteIdentifier(username)} VALID UNTIL 'now'"
    }

    override fun getDropUserSql(username: String, host: String): String {
        return "DROP USER IF EXISTS ${quoteIdentifier(username)}"
    }

    override fun getUnlockAccountSql(username: String, host: String): String {
        return "ALTER USER ${quoteIdentifier(username)} WITH LOGIN"
    }

    private fun escapePassword(password: String): String {
        // Escape single quotes in password for PostgreSQL
        return password.replace("'", "''")
    }

    override fun getExpiringAccountsQuery(): String = """
        SELECT
            usename as username,
            'localhost' as host,
            EXTRACT(DAY FROM (valuntil - NOW()))::integer as days_until_expiry
        FROM pg_user
        WHERE valuntil IS NOT NULL
        AND valuntil > NOW()
        AND EXTRACT(DAY FROM (valuntil - NOW())) < ?
        ORDER BY valuntil ASC
    """.trimIndent()

    override fun getFlushPrivilegesSql(): String? = null // PostgreSQL doesn't need flush

    // PostgreSQL doesn't have user-level default tablespace, it's set at database or table level
    override fun getSetDefaultTablespaceSql(username: String, host: String, tablespace: String): String? = null

    override fun getSetTablespaceQuotaSql(username: String, host: String, tablespace: String, quota: String): String? = null

    // ==================== Permission Queries ====================

    override fun formatGrantee(username: String, host: String): String {
        // PostgreSQL uses just the role/user name (lowercase) as grantee
        return username.lowercase()
    }

    override fun getSchemaPrivilegesQuery(): String = """
        SELECT DISTINCT
            ? as grantee,
            n.nspname as db,
            priv.privilege as privilege,
            'NO' as is_grantable
        FROM pg_namespace n
        CROSS JOIN (VALUES ('USAGE'), ('CREATE')) AS priv(privilege)
        WHERE n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
        AND has_schema_privilege(?, n.nspname, priv.privilege)
    """.trimIndent()

    override fun getTablePrivilegesQuery(): String = """
        SELECT
            grantee as grantee,
            table_schema as db,
            table_name as tbl,
            privilege_type as privilege,
            is_grantable as is_grantable
        FROM information_schema.table_privileges
        WHERE grantee = ?
    """.trimIndent()

    override fun getAllSchemaPrivilegesQuery(): String = """
        SELECT DISTINCT
            a.grantee::regrole::text as grantee,
            n.nspname as db,
            a.privilege_type as privilege,
            CASE WHEN a.is_grantable THEN 'YES' ELSE 'NO' END as is_grantable
        FROM pg_namespace n,
             aclexplode(n.nspacl) as a
        WHERE n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
        AND a.grantee IS NOT NULL
        AND a.grantee != 0
        AND a.grantee::regrole::text NOT IN (${getSystemUsers().joinToString { "'$it'" }})
    """.trimIndent()

    override fun getAllTablePrivilegesQuery(): String = """
        SELECT
            grantee as grantee,
            table_schema as db,
            table_name as tbl,
            privilege_type as privilege,
            is_grantable as is_grantable
        FROM information_schema.table_privileges
        WHERE grantee NOT IN (${getSystemUsers().joinToString { "'$it'" }})
    """.trimIndent()

    // PostgreSQL database-level privileges (CONNECT, CREATE, TEMPORARY)
    private val databasePrivileges = setOf("CONNECT", "TEMPORARY", "TEMP")

    // PostgreSQL schema-level privileges (USAGE, CREATE)
    private val schemaPrivileges = setOf("USAGE", "CREATE")

    override fun getGrantSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        // Separate privileges by level
        val dbPrivs = privileges.filter { it.uppercase() in databasePrivileges }
        val schemaPrivs = privileges.filter { it.uppercase() in schemaPrivileges }
        val tablePrivs = privileges.filterNot { it.uppercase() in databasePrivileges || it.uppercase() in schemaPrivileges }

        val statements = mutableListOf<String>()
        val schemaName = database.ifEmpty { "public" }

        // Handle database-level privileges (CONNECT, TEMPORARY)
        if (dbPrivs.isNotEmpty()) {
            val privList = dbPrivs.joinToString(", ") { it.uppercase() }
            statements.add("GRANT $privList ON DATABASE CURRENT_DATABASE() TO ${quoteIdentifier(username)}")
        }

        // Handle schema-level privileges (USAGE, CREATE)
        if (schemaPrivs.isNotEmpty()) {
            val privList = schemaPrivs.joinToString(", ") { it.uppercase() }
            statements.add("GRANT $privList ON SCHEMA ${quoteIdentifier(schemaName)} TO ${quoteIdentifier(username)}")
        }

        // Handle table-level privileges
        if (tablePrivs.isNotEmpty()) {
            val privList = tablePrivs.joinToString(", ")
            val target = if (table == "*") {
                "ALL TABLES IN SCHEMA ${quoteIdentifier(schemaName)}"
            } else {
                "${quoteIdentifier(schemaName)}.${quoteIdentifier(table)}"
            }
            statements.add("GRANT $privList ON $target TO ${quoteIdentifier(username)}")
        }

        return statements.joinToString("; ")
    }

    override fun getRevokeSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        // Separate privileges by level
        val dbPrivs = privileges.filter { it.uppercase() in databasePrivileges }
        val schemaPrivs = privileges.filter { it.uppercase() in schemaPrivileges }
        val tablePrivs = privileges.filterNot { it.uppercase() in databasePrivileges || it.uppercase() in schemaPrivileges }

        val statements = mutableListOf<String>()
        val schemaName = database.ifEmpty { "public" }

        // Handle database-level privileges
        if (dbPrivs.isNotEmpty()) {
            val privList = dbPrivs.joinToString(", ") { it.uppercase() }
            statements.add("REVOKE $privList ON DATABASE CURRENT_DATABASE() FROM ${quoteIdentifier(username)}")
        }

        // Handle schema-level privileges
        if (schemaPrivs.isNotEmpty()) {
            val privList = schemaPrivs.joinToString(", ") { it.uppercase() }
            statements.add("REVOKE $privList ON SCHEMA ${quoteIdentifier(schemaName)} FROM ${quoteIdentifier(username)}")
        }

        // Handle table-level privileges
        if (tablePrivs.isNotEmpty()) {
            val privList = tablePrivs.joinToString(", ")
            val target = if (table == "*") {
                "ALL TABLES IN SCHEMA ${quoteIdentifier(schemaName)}"
            } else {
                "${quoteIdentifier(schemaName)}.${quoteIdentifier(table)}"
            }
            statements.add("REVOKE $privList ON $target FROM ${quoteIdentifier(username)}")
        }

        return statements.joinToString("; ")
    }

    override fun getShowDatabasesQuery(): String = """
        SELECT datname FROM pg_database WHERE datistemplate = false
    """.trimIndent()

    override fun getAdminCheckQuery(): String = """
        SELECT 1 FROM pg_roles WHERE rolname = current_user AND rolsuper = true
    """.trimIndent()

    // ==================== Table/Database Queries ====================

    override fun getDatabasesQuery(): String = """
        SELECT
            s.schema_name as name,
            COALESCE(t.table_count, 0)::int as table_count,
            COALESCE(t.total_rows, 0)::bigint as total_rows,
            COALESCE(t.total_size, 0)::bigint as total_size
        FROM information_schema.schemata s
        LEFT JOIN (
            SELECT
                schemaname,
                COUNT(*) as table_count,
                SUM(COALESCE(n_live_tup, 0)) as total_rows,
                SUM(pg_total_relation_size(schemaname || '.' || relname)) as total_size
            FROM pg_stat_user_tables
            GROUP BY schemaname
        ) t ON s.schema_name = t.schemaname
        WHERE s.schema_name NOT IN (${getSystemSchemas().joinToString { "'$it'" }})
        ORDER BY s.schema_name
    """.trimIndent()

    override fun getTablesQuery(): String = """
        SELECT
            t.tablename as name,
            'PostgreSQL' as engine,
            COALESCE(GREATEST(c.reltuples, 0)::bigint, 0) as row_count,
            COALESCE(pg_total_relation_size(quote_ident(t.schemaname) || '.' || quote_ident(t.tablename)), 0) as table_size,
            '' as create_time
        FROM pg_tables t
        LEFT JOIN pg_namespace n ON n.nspname = t.schemaname
        LEFT JOIN pg_class c ON c.relname = t.tablename AND c.relnamespace = n.oid
        WHERE t.schemaname = ?
        ORDER BY t.tablename
    """.trimIndent()

    override fun getTableColumnsQuery(): String = """
        SELECT
            column_name as name,
            data_type as type,
            CASE WHEN is_nullable = 'YES' THEN 1 ELSE 0 END as nullable,
            '' as col_key,
            column_default as default_value,
            '' as extra
        FROM information_schema.columns
        WHERE table_schema = ?
        AND table_name = ?
        ORDER BY ordinal_position
    """.trimIndent()

    override fun getIndexesQuery(): String = """
        SELECT
            i.relname as name,
            STRING_AGG(a.attname, ',' ORDER BY array_position(ix.indkey, a.attnum)) as columns,
            CASE WHEN ix.indisunique THEN 1 ELSE 0 END as is_unique,
            am.amname as type
        FROM pg_index ix
        JOIN pg_class t ON t.oid = ix.indrelid
        JOIN pg_class i ON i.oid = ix.indexrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        JOIN pg_am am ON am.oid = i.relam
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
        WHERE n.nspname = ?
        AND t.relname = ?
        GROUP BY i.relname, ix.indisunique, am.amname
        ORDER BY i.relname
    """.trimIndent()

    override fun getCreateIndexSql(database: String, table: String, indexName: String, columns: List<String>, unique: Boolean): String {
        val cols = columns.joinToString(", ") { quoteIdentifier(it) }
        val uniqueKeyword = if (unique) "UNIQUE " else ""
        return "CREATE ${uniqueKeyword}INDEX ${quoteIdentifier(indexName)} ON ${quoteIdentifier(database)}.${quoteIdentifier(table)} ($cols)"
    }

    override fun getDropIndexSql(database: String, table: String, indexName: String): String {
        return "DROP INDEX IF EXISTS ${quoteIdentifier(database)}.${quoteIdentifier(indexName)}"
    }

    override fun getSelectWithLimitSql(quotedTable: String, limit: Int): String {
        return "SELECT * FROM $quotedTable LIMIT $limit"
    }

    // ==================== Tablespace Queries ====================

    override fun getTablespacesQuery(): String = """
        SELECT
            spcname as name,
            CASE
                WHEN spcname IN ('pg_default', 'pg_global') THEN 'SYSTEM'
                ELSE 'GENERAL'
            END as space_type,
            0 as file_size,
            pg_tablespace_size(spcname) as allocated_size,
            'ACTIVE' as state
        FROM pg_tablespace
        WHERE spcname != 'pg_global'
        ORDER BY spcname
    """.trimIndent()

    override fun getCreateTablespaceSql(name: String, dataFile: String?, engine: String?): String {
        val location = dataFile ?: "/var/lib/postgresql/data/${name.lowercase()}"
        return "CREATE TABLESPACE ${quoteIdentifier(name)} LOCATION '$location'"
    }

    override fun getDropTablespaceSql(name: String): String {
        return "DROP TABLESPACE IF EXISTS ${quoteIdentifier(name)}"
    }

    override fun getTablesInTablespaceQuery(): String = """
        SELECT
            n.nspname as db_name,
            c.relname as table_name,
            'PostgreSQL' as engine,
            COALESCE(c.reltuples::bigint, 0) as "rows",
            pg_total_relation_size(c.oid) as "size",
            '' as create_time
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_tablespace t ON t.oid = c.reltablespace
        WHERE t.spcname = ?
        AND c.relkind = 'r'
        ORDER BY n.nspname, c.relname
    """.trimIndent()

    override fun getMoveTableToTablespaceSql(database: String, tableName: String, tablespaceName: String): String {
        return "ALTER TABLE ${quoteIdentifier(database)}.${quoteIdentifier(tableName)} SET TABLESPACE ${quoteIdentifier(tablespaceName)}"
    }

    // ==================== Password Expiry Queries ====================

    override fun getPasswordExpiryQuery(): String = """
        SELECT
            usename as "user",
            'localhost' as host,
            0 as password_lifetime,
            NULL::text as password_last_changed,
            CASE WHEN valuntil IS NOT NULL AND valuntil < NOW() THEN true ELSE false END as is_expired,
            CASE WHEN valuntil IS NOT NULL THEN EXTRACT(DAY FROM (valuntil - NOW()))::integer ELSE NULL END as days_until_expiry
        FROM pg_user
        WHERE usename = ?
        AND COALESCE(?, 'localhost') IS NOT NULL
    """.trimIndent()

    override fun getPasswordExpiryDaysQuery(): String = """
        SELECT
            CASE WHEN valuntil IS NOT NULL THEN EXTRACT(DAY FROM (valuntil - NOW()))::integer ELSE NULL END as days_until_expiry
        FROM pg_user
        WHERE usename = ?
    """.trimIndent()

    // ==================== System Schema Filter ====================

    override fun getSystemSchemas(): List<String> = listOf(
        "information_schema",
        "pg_catalog",
        "pg_toast"
    )

    override fun getSystemUsers(): List<String> = listOf(
        "postgres"
    )

    // ==================== Schema/User Context ====================

    override fun getSwitchSchemaSql(schema: String): String {
        // PostgreSQL uses SET search_path to switch schema
        return "SET search_path TO ${quoteIdentifier(schema)}"
    }

    override fun getCurrentSchemaQuery(): String = "SELECT current_schema() AS current_schema"

    override fun getAvailableSchemasQuery(): String = """
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name NOT IN (${getSystemSchemas().joinToString { "'$it'" }})
        ORDER BY schema_name
    """.trimIndent()

    // ==================== User-specific Queries (for non-admin users) ====================

    /**
     * Get tables owned by the current user.
     */
    fun getMyTablesQuery(): String = """
        SELECT
            schemaname as schema_name,
            tablename as table_name,
            COALESCE(GREATEST(c.reltuples, 0)::bigint, 0) as row_count,
            '' as last_analyzed,
            COALESCE(t.spcname, 'pg_default') as tablespace_name
        FROM pg_tables pt
        LEFT JOIN pg_namespace n ON n.nspname = pt.schemaname
        LEFT JOIN pg_class c ON c.relname = pt.tablename AND c.relnamespace = n.oid
        LEFT JOIN pg_tablespace t ON c.reltablespace = t.oid
        WHERE pt.tableowner = CURRENT_USER
        AND pt.schemaname NOT IN (${getSystemSchemas().joinToString { "'$it'" }})
        ORDER BY pt.schemaname, pt.tablename
    """.trimIndent()

    /**
     * Get columns for a table owned by the current user.
     */
    fun getMyTableColumnsQuery(): String = """
        SELECT
            c.column_name,
            c.data_type,
            c.is_nullable,
            c.ordinal_position,
            c.column_default
        FROM information_schema.columns c
        JOIN pg_tables pt ON c.table_schema = pt.schemaname AND c.table_name = pt.tablename
        WHERE pt.tableowner = CURRENT_USER
        AND c.table_schema = ?
        AND c.table_name = ?
        ORDER BY c.ordinal_position
    """.trimIndent()

    /**
     * Get indexes for a table owned by the current user.
     */
    fun getMyTableIndexesQuery(): String = """
        SELECT
            i.indexname as index_name,
            am.amname as index_type,
            ix.indisunique as is_unique,
            string_agg(a.attname, ', ' ORDER BY array_position(ix.indkey, a.attnum)) as columns
        FROM pg_indexes i
        JOIN pg_tables pt ON i.schemaname = pt.schemaname AND i.tablename = pt.tablename
        JOIN pg_class c ON c.relname = i.indexname
        JOIN pg_index ix ON ix.indexrelid = c.oid
        JOIN pg_class t ON t.oid = ix.indrelid
        JOIN pg_am am ON c.relam = am.oid
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
        WHERE pt.tableowner = CURRENT_USER
        AND i.schemaname = ?
        AND i.tablename = ?
        GROUP BY i.indexname, am.amname, ix.indisunique
        ORDER BY i.indexname
    """.trimIndent()

    /**
     * PostgreSQL doesn't have per-user tablespace quotas like Oracle.
     * Instead, show the user's storage usage by schema for tables they own.
     */
    fun getMyTablespacesQuery(): String = """
        SELECT
            pt.schemaname as name,
            'UNLIMITED' as max_bytes,
            COALESCE(SUM(pg_total_relation_size(quote_ident(pt.schemaname) || '.' || quote_ident(pt.tablename))), 0)::bigint as used_bytes
        FROM pg_tables pt
        WHERE pt.tableowner = CURRENT_USER
        AND pt.schemaname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
        GROUP BY pt.schemaname
        ORDER BY pt.schemaname
    """.trimIndent()

    /**
     * PostgreSQL doesn't have user-level default tablespace.
     * Returns current schema as default.
     */
    fun getMyDefaultTablespaceQuery(): String = """
        SELECT current_schema() as DEFAULT_TABLESPACE, NULL as TEMPORARY_TABLESPACE
    """.trimIndent()
}