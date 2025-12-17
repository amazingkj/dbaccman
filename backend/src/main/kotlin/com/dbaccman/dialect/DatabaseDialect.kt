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

    fun getKillSessionSql(pid: Long): String

    fun getKillQuerySql(pid: Long): String

    // ==================== Account Queries ====================

    fun getAllAccountsQuery(): String

    fun getCreateUserSql(username: String, host: String, password: String): String

    fun getAlterUserPasswordExpireSql(username: String, host: String, expireDays: Int): String

    fun getAlterUserPasswordSql(username: String, host: String, newPassword: String): String

    fun getExpirePasswordSql(username: String, host: String): String

    fun getDropUserSql(username: String, host: String): String

    fun getUnlockAccountSql(username: String, host: String): String

    fun getExpiringAccountsQuery(): String

    fun getFlushPrivilegesSql(): String?

    // ==================== Permission Queries ====================

    fun getSchemaPrivilegesQuery(): String

    fun getTablePrivilegesQuery(): String

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
}