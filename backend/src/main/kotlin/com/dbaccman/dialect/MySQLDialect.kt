package com.dbaccman.dialect

/**
 * MySQL-specific implementation of DatabaseDialect.
 * Supports MySQL 8.0+ features.
 */
class MySQLDialect : DatabaseDialect {

    override val type: DatabaseType = DatabaseType.MYSQL

    // ==================== Connection Settings ====================

    override fun getJdbcUrl(host: String, port: Int, database: String?): String {
        val baseUrl = "jdbc:mysql://$host:$port"
        val params = "allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
        return if (database != null) {
            "$baseUrl/$database?$params"
        } else {
            "$baseUrl?$params"
        }
    }

    override fun getDriverClassName(): String = "com.mysql.cj.jdbc.Driver"

    override fun getConnectionTestQuery(): String = "SELECT 1"

    // ==================== Identifier Quoting ====================

    override fun quoteIdentifier(identifier: String): String {
        return "`${identifier.replace("`", "``")}`"
    }

    // ==================== Session Queries ====================

    override fun getActiveSessionsQuery(): String = """
        SELECT
            id as pid,
            user as sess_user,
            SUBSTRING_INDEX(host, ':', 1) as host,
            db as database_name,
            command,
            time,
            state,
            info as query
        FROM information_schema.processlist
        WHERE id != CONNECTION_ID()
        ORDER BY time DESC
    """.trimIndent()

    override fun getSessionStatsQuery(): String = """
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN command != 'Sleep' THEN 1 ELSE 0 END) as active,
            SUM(CASE WHEN command = 'Sleep' THEN 1 ELSE 0 END) as sleeping,
            SUM(CASE WHEN time > 60 AND command != 'Sleep' THEN 1 ELSE 0 END) as long_running
        FROM information_schema.processlist
        WHERE id != CONNECTION_ID()
    """.trimIndent()

    override fun getLongRunningQueriesQuery(): String = """
        SELECT
            id as pid,
            user as sess_user,
            SUBSTRING_INDEX(host, ':', 1) as host,
            db as database_name,
            command,
            time,
            state,
            info as query
        FROM information_schema.processlist
        WHERE id != CONNECTION_ID()
        AND command != 'Sleep'
        AND time > ?
        ORDER BY time DESC
    """.trimIndent()

    override fun getKillSessionSql(pid: Long, serialNum: Long?): String = "KILL $pid"

    override fun getKillQuerySql(pid: Long, serialNum: Long?): String = "KILL QUERY $pid"

    // ==================== Account Queries ====================

    override fun getAllAccountsQuery(): String = """
        SELECT
            user as username,
            host,
            IFNULL(DATE_FORMAT(password_last_changed, '%Y-%m-%d %H:%i:%s'), '') as password_last_changed,
            IFNULL(password_lifetime, 0) as password_lifetime,
            account_locked = 'Y' as account_locked
        FROM mysql.user
        WHERE user NOT IN (${getSystemUsers().joinToString { "'$it'" }})
        ORDER BY user, host
    """.trimIndent()

    override fun getAccountCountQuery(): String = """
        SELECT COUNT(*) as count
        FROM mysql.user
        WHERE user NOT IN (${getSystemUsers().joinToString { "'$it'" }})
    """.trimIndent()

    override fun getLockedAccountsWhereClause(): String = "AND account_locked = 'Y'"

    override fun getExpiringAccountsWhereClause(days: Int): String =
        "AND password_lifetime IS NOT NULL AND password_lifetime > 0 AND " +
        "DATE_ADD(password_last_changed, INTERVAL password_lifetime DAY) <= DATE_ADD(NOW(), INTERVAL $days DAY) " +
        "AND account_locked != 'Y'"

    override fun getPaginatedAccountsQuery(orderByClause: String, filterClause: String): String {
        val orderBy = orderByClause.ifEmpty { "ORDER BY user, host" }
        return """
            SELECT
                user as username,
                host,
                IFNULL(DATE_FORMAT(password_last_changed, '%Y-%m-%d %H:%i:%s'), '') as password_last_changed,
                IFNULL(password_lifetime, 0) as password_lifetime,
                account_locked = 'Y' as account_locked
            FROM mysql.user
            WHERE user NOT IN (${getSystemUsers().joinToString { "'$it'" }})
            $filterClause
            $orderBy
            LIMIT ? OFFSET ?
        """.trimIndent()
    }

    override fun getOptimizedPaginatedAccountsQuery(orderByClause: String, filterClause: String): String {
        val orderBy = orderByClause.ifEmpty { "ORDER BY user, host" }
        return """
            SELECT
                user as username,
                host,
                IFNULL(DATE_FORMAT(password_last_changed, '%Y-%m-%d %H:%i:%s'), '') as password_last_changed,
                IFNULL(password_lifetime, 0) as password_lifetime,
                account_locked = 'Y' as account_locked,
                COUNT(*) OVER() as total_count,
                SUM(CASE WHEN account_locked = 'Y' THEN 1 ELSE 0 END) OVER() as locked_count
            FROM mysql.user
            WHERE user NOT IN (${getSystemUsers().joinToString { "'$it'" }})
            $filterClause
            $orderBy
            LIMIT ? OFFSET ?
        """.trimIndent()
    }

    override fun getCreateUserSql(username: String, host: String, password: String): String {
        // MySQL DDL with quoted identifiers and escaped password
        return "CREATE USER ${quoteIdentifier(username)}@${quoteIdentifier(host)} IDENTIFIED BY '${escapePassword(password)}'"
    }

    override fun getAlterUserPasswordExpireSql(username: String, host: String, expireDays: Int): String {
        return "ALTER USER ${quoteIdentifier(username)}@${quoteIdentifier(host)} PASSWORD EXPIRE INTERVAL $expireDays DAY"
    }

    override fun getAlterUserPasswordSql(username: String, host: String, newPassword: String): String {
        return "ALTER USER ${quoteIdentifier(username)}@${quoteIdentifier(host)} IDENTIFIED BY '${escapePassword(newPassword)}'"
    }

    override fun getExpirePasswordSql(username: String, host: String): String {
        return "ALTER USER ${quoteIdentifier(username)}@${quoteIdentifier(host)} PASSWORD EXPIRE"
    }

    override fun getDropUserSql(username: String, host: String): String {
        return "DROP USER ${quoteIdentifier(username)}@${quoteIdentifier(host)}"
    }

    override fun getUnlockAccountSql(username: String, host: String): String {
        return "ALTER USER ${quoteIdentifier(username)}@${quoteIdentifier(host)} ACCOUNT UNLOCK"
    }

    private fun escapePassword(password: String): String {
        // Escape single quotes and backslashes in password for MySQL
        return password.replace("\\", "\\\\").replace("'", "\\'")
    }

    override fun getExpiringAccountsQuery(): String = """
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

    override fun getFlushPrivilegesSql(): String = "FLUSH PRIVILEGES"

    // MySQL doesn't support user-level default tablespace assignment
    override fun getSetDefaultTablespaceSql(username: String, host: String, tablespace: String): String? = null

    override fun getSetTablespaceQuotaSql(username: String, host: String, tablespace: String, quota: String): String? = null

    // ==================== Permission Queries ====================

    override fun formatGrantee(username: String, host: String): String {
        // MySQL uses 'username'@'host' format in information_schema
        return "'$username'@'$host'"
    }

    override fun getSchemaPrivilegesQuery(): String = """
        SELECT
            GRANTEE as grantee,
            TABLE_SCHEMA as db,
            PRIVILEGE_TYPE as privilege,
            IS_GRANTABLE as is_grantable
        FROM information_schema.SCHEMA_PRIVILEGES
        WHERE GRANTEE = ?
    """.trimIndent()

    override fun getTablePrivilegesQuery(): String = """
        SELECT
            GRANTEE as grantee,
            TABLE_SCHEMA as db,
            TABLE_NAME as tbl,
            PRIVILEGE_TYPE as privilege,
            IS_GRANTABLE as is_grantable
        FROM information_schema.TABLE_PRIVILEGES
        WHERE GRANTEE = ?
    """.trimIndent()

    override fun getAllSchemaPrivilegesQuery(): String = """
        SELECT
            GRANTEE as grantee,
            TABLE_SCHEMA as db,
            PRIVILEGE_TYPE as privilege,
            IS_GRANTABLE as is_grantable
        FROM information_schema.SCHEMA_PRIVILEGES
    """.trimIndent()

    override fun getAllTablePrivilegesQuery(): String = """
        SELECT
            GRANTEE as grantee,
            TABLE_SCHEMA as db,
            TABLE_NAME as tbl,
            PRIVILEGE_TYPE as privilege,
            IS_GRANTABLE as is_grantable
        FROM information_schema.TABLE_PRIVILEGES
    """.trimIndent()

    /**
     * Get global privileges for a specific user from mysql.user table.
     * MySQL stores global privileges as Y/N columns, so we need to unpivot them.
     * Optimized: Filter user first in subquery, then cross join with privilege list.
     */
    fun getGlobalPrivilegesQuery(): String = """
        SELECT
            CONCAT("'", u.user, "'@'", u.host, "'") as grantee,
            '*' as db,
            privilege,
            CASE WHEN u.Grant_priv = 'Y' THEN 'YES' ELSE 'NO' END as is_grantable
        FROM (
            SELECT * FROM mysql.user
            WHERE CONCAT("'", user, "'@'", host, "'") = ?
        ) u
        CROSS JOIN (
            SELECT 'SELECT' as privilege UNION ALL
            SELECT 'INSERT' UNION ALL
            SELECT 'UPDATE' UNION ALL
            SELECT 'DELETE' UNION ALL
            SELECT 'CREATE' UNION ALL
            SELECT 'DROP' UNION ALL
            SELECT 'RELOAD' UNION ALL
            SELECT 'SHUTDOWN' UNION ALL
            SELECT 'PROCESS' UNION ALL
            SELECT 'FILE' UNION ALL
            SELECT 'REFERENCES' UNION ALL
            SELECT 'INDEX' UNION ALL
            SELECT 'ALTER' UNION ALL
            SELECT 'SHOW DATABASES' UNION ALL
            SELECT 'SUPER' UNION ALL
            SELECT 'CREATE TEMPORARY TABLES' UNION ALL
            SELECT 'LOCK TABLES' UNION ALL
            SELECT 'EXECUTE' UNION ALL
            SELECT 'REPLICATION SLAVE' UNION ALL
            SELECT 'REPLICATION CLIENT' UNION ALL
            SELECT 'CREATE VIEW' UNION ALL
            SELECT 'SHOW VIEW' UNION ALL
            SELECT 'CREATE ROUTINE' UNION ALL
            SELECT 'ALTER ROUTINE' UNION ALL
            SELECT 'CREATE USER' UNION ALL
            SELECT 'EVENT' UNION ALL
            SELECT 'TRIGGER' UNION ALL
            SELECT 'CREATE TABLESPACE'
        ) privs
        WHERE CASE privilege
            WHEN 'SELECT' THEN u.Select_priv
            WHEN 'INSERT' THEN u.Insert_priv
            WHEN 'UPDATE' THEN u.Update_priv
            WHEN 'DELETE' THEN u.Delete_priv
            WHEN 'CREATE' THEN u.Create_priv
            WHEN 'DROP' THEN u.Drop_priv
            WHEN 'RELOAD' THEN u.Reload_priv
            WHEN 'SHUTDOWN' THEN u.Shutdown_priv
            WHEN 'PROCESS' THEN u.Process_priv
            WHEN 'FILE' THEN u.File_priv
            WHEN 'REFERENCES' THEN u.References_priv
            WHEN 'INDEX' THEN u.Index_priv
            WHEN 'ALTER' THEN u.Alter_priv
            WHEN 'SHOW DATABASES' THEN u.Show_db_priv
            WHEN 'SUPER' THEN u.Super_priv
            WHEN 'CREATE TEMPORARY TABLES' THEN u.Create_tmp_table_priv
            WHEN 'LOCK TABLES' THEN u.Lock_tables_priv
            WHEN 'EXECUTE' THEN u.Execute_priv
            WHEN 'REPLICATION SLAVE' THEN u.Repl_slave_priv
            WHEN 'REPLICATION CLIENT' THEN u.Repl_client_priv
            WHEN 'CREATE VIEW' THEN u.Create_view_priv
            WHEN 'SHOW VIEW' THEN u.Show_view_priv
            WHEN 'CREATE ROUTINE' THEN u.Create_routine_priv
            WHEN 'ALTER ROUTINE' THEN u.Alter_routine_priv
            WHEN 'CREATE USER' THEN u.Create_user_priv
            WHEN 'EVENT' THEN u.Event_priv
            WHEN 'TRIGGER' THEN u.Trigger_priv
            WHEN 'CREATE TABLESPACE' THEN u.Create_tablespace_priv
            ELSE 'N'
        END = 'Y'
        ORDER BY privilege
    """.trimIndent()

    /**
     * Get all global privileges for all users from mysql.user table.
     * Optimized: Filter system users first in subquery, then cross join with privilege list.
     */
    fun getAllGlobalPrivilegesQuery(): String = """
        SELECT
            CONCAT("'", u.user, "'@'", u.host, "'") as grantee,
            '*' as db,
            privilege,
            CASE WHEN u.Grant_priv = 'Y' THEN 'YES' ELSE 'NO' END as is_grantable
        FROM (
            SELECT * FROM mysql.user
            WHERE user NOT IN (${getSystemUsers().joinToString { "'$it'" }})
        ) u
        CROSS JOIN (
            SELECT 'SELECT' as privilege UNION ALL
            SELECT 'INSERT' UNION ALL
            SELECT 'UPDATE' UNION ALL
            SELECT 'DELETE' UNION ALL
            SELECT 'CREATE' UNION ALL
            SELECT 'DROP' UNION ALL
            SELECT 'RELOAD' UNION ALL
            SELECT 'SHUTDOWN' UNION ALL
            SELECT 'PROCESS' UNION ALL
            SELECT 'FILE' UNION ALL
            SELECT 'REFERENCES' UNION ALL
            SELECT 'INDEX' UNION ALL
            SELECT 'ALTER' UNION ALL
            SELECT 'SHOW DATABASES' UNION ALL
            SELECT 'SUPER' UNION ALL
            SELECT 'CREATE TEMPORARY TABLES' UNION ALL
            SELECT 'LOCK TABLES' UNION ALL
            SELECT 'EXECUTE' UNION ALL
            SELECT 'REPLICATION SLAVE' UNION ALL
            SELECT 'REPLICATION CLIENT' UNION ALL
            SELECT 'CREATE VIEW' UNION ALL
            SELECT 'SHOW VIEW' UNION ALL
            SELECT 'CREATE ROUTINE' UNION ALL
            SELECT 'ALTER ROUTINE' UNION ALL
            SELECT 'CREATE USER' UNION ALL
            SELECT 'EVENT' UNION ALL
            SELECT 'TRIGGER' UNION ALL
            SELECT 'CREATE TABLESPACE'
        ) privs
        WHERE CASE privilege
            WHEN 'SELECT' THEN u.Select_priv
            WHEN 'INSERT' THEN u.Insert_priv
            WHEN 'UPDATE' THEN u.Update_priv
            WHEN 'DELETE' THEN u.Delete_priv
            WHEN 'CREATE' THEN u.Create_priv
            WHEN 'DROP' THEN u.Drop_priv
            WHEN 'RELOAD' THEN u.Reload_priv
            WHEN 'SHUTDOWN' THEN u.Shutdown_priv
            WHEN 'PROCESS' THEN u.Process_priv
            WHEN 'FILE' THEN u.File_priv
            WHEN 'REFERENCES' THEN u.References_priv
            WHEN 'INDEX' THEN u.Index_priv
            WHEN 'ALTER' THEN u.Alter_priv
            WHEN 'SHOW DATABASES' THEN u.Show_db_priv
            WHEN 'SUPER' THEN u.Super_priv
            WHEN 'CREATE TEMPORARY TABLES' THEN u.Create_tmp_table_priv
            WHEN 'LOCK TABLES' THEN u.Lock_tables_priv
            WHEN 'EXECUTE' THEN u.Execute_priv
            WHEN 'REPLICATION SLAVE' THEN u.Repl_slave_priv
            WHEN 'REPLICATION CLIENT' THEN u.Repl_client_priv
            WHEN 'CREATE VIEW' THEN u.Create_view_priv
            WHEN 'SHOW VIEW' THEN u.Show_view_priv
            WHEN 'CREATE ROUTINE' THEN u.Create_routine_priv
            WHEN 'ALTER ROUTINE' THEN u.Alter_routine_priv
            WHEN 'CREATE USER' THEN u.Create_user_priv
            WHEN 'EVENT' THEN u.Event_priv
            WHEN 'TRIGGER' THEN u.Trigger_priv
            WHEN 'CREATE TABLESPACE' THEN u.Create_tablespace_priv
            ELSE 'N'
        END = 'Y'
        ORDER BY grantee, privilege
    """.trimIndent()

    override fun getGrantSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val privList = privileges.joinToString(", ")
        // Handle global privileges (empty database = *.*)
        val target = when {
            database.isEmpty() || database == "*" -> "*.*"  // Global privileges
            table == "*" -> "${quoteIdentifier(database)}.*"  // Database-level privileges
            else -> "${quoteIdentifier(database)}.${quoteIdentifier(table)}"  // Table-level privileges
        }
        return "GRANT $privList ON $target TO ${quoteIdentifier(username)}@${quoteIdentifier(host)}"
    }

    override fun getRevokeSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val privList = privileges.joinToString(", ")
        val target = when {
            database.isEmpty() || database == "*" -> "*.*"  // Global privileges
            table == "*" -> "${quoteIdentifier(database)}.*"  // Database-level privileges
            else -> "${quoteIdentifier(database)}.${quoteIdentifier(table)}"  // Table-level privileges
        }
        return "REVOKE $privList ON $target FROM ${quoteIdentifier(username)}@${quoteIdentifier(host)}"
    }

    override fun getShowDatabasesQuery(): String = "SHOW DATABASES"

    override fun getAdminCheckQuery(): String = "SHOW GRANTS FOR CURRENT_USER()"

    // ==================== Table/Database Queries ====================

    override fun getDatabasesQuery(): String = """
        SELECT
            s.schema_name as name,
            COUNT(t.table_name) as table_count,
            IFNULL(SUM(t.table_rows), 0) as total_rows,
            IFNULL(SUM(t.data_length + t.index_length), 0) as total_size
        FROM information_schema.schemata s
        LEFT JOIN information_schema.tables t
            ON s.schema_name = t.table_schema
        WHERE s.schema_name NOT IN (${getSystemSchemas().joinToString { "'$it'" }})
        GROUP BY s.schema_name
        ORDER BY s.schema_name
    """.trimIndent()

    override fun getTablesQuery(): String = """
        SELECT
            table_name as name,
            engine,
            IFNULL(table_rows, 0) as row_count,
            IFNULL(data_length + index_length, 0) as table_size,
            DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') as create_time
        FROM information_schema.tables
        WHERE table_schema = ?
        AND table_type = 'BASE TABLE'
        ORDER BY table_name
    """.trimIndent()

    override fun getTableColumnsQuery(): String = """
        SELECT
            column_name as name,
            column_type as type,
            is_nullable = 'YES' as nullable,
            column_key as col_key,
            column_default as default_value,
            extra
        FROM information_schema.columns
        WHERE table_schema = ?
        AND table_name = ?
        ORDER BY ordinal_position
    """.trimIndent()

    override fun getIndexesQuery(): String = """
        SELECT
            index_name as name,
            GROUP_CONCAT(column_name ORDER BY seq_in_index) as columns,
            NOT non_unique as is_unique,
            index_type as type
        FROM information_schema.statistics
        WHERE table_schema = ?
        AND table_name = ?
        GROUP BY index_name, non_unique, index_type
        ORDER BY index_name
    """.trimIndent()

    override fun getCreateIndexSql(database: String, table: String, indexName: String, columns: List<String>, unique: Boolean): String {
        val cols = columns.joinToString(", ") { quoteIdentifier(it) }
        val uniqueKeyword = if (unique) "UNIQUE " else ""
        return "CREATE ${uniqueKeyword}INDEX ${quoteIdentifier(indexName)} ON ${quoteIdentifier(database)}.${quoteIdentifier(table)} ($cols)"
    }

    override fun getDropIndexSql(database: String, table: String, indexName: String): String {
        return "DROP INDEX ${quoteIdentifier(indexName)} ON ${quoteIdentifier(database)}.${quoteIdentifier(table)}"
    }

    override fun getSelectWithLimitSql(quotedTable: String, limit: Int): String {
        return "SELECT * FROM $quotedTable LIMIT $limit"
    }

    // ==================== Tablespace Queries ====================

    override fun getTablespacesQuery(): String = """
        SELECT
            NAME as name,
            SPACE_TYPE as space_type,
            FILE_SIZE as file_size,
            ALLOCATED_SIZE as allocated_size,
            STATE as state
        FROM information_schema.INNODB_TABLESPACES
        ORDER BY NAME
    """.trimIndent()

    override fun getCreateTablespaceSql(name: String, dataFile: String?, engine: String?): String {
        val file = dataFile ?: "${name}.ibd"
        val eng = engine ?: "InnoDB"
        return "CREATE TABLESPACE ${quoteIdentifier(name)} ADD DATAFILE '$file' ENGINE=$eng"
    }

    override fun getDropTablespaceSql(name: String): String {
        return "DROP TABLESPACE ${quoteIdentifier(name)}"
    }

    // ==================== Provisioning ====================

    /**
     * Returns SQL to create a database with an explicit character set.
     */
    fun getCreateDatabaseSql(name: String, charset: String = "utf8mb4"): String {
        return "CREATE DATABASE ${quoteIdentifier(name)} CHARACTER SET $charset"
    }

    /**
     * Returns SQL to grant all privileges on a database to a user.
     */
    fun getGrantAllOnDatabaseSql(database: String, username: String, host: String): String {
        return "GRANT ALL PRIVILEGES ON ${quoteIdentifier(database)}.* TO ${quoteIdentifier(username)}@${quoteIdentifier(host)}"
    }

    override fun getTablesInTablespaceQuery(): String = """
        SELECT
            t.TABLE_SCHEMA as db_name,
            t.TABLE_NAME as table_name,
            t.ENGINE as engine,
            IFNULL(t.TABLE_ROWS, 0) as `rows`,
            IFNULL(t.DATA_LENGTH + t.INDEX_LENGTH, 0) as size,
            IFNULL(DATE_FORMAT(t.CREATE_TIME, '%Y-%m-%d %H:%i:%s'), '') as create_time
        FROM information_schema.TABLES t
        JOIN information_schema.INNODB_TABLES it
            ON CONCAT(t.TABLE_SCHEMA, '/', t.TABLE_NAME) = it.NAME
        JOIN information_schema.INNODB_TABLESPACES ts
            ON it.SPACE = ts.SPACE
        WHERE ts.NAME = ?
        AND t.TABLE_TYPE = 'BASE TABLE'
        ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME
    """.trimIndent()

    override fun getMoveTableToTablespaceSql(database: String, tableName: String, tablespaceName: String): String {
        return "ALTER TABLE ${quoteIdentifier(database)}.${quoteIdentifier(tableName)} TABLESPACE = ${quoteIdentifier(tablespaceName)}"
    }

    // ==================== Password Expiry Queries ====================

    override fun getPasswordExpiryQuery(): String = """
        SELECT
            user,
            host,
            password_lifetime,
            password_last_changed,
            CASE
                WHEN password_expired = 'Y' THEN true
                ELSE false
            END as is_expired,
            CASE
                WHEN password_lifetime IS NULL OR password_lifetime = 0 THEN NULL
                ELSE DATEDIFF(
                    DATE_ADD(password_last_changed, INTERVAL password_lifetime DAY),
                    NOW()
                )
            END as days_until_expiry
        FROM mysql.user
        WHERE user = ? AND host = ?
    """.trimIndent()

    /**
     * Returns password expiry query for regular users (using session status).
     * Limited info available without mysql.user access.
     */
    fun getMyPasswordExpiryQuery(): String = """
        SELECT
            SUBSTRING_INDEX(CURRENT_USER(), '@', 1) as user,
            SUBSTRING_INDEX(CURRENT_USER(), '@', -1) as host,
            NULL as password_lifetime,
            NULL as password_last_changed,
            false as is_expired,
            NULL as days_until_expiry
    """.trimIndent()

    override fun getPasswordExpiryDaysQuery(): String = """
        SELECT
            CASE
                WHEN password_lifetime IS NULL OR password_lifetime = 0 THEN NULL
                ELSE DATEDIFF(
                    DATE_ADD(password_last_changed, INTERVAL password_lifetime DAY),
                    NOW()
                )
            END as days_until_expiry
        FROM mysql.user
        WHERE user = ?
        LIMIT 1
    """.trimIndent()

    // ==================== System Schema Filter ====================

    override fun getSystemSchemas(): List<String> = listOf(
        "information_schema",
        "performance_schema",
        "mysql",
        "sys"
    )

    override fun getSystemUsers(): List<String> = listOf(
        "mysql.sys",
        "mysql.session",
        "mysql.infoschema"
    )

    // ==================== Schema/User Context ====================

    override fun getSwitchSchemaSql(schema: String): String {
        // MySQL uses USE database to switch schema
        return "USE ${quoteIdentifier(schema)}"
    }

    override fun getCurrentSchemaQuery(): String = "SELECT DATABASE() AS current_schema"

    override fun getAvailableSchemasQuery(): String = """
        SELECT SCHEMA_NAME as schema_name
        FROM information_schema.SCHEMATA
        WHERE SCHEMA_NAME NOT IN (${getSystemSchemas().joinToString { "'$it'" }})
        ORDER BY SCHEMA_NAME
    """.trimIndent()

    // ==================== User-specific Queries (for non-admin users) ====================

    /**
     * Get tables in the current database (user's default schema).
     */
    fun getMyTablesQuery(): String = """
        SELECT
            DATABASE() as schema_name,
            table_name,
            IFNULL(table_rows, 0) as row_count,
            DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') as last_analyzed,
            '' as tablespace_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
        AND table_type = 'BASE TABLE'
        ORDER BY table_name
    """.trimIndent()

    /**
     * Get columns for a table in the current database.
     */
    fun getMyTableColumnsQuery(): String = """
        SELECT
            column_name,
            column_type as data_type,
            is_nullable,
            ordinal_position,
            column_default
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
        AND table_name = ?
        ORDER BY ordinal_position
    """.trimIndent()

    /**
     * Get indexes for a table in the current database.
     */
    fun getMyTableIndexesQuery(): String = """
        SELECT
            index_name,
            index_type,
            NOT non_unique as is_unique,
            GROUP_CONCAT(column_name ORDER BY seq_in_index) as columns
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
        AND table_name = ?
        GROUP BY index_name, index_type, non_unique
        ORDER BY index_name
    """.trimIndent()

    /**
     * MySQL doesn't have per-user tablespace quotas like Oracle.
     * Instead, show the user's storage usage in the current database.
     */
    fun getMyTablespacesQuery(): String = """
        SELECT
            DATABASE() as name,
            'UNLIMITED' as max_bytes,
            COALESCE(SUM(data_length + index_length), 0) as used_bytes
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
        AND table_type = 'BASE TABLE'
    """.trimIndent()

    /**
     * MySQL doesn't have user-level default tablespace.
     * Returns current database as schema.
     */
    fun getMyDefaultTablespaceQuery(): String = """
        SELECT DATABASE() as DEFAULT_TABLESPACE, NULL as TEMPORARY_TABLESPACE
    """.trimIndent()

    /**
     * MySQL: Get tables in the current database (tablespace = database for My Tablespaces).
     */
    fun getMyTablesInTablespaceQuery(): String = """
        SELECT
            TABLE_SCHEMA as db_name,
            TABLE_NAME as table_name,
            ENGINE as engine,
            IFNULL(TABLE_ROWS, 0) as `rows`,
            IFNULL(DATA_LENGTH + INDEX_LENGTH, 0) as `size`,
            IFNULL(DATE_FORMAT(CREATE_TIME, '%Y-%m-%d %H:%i:%s'), '') as create_time
        FROM information_schema.tables
        WHERE TABLE_SCHEMA = ?
        AND TABLE_TYPE = 'BASE TABLE'
        ORDER BY TABLE_NAME
    """.trimIndent()
}