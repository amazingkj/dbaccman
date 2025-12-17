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
            user,
            host,
            db as database_name,
            command,
            time,
            state,
            info as query
        FROM information_schema.processlist
        ORDER BY time DESC
    """.trimIndent()

    override fun getSessionStatsQuery(): String = """
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN command != 'Sleep' THEN 1 ELSE 0 END) as active,
            SUM(CASE WHEN command = 'Sleep' THEN 1 ELSE 0 END) as sleeping,
            SUM(CASE WHEN time > 60 AND command != 'Sleep' THEN 1 ELSE 0 END) as long_running
        FROM information_schema.processlist
    """.trimIndent()

    override fun getLongRunningQueriesQuery(): String = """
        SELECT
            id as pid,
            user,
            host,
            db as database_name,
            command,
            time,
            state,
            info as query
        FROM information_schema.processlist
        WHERE command != 'Sleep'
        AND time > ?
        ORDER BY time DESC
    """.trimIndent()

    override fun getKillSessionSql(pid: Long): String = "KILL $pid"

    override fun getKillQuerySql(pid: Long): String = "KILL QUERY $pid"

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

    override fun getCreateUserSql(username: String, host: String, password: String): String {
        return "CREATE USER ?@? IDENTIFIED BY ?"
    }

    override fun getAlterUserPasswordExpireSql(username: String, host: String, expireDays: Int): String {
        return "ALTER USER ?@? PASSWORD EXPIRE INTERVAL ? DAY"
    }

    override fun getAlterUserPasswordSql(username: String, host: String, newPassword: String): String {
        return "ALTER USER ?@? IDENTIFIED BY ?"
    }

    override fun getExpirePasswordSql(username: String, host: String): String {
        return "ALTER USER ?@? PASSWORD EXPIRE"
    }

    override fun getDropUserSql(username: String, host: String): String {
        return "DROP USER ?@?"
    }

    override fun getUnlockAccountSql(username: String, host: String): String {
        return "ALTER USER ?@? ACCOUNT UNLOCK"
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

    // ==================== Permission Queries ====================

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

    override fun getGrantSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val privList = privileges.joinToString(", ")
        val target = if (table == "*") {
            "${quoteIdentifier(database)}.*"
        } else {
            "${quoteIdentifier(database)}.${quoteIdentifier(table)}"
        }
        return "GRANT $privList ON $target TO ?@?"
    }

    override fun getRevokeSql(privileges: List<String>, database: String, table: String, username: String, host: String): String {
        val privList = privileges.joinToString(", ")
        val target = if (table == "*") {
            "${quoteIdentifier(database)}.*"
        } else {
            "${quoteIdentifier(database)}.${quoteIdentifier(table)}"
        }
        return "REVOKE $privList ON $target FROM ?@?"
    }

    override fun getShowDatabasesQuery(): String = "SHOW DATABASES"

    override fun getAdminCheckQuery(): String = "SHOW GRANTS FOR CURRENT_USER()"

    // ==================== Table/Database Queries ====================

    override fun getDatabasesQuery(): String = """
        SELECT
            s.schema_name as name,
            COUNT(t.table_name) as table_count,
            IFNULL(SUM(t.data_length + t.index_length), 0) as size
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
            IFNULL(table_rows, 0) as `rows`,
            IFNULL(data_length + index_length, 0) as size,
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

    override fun getTablesInTablespaceQuery(): String = """
        SELECT
            t.TABLE_SCHEMA as db_name,
            t.TABLE_NAME as table_name,
            t.ENGINE as engine,
            IFNULL(t.TABLE_ROWS, 0) as `rows`,
            IFNULL(t.DATA_LENGTH + t.INDEX_LENGTH, 0) as size,
            DATE_FORMAT(t.CREATE_TIME, '%Y-%m-%d %H:%i:%s') as create_time
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
}