package com.dbaccman.dialect

import java.sql.Connection

/**
 * Database dialect interface for abstracting database-specific operations.
 * Implementations should provide database-specific SQL queries and connection settings.
 */
interface DatabaseDialect {

    val type: DatabaseType

    // ==================== Connection Settings ====================

    fun getJdbcUrl(host: String, port: Int, database: String? = null): String

    fun getDriverClassName(): String

    fun getConnectionTestQuery(): String

    // ==================== Identifier Quoting ====================

    fun quoteIdentifier(identifier: String): String

    // ==================== Session Queries ====================

    fun getActiveSessionsQuery(): String

    fun getSessionStatsQuery(): String

    fun getLongRunningQueriesQuery(): String

    fun getKillSessionSql(pid: Long, serialNum: Long? = null): String

    fun getKillQuerySql(pid: Long, serialNum: Long? = null): String

    // ==================== Account Queries ====================

    fun getAllAccountsQuery(): String

    fun getAccountCountQuery(): String

    /**
     * Returns the WHERE clause for filtering locked accounts.
     * This is used to construct filtered queries dynamically.
     */
    fun getLockedAccountsWhereClause(): String

    /**
     * Returns the WHERE clause for filtering expiring accounts (within N days).
     * This is used to construct filtered queries dynamically.
     * @param days Number of days until expiry threshold
     */
    fun getExpiringAccountsWhereClause(days: Int): String

    /**
     * Returns paginated accounts query with LIMIT/OFFSET.
     * Parameters: offset (Int), limit (Int)
     * @param orderByClause Optional ORDER BY clause for sorting (e.g., "ORDER BY username ASC")
     * @param filterClause Optional WHERE clause for filtering (e.g., locked/expiring accounts)
     */
    fun getPaginatedAccountsQuery(orderByClause: String = "", filterClause: String = ""): String

    /**
     * Returns optimized paginated accounts query that includes total_count and locked_count
     * using window functions to reduce multiple queries to one.
     * Returns columns: username, host, password_last_changed, password_lifetime, account_locked, total_count, locked_count
     */
    fun getOptimizedPaginatedAccountsQuery(orderByClause: String = "", filterClause: String = ""): String

    fun getCreateUserSql(username: String, host: String, password: String): String

    fun getAlterUserPasswordExpireSql(username: String, host: String, expireDays: Int): String

    fun getAlterUserPasswordSql(username: String, host: String, newPassword: String): String

    fun getExpirePasswordSql(username: String, host: String): String

    fun getDropUserSql(username: String, host: String): String

    fun getUnlockAccountSql(username: String, host: String): String

    fun getExpiringAccountsQuery(): String

    fun getFlushPrivilegesSql(): String?

    /**
     * Returns SQL to set default tablespace for a user.
     * Returns null if the database doesn't support this feature.
     */
    fun getSetDefaultTablespaceSql(username: String, host: String, tablespace: String): String?

    /**
     * Returns SQL to set quota on a tablespace for a user.
     * Returns null if the database doesn't support this feature.
     */
    fun getSetTablespaceQuotaSql(username: String, host: String, tablespace: String, quota: String): String?

    // ==================== Permission Queries ====================

    /**
     * Formats grantee identifier for permission queries.
     * MySQL uses 'user'@'host', Oracle uses just USERNAME, PostgreSQL uses username.
     */
    fun formatGrantee(username: String, host: String): String

    fun getSchemaPrivilegesQuery(): String

    fun getTablePrivilegesQuery(): String

    /**
     * Get all schema-level privileges for all users (no WHERE clause on grantee).
     * Used for batch export operations.
     */
    fun getAllSchemaPrivilegesQuery(): String

    /**
     * Get all table-level privileges for all users (no WHERE clause on grantee).
     * Used for batch export operations.
     */
    fun getAllTablePrivilegesQuery(): String

    fun getGrantSql(privileges: List<String>, database: String, table: String, username: String, host: String): String

    fun getRevokeSql(privileges: List<String>, database: String, table: String, username: String, host: String): String

    fun getShowDatabasesQuery(): String

    fun getAdminCheckQuery(): String

    // ==================== Table/Database Queries ====================

    fun getDatabasesQuery(): String

    fun getTablesQuery(): String

    fun getTableColumnsQuery(): String

    fun getIndexesQuery(): String

    fun getCreateIndexSql(database: String, table: String, indexName: String, columns: List<String>, unique: Boolean): String

    fun getDropIndexSql(database: String, table: String, indexName: String): String

    /**
     * Returns SQL for SELECT * FROM table with LIMIT.
     * Different databases have different LIMIT syntax.
     */
    fun getSelectWithLimitSql(quotedTable: String, limit: Int): String

    /**
     * Returns SQL to gather table statistics.
     * Returns null if the database doesn't support/need explicit statistics gathering.
     */
    fun getGatherStatsSql(schema: String, table: String? = null): String? = null

    // ==================== Tablespace Queries ====================

    fun getTablespacesQuery(): String

    fun getCreateTablespaceSql(name: String, dataFile: String?, engine: String?): String

    fun getDropTablespaceSql(name: String): String

    fun getTablesInTablespaceQuery(): String

    fun getMoveTableToTablespaceSql(database: String, tableName: String, tablespaceName: String): String

    // ==================== Password Expiry Queries ====================

    fun getPasswordExpiryQuery(): String

    fun getPasswordExpiryDaysQuery(): String

    // ==================== System Schema Filter ====================

    fun getSystemSchemas(): List<String>

    fun getSystemUsers(): List<String>

    // ==================== Result Set Column Mapping ====================

    /**
     * Maps generic column names to database-specific column names in result sets.
     * Override if the database uses different column naming conventions.
     */
    fun mapColumnName(genericName: String): String = genericName

    // ==================== Schema/User Context ====================

    /**
     * Returns SQL to switch current schema/user context for query execution.
     * This allows running queries in the context of a different user's schema.
     */
    fun getSwitchSchemaSql(schema: String): String?

    /**
     * Returns SQL to get the current schema name.
     */
    fun getCurrentSchemaQuery(): String

    /**
     * Returns SQL to get all available schemas that the user can access.
     * This is different from getDatabasesQuery which may return tablespaces for Oracle.
     */
    fun getAvailableSchemasQuery(): String
}