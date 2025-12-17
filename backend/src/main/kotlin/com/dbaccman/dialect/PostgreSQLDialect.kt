package com.dbaccman.dialect

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
            usename as "user",
            client_addr::text as host,
            datname as database_name,
            state as command,
            EXTRACT(EPOCH FROM (NOW() - backend_start))::integer as time,
            wait_event_type as state,
            query
        FROM pg_stat_activity
        WHERE backend_type = 'client backend'
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
    """.trimIndent()

    override fun getLongRunningQueriesQuery(): String = """
        SELECT
            pid,
            usename as "user",
            client_addr::text as host,
            datname as database_name,
            state as command,
            EXTRACT(EPOCH FROM (NOW() - query_start))::integer as time,
            wait_event_type as state,
            query
        FROM pg_stat_activity
        WHERE backend_type = 'client backend'
        AND state = 'active'
        AND EXTRACT(EPOCH FROM (NOW() - query_start)) > ?
        ORDER BY query_start ASC
    """.trimIndent()

    override fun getKillSessionSql(pid: Long): String = "SELECT pg_terminate_backend($pid)"

    override fun getKillQuerySql(pid: Long): String = "SELECT pg_cancel_backend($pid)"

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

    override fun getCreateUserSql(username: String, host: String, password: String): String {
        return "CREATE USER ? WITH PASSWORD ?"
    }

    override fun getAlterUserPasswordExpireSql(username: String, host: String, expireDays: Int): String {
        return "ALTER USER ? VALID UNTIL CURRENT_DATE + INTERVAL '? days'"
    }

    override fun getAlterUserPasswordSql(username: String, host: String, newPassword: String): String {
        return "ALTER USER ? WITH PASSWORD ?"
    }

    override fun getExpirePasswordSql(username: String, host: String): String {
        return "ALTER USER ? VALID UNTIL 'now'"
    }

    override fun getDropUserSql(username: String, host: String): String {
        return "DROP USER IF EXISTS ?"
    }

    override fun getUnlockAccountSql(username: String, host: String): String {
        return "ALTER USER ? WITH LOGIN"
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

    // ==================== Permission Queries ====================

    override fun getSchemaPrivilegesQuery(): String = """
        SELECT
            grantee as grantee,
            table_schema as db,
            privilege_type as privilege,
            is_grantable as is_grantable
        FROM information_schema.table_privileges
        WHERE grantee = ?
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

    override fun getGrantSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val privList = privileges.joinToString(", ")
        val target = if (table == "*") {
            "ALL TABLES IN SCHEMA ${quoteIdentifier(database)}"
        } else {
            "${quoteIdentifier(database)}.${quoteIdentifier(table)}"
        }
        return "GRANT $privList ON $target TO ?"
    }

    override fun getRevokeSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val privList = privileges.joinToString(", ")
        val target = if (table == "*") {
            "ALL TABLES IN SCHEMA ${quoteIdentifier(database)}"
        } else {
            "${quoteIdentifier(database)}.${quoteIdentifier(table)}"
        }
        return "REVOKE $privList ON $target FROM ?"
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
            schema_name as name,
            (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = schema_name) as table_count,
            0::bigint as "size"
        FROM information_schema.schemata
        WHERE schema_name NOT IN (${getSystemSchemas().joinToString { "'$it'" }})
        ORDER BY schema_name
    """.trimIndent()

    override fun getTablesQuery(): String = """
        SELECT
            table_name as name,
            'PostgreSQL' as engine,
            COALESCE((SELECT reltuples::bigint FROM pg_class WHERE relname = t.table_name), 0) as "rows",
            COALESCE(pg_total_relation_size(quote_ident(t.table_schema) || '.' || quote_ident(t.table_name)), 0) as "size",
            '' as create_time
        FROM information_schema.tables t
        WHERE table_schema = ?
        AND table_type = 'BASE TABLE'
        ORDER BY table_name
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

    // ==================== Tablespace Queries ====================

    override fun getTablespacesQuery(): String = """
        SELECT
            spcname as name,
            'GENERAL' as space_type,
            pg_tablespace_size(spcname) as file_size,
            pg_tablespace_size(spcname) as allocated_size,
            'ACTIVE' as state
        FROM pg_tablespace
        WHERE spcname NOT IN ('pg_default', 'pg_global')
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
}