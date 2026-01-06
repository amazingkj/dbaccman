package com.dbaccman.dialect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class MySQLDialectTest {

    private lateinit var dialect: MySQLDialect

    @BeforeEach
    fun setUp() {
        dialect = MySQLDialect()
    }

    // ==================== Connection Settings Tests ====================

    @Test
    @DisplayName("MySQL dialect type should be MYSQL")
    fun testDialectType() {
        assertEquals(DatabaseType.MYSQL, dialect.type)
    }

    @Test
    @DisplayName("JDBC URL should be correctly formatted with database")
    fun testJdbcUrlWithDatabase() {
        val url = dialect.getJdbcUrl("localhost", 3306, "testdb")
        assertTrue(url.startsWith("jdbc:mysql://localhost:3306/testdb"))
        assertTrue(url.contains("allowPublicKeyRetrieval=true"))
        assertTrue(url.contains("useSSL=false"))
    }

    @Test
    @DisplayName("JDBC URL should be correctly formatted without database")
    fun testJdbcUrlWithoutDatabase() {
        val url = dialect.getJdbcUrl("localhost", 3306, null)
        assertTrue(url.startsWith("jdbc:mysql://localhost:3306?"))
        assertFalse(url.contains("/testdb"))
    }

    @Test
    @DisplayName("Driver class name should be MySQL connector")
    fun testDriverClassName() {
        assertEquals("com.mysql.cj.jdbc.Driver", dialect.getDriverClassName())
    }

    @Test
    @DisplayName("Connection test query should be SELECT 1")
    fun testConnectionTestQuery() {
        assertEquals("SELECT 1", dialect.getConnectionTestQuery())
    }

    // ==================== Identifier Quoting Tests ====================

    @Test
    @DisplayName("Identifier should be quoted with backticks")
    fun testQuoteIdentifier() {
        assertEquals("`username`", dialect.quoteIdentifier("username"))
    }

    @Test
    @DisplayName("Backticks in identifier should be escaped")
    fun testQuoteIdentifierWithBacktick() {
        // Input: `test` -> escape backticks: ``test`` -> wrap: ` ``test`` `
        assertEquals("```test```", dialect.quoteIdentifier("`test`"))
    }

    // ==================== Session Query Tests ====================

    @Test
    @DisplayName("Kill session SQL should use KILL command")
    fun testKillSessionSql() {
        val sql = dialect.getKillSessionSql(12345, null)
        assertEquals("KILL 12345", sql)
    }

    @Test
    @DisplayName("Kill query SQL should use KILL QUERY command")
    fun testKillQuerySql() {
        val sql = dialect.getKillQuerySql(12345, null)
        assertEquals("KILL QUERY 12345", sql)
    }

    @Test
    @DisplayName("Active sessions query should select from processlist")
    fun testActiveSessionsQuery() {
        val query = dialect.getActiveSessionsQuery()
        assertTrue(query.contains("information_schema.processlist"))
        assertTrue(query.contains("pid"))
        assertTrue(query.contains("sess_user"))
    }

    // ==================== Account Query Tests ====================

    @Test
    @DisplayName("Create user SQL should be properly formatted")
    fun testCreateUserSql() {
        val sql = dialect.getCreateUserSql("testuser", "localhost", "password123")
        assertTrue(sql.contains("CREATE USER"))
        assertTrue(sql.contains("`testuser`"))
        assertTrue(sql.contains("`localhost`"))
        assertTrue(sql.contains("IDENTIFIED BY"))
    }

    @Test
    @DisplayName("Create user SQL should escape single quotes in password")
    fun testCreateUserSqlWithSpecialPassword() {
        val sql = dialect.getCreateUserSql("testuser", "%", "pass'word")
        assertTrue(sql.contains("\\'"))
    }

    @Test
    @DisplayName("Alter user password SQL should be properly formatted")
    fun testAlterUserPasswordSql() {
        val sql = dialect.getAlterUserPasswordSql("testuser", "localhost", "newpass")
        assertTrue(sql.contains("ALTER USER"))
        assertTrue(sql.contains("`testuser`"))
        assertTrue(sql.contains("IDENTIFIED BY"))
    }

    @Test
    @DisplayName("Password expire SQL should use ALTER USER")
    fun testPasswordExpireSql() {
        val sql = dialect.getAlterUserPasswordExpireSql("testuser", "localhost", 90)
        assertTrue(sql.contains("ALTER USER"))
        assertTrue(sql.contains("PASSWORD EXPIRE INTERVAL 90 DAY"))
    }

    @Test
    @DisplayName("Expire password immediately SQL should be correct")
    fun testExpirePasswordSql() {
        val sql = dialect.getExpirePasswordSql("testuser", "localhost")
        assertTrue(sql.contains("PASSWORD EXPIRE"))
        assertFalse(sql.contains("INTERVAL"))
    }

    @Test
    @DisplayName("Drop user SQL should use DROP USER")
    fun testDropUserSql() {
        val sql = dialect.getDropUserSql("testuser", "localhost")
        assertEquals("DROP USER `testuser`@`localhost`", sql)
    }

    @Test
    @DisplayName("Unlock account SQL should use ACCOUNT UNLOCK")
    fun testUnlockAccountSql() {
        val sql = dialect.getUnlockAccountSql("testuser", "localhost")
        assertTrue(sql.contains("ACCOUNT UNLOCK"))
    }

    @Test
    @DisplayName("Flush privileges SQL should return FLUSH PRIVILEGES")
    fun testFlushPrivilegesSql() {
        assertEquals("FLUSH PRIVILEGES", dialect.getFlushPrivilegesSql())
    }

    @Test
    @DisplayName("Set default tablespace should return null for MySQL")
    fun testSetDefaultTablespaceReturnsNull() {
        assertNull(dialect.getSetDefaultTablespaceSql("user", "host", "tablespace"))
    }

    @Test
    @DisplayName("Set tablespace quota should return null for MySQL")
    fun testSetTablespaceQuotaReturnsNull() {
        assertNull(dialect.getSetTablespaceQuotaSql("user", "host", "tablespace", "100M"))
    }

    // ==================== Permission Query Tests ====================

    @Test
    @DisplayName("Format grantee should use MySQL format")
    fun testFormatGrantee() {
        val grantee = dialect.formatGrantee("testuser", "localhost")
        assertEquals("'testuser'@'localhost'", grantee)
    }

    @Test
    @DisplayName("Grant SQL should be properly formatted for schema")
    fun testGrantSqlForSchema() {
        val sql = dialect.getGrantSql(listOf("SELECT", "INSERT"), "testdb", "*", "testuser", "localhost")
        assertTrue(sql.contains("GRANT SELECT, INSERT"))
        assertTrue(sql.contains("`testdb`.*"))
        assertTrue(sql.contains("TO `testuser`@`localhost`"))
    }

    @Test
    @DisplayName("Grant SQL should be properly formatted for table")
    fun testGrantSqlForTable() {
        val sql = dialect.getGrantSql(listOf("SELECT"), "testdb", "users", "testuser", "localhost")
        assertTrue(sql.contains("`testdb`.`users`"))
    }

    @Test
    @DisplayName("Revoke SQL should be properly formatted")
    fun testRevokeSql() {
        val sql = dialect.getRevokeSql(listOf("DELETE"), "testdb", "*", "testuser", "localhost")
        assertTrue(sql.contains("REVOKE DELETE"))
        assertTrue(sql.contains("FROM `testuser`@`localhost`"))
    }

    @Test
    @DisplayName("Grant SQL should support global privileges with empty database")
    fun testGrantSqlGlobalPrivilegesEmptyDatabase() {
        val sql = dialect.getGrantSql(listOf("SELECT", "INSERT"), "", "*", "testuser", "localhost")
        assertTrue(sql.contains("GRANT SELECT, INSERT ON *.*"))
        assertTrue(sql.contains("TO `testuser`@`localhost`"))
    }

    @Test
    @DisplayName("Grant SQL should support global privileges with asterisk database")
    fun testGrantSqlGlobalPrivilegesAsteriskDatabase() {
        val sql = dialect.getGrantSql(listOf("SELECT"), "*", "*", "testuser", "localhost")
        assertTrue(sql.contains("ON *.*"))
    }

    @Test
    @DisplayName("Revoke SQL should support global privileges with empty database")
    fun testRevokeSqlGlobalPrivileges() {
        val sql = dialect.getRevokeSql(listOf("SELECT", "INSERT"), "", "*", "testuser", "localhost")
        assertTrue(sql.contains("REVOKE SELECT, INSERT ON *.*"))
        assertTrue(sql.contains("FROM `testuser`@`localhost`"))
    }

    @Test
    @DisplayName("Show databases query should use SHOW DATABASES")
    fun testShowDatabasesQuery() {
        assertEquals("SHOW DATABASES", dialect.getShowDatabasesQuery())
    }

    // ==================== Table/Index Query Tests ====================

    @Test
    @DisplayName("Create index SQL should be properly formatted")
    fun testCreateIndexSql() {
        val sql = dialect.getCreateIndexSql("testdb", "users", "idx_name", listOf("name"), false)
        assertTrue(sql.contains("CREATE INDEX"))
        assertTrue(sql.contains("`idx_name`"))
        assertTrue(sql.contains("`testdb`.`users`"))
    }

    @Test
    @DisplayName("Create unique index SQL should include UNIQUE")
    fun testCreateUniqueIndexSql() {
        val sql = dialect.getCreateIndexSql("testdb", "users", "idx_email", listOf("email"), true)
        assertTrue(sql.contains("CREATE UNIQUE INDEX"))
    }

    @Test
    @DisplayName("Drop index SQL should be properly formatted")
    fun testDropIndexSql() {
        val sql = dialect.getDropIndexSql("testdb", "users", "idx_name")
        assertTrue(sql.contains("DROP INDEX"))
        assertTrue(sql.contains("`idx_name`"))
        assertTrue(sql.contains("ON `testdb`.`users`"))
    }

    // ==================== Tablespace Query Tests ====================

    @Test
    @DisplayName("Create tablespace SQL should be properly formatted")
    fun testCreateTablespaceSql() {
        val sql = dialect.getCreateTablespaceSql("myspace", null, null)
        assertTrue(sql.contains("CREATE TABLESPACE"))
        assertTrue(sql.contains("`myspace`"))
        assertTrue(sql.contains("ADD DATAFILE"))
        assertTrue(sql.contains("ENGINE=InnoDB"))
    }

    @Test
    @DisplayName("Create tablespace SQL with custom datafile")
    fun testCreateTablespaceSqlWithDatafile() {
        val sql = dialect.getCreateTablespaceSql("myspace", "custom.ibd", "InnoDB")
        assertTrue(sql.contains("'custom.ibd'"))
    }

    @Test
    @DisplayName("Drop tablespace SQL should be properly formatted")
    fun testDropTablespaceSql() {
        val sql = dialect.getDropTablespaceSql("myspace")
        assertEquals("DROP TABLESPACE `myspace`", sql)
    }

    @Test
    @DisplayName("Move table to tablespace SQL should use ALTER TABLE")
    fun testMoveTableToTablespaceSql() {
        val sql = dialect.getMoveTableToTablespaceSql("testdb", "users", "myspace")
        assertTrue(sql.contains("ALTER TABLE"))
        assertTrue(sql.contains("TABLESPACE = `myspace`"))
    }

    // ==================== System Schema/User Tests ====================

    @Test
    @DisplayName("System schemas should include MySQL system databases")
    fun testSystemSchemas() {
        val schemas = dialect.getSystemSchemas()
        assertTrue(schemas.contains("information_schema"))
        assertTrue(schemas.contains("performance_schema"))
        assertTrue(schemas.contains("mysql"))
        assertTrue(schemas.contains("sys"))
    }

    @Test
    @DisplayName("System users should include MySQL system users")
    fun testSystemUsers() {
        val users = dialect.getSystemUsers()
        assertTrue(users.contains("mysql.sys"))
        assertTrue(users.contains("mysql.session"))
        assertTrue(users.contains("mysql.infoschema"))
    }

    // ==================== Schema Switch Tests ====================

    @Test
    @DisplayName("Switch schema SQL should use USE command")
    fun testSwitchSchemaSql() {
        val sql = dialect.getSwitchSchemaSql("testdb")
        assertEquals("USE `testdb`", sql)
    }

    @Test
    @DisplayName("Current schema query should use DATABASE() function")
    fun testCurrentSchemaQuery() {
        val query = dialect.getCurrentSchemaQuery()
        assertEquals("SELECT DATABASE() AS current_schema", query)
    }

    @Test
    @DisplayName("Available schemas query should exclude system schemas")
    fun testAvailableSchemasQuery() {
        val query = dialect.getAvailableSchemasQuery()
        assertTrue(query.contains("SCHEMA_NAME"))
        assertTrue(query.contains("information_schema.SCHEMATA"))
        assertTrue(query.contains("'information_schema'"))
        assertTrue(query.contains("'mysql'"))
    }

    // ==================== Password Escaping Tests ====================

    @Test
    @DisplayName("Create user SQL should escape backslash in password")
    fun testCreateUserSqlWithBackslash() {
        val sql = dialect.getCreateUserSql("testuser", "%", "pass\\word")
        assertTrue(sql.contains("\\\\"))
    }

    @Test
    @DisplayName("Create user SQL should escape both backslash and quote")
    fun testCreateUserSqlWithBackslashAndQuote() {
        val sql = dialect.getCreateUserSql("testuser", "%", "pa\\ss'word")
        assertTrue(sql.contains("\\\\"))
        assertTrue(sql.contains("\\'"))
    }

    // ==================== Query Content Validation Tests ====================

    @Test
    @DisplayName("All accounts query should select from mysql.user")
    fun testAllAccountsQuery() {
        val query = dialect.getAllAccountsQuery()
        assertTrue(query.contains("mysql.user"))
        assertTrue(query.contains("username"))
        assertTrue(query.contains("password_last_changed"))
        assertTrue(query.contains("account_locked"))
        // Should exclude system users
        assertTrue(query.contains("mysql.sys"))
    }

    @Test
    @DisplayName("Account count query should count from mysql.user")
    fun testAccountCountQuery() {
        val query = dialect.getAccountCountQuery()
        assertTrue(query.contains("COUNT(*)"))
        assertTrue(query.contains("mysql.user"))
    }

    @Test
    @DisplayName("Paginated accounts query should include LIMIT and OFFSET")
    fun testPaginatedAccountsQuery() {
        val query = dialect.getPaginatedAccountsQuery()
        assertTrue(query.contains("LIMIT"))
        assertTrue(query.contains("OFFSET"))
    }

    @Test
    @DisplayName("Session stats query should calculate active and sleeping counts")
    fun testSessionStatsQuery() {
        val query = dialect.getSessionStatsQuery()
        assertTrue(query.contains("COUNT(*)"))
        assertTrue(query.contains("active"))
        assertTrue(query.contains("sleeping"))
        assertTrue(query.contains("long_running"))
    }

    @Test
    @DisplayName("Long running queries query should have time threshold placeholder")
    fun testLongRunningQueriesQuery() {
        val query = dialect.getLongRunningQueriesQuery()
        assertTrue(query.contains("time > ?"))
        assertTrue(query.contains("ORDER BY time DESC"))
    }

    @Test
    @DisplayName("Password expiry query should select from mysql.user")
    fun testPasswordExpiryQuery() {
        val query = dialect.getPasswordExpiryQuery()
        assertTrue(query.contains("mysql.user"))
        assertTrue(query.contains("password_lifetime"))
        assertTrue(query.contains("days_until_expiry"))
        assertTrue(query.contains("is_expired"))
    }

    @Test
    @DisplayName("Password expiry days query should return days until expiry")
    fun testPasswordExpiryDaysQuery() {
        val query = dialect.getPasswordExpiryDaysQuery()
        assertTrue(query.contains("days_until_expiry"))
        assertTrue(query.contains("DATEDIFF"))
    }

    @Test
    @DisplayName("Expiring accounts query should filter by days threshold")
    fun testExpiringAccountsQuery() {
        val query = dialect.getExpiringAccountsQuery()
        assertTrue(query.contains("days_until_expiry"))
        assertTrue(query.contains("< ?"))
        assertTrue(query.contains(">= 0"))
    }

    @Test
    @DisplayName("Databases query should aggregate table info")
    fun testDatabasesQuery() {
        val query = dialect.getDatabasesQuery()
        assertTrue(query.contains("information_schema.schemata"))
        assertTrue(query.contains("table_count"))
        assertTrue(query.contains("total_rows"))
        assertTrue(query.contains("total_size"))
    }

    @Test
    @DisplayName("Tables query should filter by schema")
    fun testTablesQuery() {
        val query = dialect.getTablesQuery()
        assertTrue(query.contains("information_schema.tables"))
        assertTrue(query.contains("table_schema = ?"))
        assertTrue(query.contains("BASE TABLE"))
    }

    @Test
    @DisplayName("Table columns query should filter by schema and table")
    fun testTableColumnsQuery() {
        val query = dialect.getTableColumnsQuery()
        assertTrue(query.contains("information_schema.columns"))
        assertTrue(query.contains("table_schema = ?"))
        assertTrue(query.contains("table_name = ?"))
    }

    @Test
    @DisplayName("Indexes query should group by index name")
    fun testIndexesQuery() {
        val query = dialect.getIndexesQuery()
        assertTrue(query.contains("information_schema.statistics"))
        assertTrue(query.contains("GROUP_CONCAT"))
        assertTrue(query.contains("GROUP BY index_name"))
    }

    @Test
    @DisplayName("Schema privileges query should use information_schema")
    fun testSchemaPrivilegesQuery() {
        val query = dialect.getSchemaPrivilegesQuery()
        assertTrue(query.contains("information_schema.SCHEMA_PRIVILEGES"))
        assertTrue(query.contains("GRANTEE = ?"))
    }

    @Test
    @DisplayName("Table privileges query should use information_schema")
    fun testTablePrivilegesQuery() {
        val query = dialect.getTablePrivilegesQuery()
        assertTrue(query.contains("information_schema.TABLE_PRIVILEGES"))
        assertTrue(query.contains("GRANTEE = ?"))
    }

    @Test
    @DisplayName("Tablespaces query should select from INNODB_TABLESPACES")
    fun testTablespacesQuery() {
        val query = dialect.getTablespacesQuery()
        assertTrue(query.contains("INNODB_TABLESPACES"))
        assertTrue(query.contains("file_size"))
        assertTrue(query.contains("allocated_size"))
    }

    @Test
    @DisplayName("Tables in tablespace query should join with INNODB_TABLES")
    fun testTablesInTablespaceQuery() {
        val query = dialect.getTablesInTablespaceQuery()
        assertTrue(query.contains("INNODB_TABLES"))
        assertTrue(query.contains("INNODB_TABLESPACES"))
        assertTrue(query.contains("WHERE ts.NAME = ?"))
    }

    @Test
    @DisplayName("Admin check query should use SHOW GRANTS")
    fun testAdminCheckQuery() {
        assertEquals("SHOW GRANTS FOR CURRENT_USER()", dialect.getAdminCheckQuery())
    }

    // ==================== Select With Limit Tests ====================

    @Test
    @DisplayName("Select with limit should use LIMIT clause")
    fun testSelectWithLimit() {
        val sql = dialect.getSelectWithLimitSql("`testdb`.`users`", 100)
        assertEquals("SELECT * FROM `testdb`.`users` LIMIT 100", sql)
    }

    @Test
    @DisplayName("Select with limit should handle large limits")
    fun testSelectWithLargeLimit() {
        val sql = dialect.getSelectWithLimitSql("`db`.`table`", 50000)
        assertTrue(sql.contains("LIMIT 50000"))
    }

    // ==================== Multi-Column Index Tests ====================

    @Test
    @DisplayName("Create index with multiple columns should include all columns")
    fun testCreateIndexMultipleColumns() {
        val sql = dialect.getCreateIndexSql("testdb", "users", "idx_name_email",
            listOf("name", "email", "created_at"), false)
        assertTrue(sql.contains("`name`, `email`, `created_at`"))
    }

    @Test
    @DisplayName("Create unique index with multiple columns")
    fun testCreateUniqueIndexMultipleColumns() {
        val sql = dialect.getCreateIndexSql("testdb", "users", "idx_uniq",
            listOf("col1", "col2"), true)
        assertTrue(sql.contains("CREATE UNIQUE INDEX"))
        assertTrue(sql.contains("`col1`, `col2`"))
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Quote identifier with special characters")
    fun testQuoteIdentifierSpecialChars() {
        val result = dialect.quoteIdentifier("table-name")
        assertEquals("`table-name`", result)
    }

    @Test
    @DisplayName("Quote identifier with spaces")
    fun testQuoteIdentifierWithSpaces() {
        val result = dialect.quoteIdentifier("table name")
        assertEquals("`table name`", result)
    }

    @Test
    @DisplayName("Format grantee with special host")
    fun testFormatGranteeWildcard() {
        val grantee = dialect.formatGrantee("admin", "%")
        assertEquals("'admin'@'%'", grantee)
    }

    @Test
    @DisplayName("JDBC URL with custom port")
    fun testJdbcUrlCustomPort() {
        val url = dialect.getJdbcUrl("192.168.1.100", 13306, "production")
        assertTrue(url.startsWith("jdbc:mysql://192.168.1.100:13306/production"))
    }

    @Test
    @DisplayName("Kill session with different PID values")
    fun testKillSessionVariousPids() {
        assertEquals("KILL 1", dialect.getKillSessionSql(1, null))
        assertEquals("KILL 999999", dialect.getKillSessionSql(999999, null))
        assertEquals("KILL 0", dialect.getKillSessionSql(0, null))
    }

    @Test
    @DisplayName("Create tablespace with custom engine")
    fun testCreateTablespaceCustomEngine() {
        val sql = dialect.getCreateTablespaceSql("myspace", "custom.ibd", "MyISAM")
        assertTrue(sql.contains("ENGINE=MyISAM"))
    }

    @Test
    @DisplayName("My password expiry query for regular users")
    fun testMyPasswordExpiryQuery() {
        val query = dialect.getMyPasswordExpiryQuery()
        assertTrue(query.contains("CURRENT_USER()"))
        assertTrue(query.contains("NULL as password_lifetime"))
    }
}
