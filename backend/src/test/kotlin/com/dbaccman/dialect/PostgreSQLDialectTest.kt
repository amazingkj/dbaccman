package com.dbaccman.dialect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class PostgreSQLDialectTest {

    private lateinit var dialect: PostgreSQLDialect

    @BeforeEach
    fun setUp() {
        dialect = PostgreSQLDialect()
    }

    // ==================== Connection Settings Tests ====================

    @Test
    @DisplayName("PostgreSQL dialect type should be POSTGRESQL")
    fun testDialectType() {
        assertEquals(DatabaseType.POSTGRESQL, dialect.type)
    }

    @Test
    @DisplayName("JDBC URL should be correctly formatted with database")
    fun testJdbcUrlWithDatabase() {
        val url = dialect.getJdbcUrl("localhost", 5432, "testdb")
        assertEquals("jdbc:postgresql://localhost:5432/testdb", url)
    }

    @Test
    @DisplayName("JDBC URL should use postgres as default database")
    fun testJdbcUrlWithoutDatabase() {
        val url = dialect.getJdbcUrl("localhost", 5432, null)
        assertEquals("jdbc:postgresql://localhost:5432/postgres", url)
    }

    @Test
    @DisplayName("Driver class name should be PostgreSQL driver")
    fun testDriverClassName() {
        assertEquals("org.postgresql.Driver", dialect.getDriverClassName())
    }

    @Test
    @DisplayName("Connection test query should be SELECT 1")
    fun testConnectionTestQuery() {
        assertEquals("SELECT 1", dialect.getConnectionTestQuery())
    }

    // ==================== Identifier Quoting Tests ====================

    @Test
    @DisplayName("Identifier should be quoted with double quotes")
    fun testQuoteIdentifier() {
        assertEquals("\"username\"", dialect.quoteIdentifier("username"))
    }

    @Test
    @DisplayName("Double quotes in identifier should be escaped")
    fun testQuoteIdentifierWithDoubleQuote() {
        assertEquals("\"test\"\"name\"", dialect.quoteIdentifier("test\"name"))
    }

    // ==================== Session Query Tests ====================

    @Test
    @DisplayName("Kill session SQL should use pg_terminate_backend")
    fun testKillSessionSql() {
        val sql = dialect.getKillSessionSql(12345, null)
        assertEquals("SELECT pg_terminate_backend(12345)", sql)
    }

    @Test
    @DisplayName("Kill query SQL should use pg_cancel_backend")
    fun testKillQuerySql() {
        val sql = dialect.getKillQuerySql(12345, null)
        assertEquals("SELECT pg_cancel_backend(12345)", sql)
    }

    @Test
    @DisplayName("Active sessions query should select from pg_stat_activity")
    fun testActiveSessionsQuery() {
        val query = dialect.getActiveSessionsQuery()
        assertTrue(query.contains("pg_stat_activity"))
        assertTrue(query.contains("pid"))
        assertTrue(query.contains("sess_user"))
    }

    // ==================== Account Query Tests ====================

    @Test
    @DisplayName("Create user SQL should be properly formatted")
    fun testCreateUserSql() {
        val sql = dialect.getCreateUserSql("testuser", "localhost", "password123")
        assertTrue(sql.contains("CREATE USER"))
        assertTrue(sql.contains("\"testuser\""))
        assertTrue(sql.contains("WITH PASSWORD"))
    }

    @Test
    @DisplayName("Create user SQL should escape single quotes in password")
    fun testCreateUserSqlWithSpecialPassword() {
        val sql = dialect.getCreateUserSql("testuser", "%", "pass'word")
        assertTrue(sql.contains("''"))
    }

    @Test
    @DisplayName("Alter user password SQL should be properly formatted")
    fun testAlterUserPasswordSql() {
        val sql = dialect.getAlterUserPasswordSql("testuser", "localhost", "newpass")
        assertTrue(sql.contains("ALTER USER"))
        assertTrue(sql.contains("\"testuser\""))
        assertTrue(sql.contains("WITH PASSWORD"))
    }

    @Test
    @DisplayName("Password expire SQL should use VALID UNTIL with date")
    fun testPasswordExpireSql() {
        val sql = dialect.getAlterUserPasswordExpireSql("testuser", "localhost", 90)
        assertTrue(sql.contains("ALTER USER"))
        assertTrue(sql.contains("VALID UNTIL"))
        // Now uses computed date string like '2026-04-05 12:00:00'
        assertTrue(sql.matches(Regex(".*VALID UNTIL '\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}'.*")))
    }

    @Test
    @DisplayName("Expire password immediately SQL should set VALID UNTIL now")
    fun testExpirePasswordSql() {
        val sql = dialect.getExpirePasswordSql("testuser", "localhost")
        assertTrue(sql.contains("VALID UNTIL 'now'"))
    }

    @Test
    @DisplayName("Drop user SQL should use DROP USER IF EXISTS")
    fun testDropUserSql() {
        val sql = dialect.getDropUserSql("testuser", "localhost")
        assertTrue(sql.contains("DROP USER IF EXISTS"))
        assertTrue(sql.contains("\"testuser\""))
    }

    @Test
    @DisplayName("Unlock account SQL should use WITH LOGIN")
    fun testUnlockAccountSql() {
        val sql = dialect.getUnlockAccountSql("testuser", "localhost")
        assertTrue(sql.contains("WITH LOGIN"))
    }

    @Test
    @DisplayName("Flush privileges SQL should return null for PostgreSQL")
    fun testFlushPrivilegesSqlReturnsNull() {
        assertNull(dialect.getFlushPrivilegesSql())
    }

    @Test
    @DisplayName("Set default tablespace should return null for PostgreSQL")
    fun testSetDefaultTablespaceReturnsNull() {
        assertNull(dialect.getSetDefaultTablespaceSql("user", "host", "tablespace"))
    }

    @Test
    @DisplayName("Set tablespace quota should return null for PostgreSQL")
    fun testSetTablespaceQuotaReturnsNull() {
        assertNull(dialect.getSetTablespaceQuotaSql("user", "host", "tablespace", "100M"))
    }

    // ==================== Permission Query Tests ====================

    @Test
    @DisplayName("Format grantee should use lowercase username")
    fun testFormatGrantee() {
        val grantee = dialect.formatGrantee("TestUser", "localhost")
        assertEquals("testuser", grantee)
    }

    @Test
    @DisplayName("Grant SQL should be properly formatted for schema")
    fun testGrantSqlForSchema() {
        val sql = dialect.getGrantSql(listOf("SELECT", "INSERT"), "public", "*", "testuser", "localhost")
        assertTrue(sql.contains("GRANT SELECT, INSERT"))
        assertTrue(sql.contains("ALL TABLES IN SCHEMA \"public\""))
        assertTrue(sql.contains("TO \"testuser\""))
    }

    @Test
    @DisplayName("Grant SQL should be properly formatted for table")
    fun testGrantSqlForTable() {
        val sql = dialect.getGrantSql(listOf("SELECT"), "public", "users", "testuser", "localhost")
        assertTrue(sql.contains("\"public\".\"users\""))
    }

    @Test
    @DisplayName("Revoke SQL should be properly formatted")
    fun testRevokeSql() {
        val sql = dialect.getRevokeSql(listOf("DELETE"), "public", "*", "testuser", "localhost")
        assertTrue(sql.contains("REVOKE DELETE"))
        assertTrue(sql.contains("FROM \"testuser\""))
    }

    @Test
    @DisplayName("Grant SQL should support CONNECT database privilege")
    fun testGrantSqlConnectPrivilege() {
        val sql = dialect.getGrantSql(listOf("CONNECT"), "testdb", "*", "testuser", "localhost")
        assertTrue(sql.contains("GRANT CONNECT ON DATABASE"))
        assertTrue(sql.contains("TO \"testuser\""))
    }

    @Test
    @DisplayName("Grant SQL should support multiple database privileges")
    fun testGrantSqlMultipleDatabasePrivileges() {
        val sql = dialect.getGrantSql(listOf("CONNECT", "CREATE", "TEMPORARY"), "testdb", "*", "testuser", "localhost")
        assertTrue(sql.contains("GRANT CONNECT, CREATE, TEMPORARY ON DATABASE"))
    }

    @Test
    @DisplayName("Grant SQL should handle mixed database and table privileges")
    fun testGrantSqlMixedPrivileges() {
        val sql = dialect.getGrantSql(listOf("CONNECT", "SELECT"), "testdb", "*", "testuser", "localhost")
        // Should contain both database grant and table grant
        assertTrue(sql.contains("ON DATABASE"))
        assertTrue(sql.contains("ALL TABLES IN SCHEMA"))
    }

    @Test
    @DisplayName("Revoke SQL should support database privileges")
    fun testRevokeSqlDatabasePrivileges() {
        val sql = dialect.getRevokeSql(listOf("CONNECT"), "testdb", "*", "testuser", "localhost")
        assertTrue(sql.contains("REVOKE CONNECT ON DATABASE"))
        assertTrue(sql.contains("FROM \"testuser\""))
    }

    @Test
    @DisplayName("Grant SQL should use public schema for empty database on table privileges")
    fun testGrantSqlEmptyDatabaseUsesPublicSchema() {
        val sql = dialect.getGrantSql(listOf("SELECT"), "", "*", "testuser", "localhost")
        assertTrue(sql.contains("ALL TABLES IN SCHEMA \"public\""))
    }

    @Test
    @DisplayName("Show databases query should select from pg_database")
    fun testShowDatabasesQuery() {
        val query = dialect.getShowDatabasesQuery()
        assertTrue(query.contains("pg_database"))
        assertTrue(query.contains("datistemplate = false"))
    }

    // ==================== Table/Index Query Tests ====================

    @Test
    @DisplayName("Create index SQL should be properly formatted")
    fun testCreateIndexSql() {
        val sql = dialect.getCreateIndexSql("public", "users", "idx_name", listOf("name"), false)
        assertTrue(sql.contains("CREATE INDEX"))
        assertTrue(sql.contains("\"idx_name\""))
        assertTrue(sql.contains("\"public\".\"users\""))
    }

    @Test
    @DisplayName("Create unique index SQL should include UNIQUE")
    fun testCreateUniqueIndexSql() {
        val sql = dialect.getCreateIndexSql("public", "users", "idx_email", listOf("email"), true)
        assertTrue(sql.contains("CREATE UNIQUE INDEX"))
    }

    @Test
    @DisplayName("Drop index SQL should use DROP INDEX IF EXISTS")
    fun testDropIndexSql() {
        val sql = dialect.getDropIndexSql("public", "users", "idx_name")
        assertTrue(sql.contains("DROP INDEX IF EXISTS"))
        assertTrue(sql.contains("\"public\".\"idx_name\""))
    }

    // ==================== Tablespace Query Tests ====================

    @Test
    @DisplayName("Create tablespace SQL should use LOCATION")
    fun testCreateTablespaceSql() {
        val sql = dialect.getCreateTablespaceSql("myspace", null, null)
        assertTrue(sql.contains("CREATE TABLESPACE"))
        assertTrue(sql.contains("\"myspace\""))
        assertTrue(sql.contains("LOCATION"))
    }

    @Test
    @DisplayName("Create tablespace SQL with custom location")
    fun testCreateTablespaceSqlWithLocation() {
        val sql = dialect.getCreateTablespaceSql("myspace", "/data/tablespace", null)
        assertTrue(sql.contains("'/data/tablespace'"))
    }

    @Test
    @DisplayName("Drop tablespace SQL should use DROP TABLESPACE IF EXISTS")
    fun testDropTablespaceSql() {
        val sql = dialect.getDropTablespaceSql("myspace")
        assertTrue(sql.contains("DROP TABLESPACE IF EXISTS"))
        assertTrue(sql.contains("\"myspace\""))
    }

    @Test
    @DisplayName("Move table to tablespace SQL should use SET TABLESPACE")
    fun testMoveTableToTablespaceSql() {
        val sql = dialect.getMoveTableToTablespaceSql("public", "users", "myspace")
        assertTrue(sql.contains("ALTER TABLE"))
        assertTrue(sql.contains("SET TABLESPACE"))
        assertTrue(sql.contains("\"myspace\""))
    }

    // ==================== System Schema/User Tests ====================

    @Test
    @DisplayName("System schemas should include PostgreSQL system schemas")
    fun testSystemSchemas() {
        val schemas = dialect.getSystemSchemas()
        assertTrue(schemas.contains("information_schema"))
        assertTrue(schemas.contains("pg_catalog"))
        assertTrue(schemas.contains("pg_toast"))
    }

    @Test
    @DisplayName("System users should include postgres")
    fun testSystemUsers() {
        val users = dialect.getSystemUsers()
        assertTrue(users.contains("postgres"))
    }

    // ==================== Schema Switch Tests ====================

    @Test
    @DisplayName("Switch schema SQL should use SET search_path")
    fun testSwitchSchemaSql() {
        val sql = dialect.getSwitchSchemaSql("myschema")
        assertTrue(sql.contains("SET search_path TO"))
        assertTrue(sql.contains("\"myschema\""))
    }

    @Test
    @DisplayName("Current schema query should use current_schema() function")
    fun testCurrentSchemaQuery() {
        val query = dialect.getCurrentSchemaQuery()
        assertEquals("SELECT current_schema() AS current_schema", query)
    }

    @Test
    @DisplayName("Available schemas query should exclude system schemas")
    fun testAvailableSchemasQuery() {
        val query = dialect.getAvailableSchemasQuery()
        assertTrue(query.contains("schema_name"))
        assertTrue(query.contains("information_schema.schemata"))
        assertTrue(query.contains("'information_schema'"))
        assertTrue(query.contains("'pg_catalog'"))
    }

    // ==================== Password Escaping Tests ====================

    @Test
    @DisplayName("Create user SQL should double single quotes in password")
    fun testCreateUserSqlWithMultipleQuotes() {
        val sql = dialect.getCreateUserSql("testuser", "%", "pa''ss")
        // Input: pa''ss -> escaped: pa''''ss
        assertTrue(sql.contains("''''"))
    }

    @Test
    @DisplayName("Alter user password should escape quotes correctly")
    fun testAlterUserPasswordWithQuote() {
        val sql = dialect.getAlterUserPasswordSql("testuser", "localhost", "test'pass")
        assertTrue(sql.contains("''"))
    }

    // ==================== Query Content Validation Tests ====================

    @Test
    @DisplayName("All accounts query should select from pg_user")
    fun testAllAccountsQuery() {
        val query = dialect.getAllAccountsQuery()
        assertTrue(query.contains("pg_user"))
        assertTrue(query.contains("username"))
        assertTrue(query.contains("account_locked"))
        assertTrue(query.contains("pg_roles"))
    }

    @Test
    @DisplayName("Account count query should count from pg_user")
    fun testAccountCountQuery() {
        val query = dialect.getAccountCountQuery()
        assertTrue(query.contains("COUNT(*)"))
        assertTrue(query.contains("pg_user"))
    }

    @Test
    @DisplayName("Paginated accounts query should include LIMIT and OFFSET")
    fun testPaginatedAccountsQuery() {
        val query = dialect.getPaginatedAccountsQuery()
        assertTrue(query.contains("LIMIT"))
        assertTrue(query.contains("OFFSET"))
    }

    @Test
    @DisplayName("Session stats query should calculate active and idle counts")
    fun testSessionStatsQuery() {
        val query = dialect.getSessionStatsQuery()
        assertTrue(query.contains("COUNT(*)"))
        assertTrue(query.contains("active"))
        assertTrue(query.contains("sleeping"))
        assertTrue(query.contains("long_running"))
        assertTrue(query.contains("pg_stat_activity"))
    }

    @Test
    @DisplayName("Long running queries query should have time threshold placeholder")
    fun testLongRunningQueriesQuery() {
        val query = dialect.getLongRunningQueriesQuery()
        assertTrue(query.contains("> ?"))
        assertTrue(query.contains("state = 'active'"))
    }

    @Test
    @DisplayName("Password expiry query should select from pg_user")
    fun testPasswordExpiryQuery() {
        val query = dialect.getPasswordExpiryQuery()
        assertTrue(query.contains("pg_user"))
        assertTrue(query.contains("days_until_expiry"))
        assertTrue(query.contains("is_expired"))
    }

    @Test
    @DisplayName("Password expiry days query should return days until expiry")
    fun testPasswordExpiryDaysQuery() {
        val query = dialect.getPasswordExpiryDaysQuery()
        assertTrue(query.contains("days_until_expiry"))
        assertTrue(query.contains("EXTRACT"))
    }

    @Test
    @DisplayName("Expiring accounts query should filter by days threshold")
    fun testExpiringAccountsQuery() {
        val query = dialect.getExpiringAccountsQuery()
        assertTrue(query.contains("days_until_expiry"))
        assertTrue(query.contains("< ?"))
        assertTrue(query.contains("valuntil"))
    }

    @Test
    @DisplayName("Databases query should aggregate table info from schemata")
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
    @DisplayName("Indexes query should use pg_index")
    fun testIndexesQuery() {
        val query = dialect.getIndexesQuery()
        assertTrue(query.contains("pg_index"))
        assertTrue(query.contains("STRING_AGG"))
        assertTrue(query.contains("GROUP BY"))
    }

    @Test
    @DisplayName("Schema privileges query should use information_schema")
    fun testSchemaPrivilegesQuery() {
        val query = dialect.getSchemaPrivilegesQuery()
        assertTrue(query.contains("information_schema.table_privileges"))
        assertTrue(query.contains("grantee = ?"))
    }

    @Test
    @DisplayName("Table privileges query should use information_schema")
    fun testTablePrivilegesQuery() {
        val query = dialect.getTablePrivilegesQuery()
        assertTrue(query.contains("information_schema.table_privileges"))
        assertTrue(query.contains("grantee = ?"))
    }

    @Test
    @DisplayName("Tablespaces query should select from pg_tablespace")
    fun testTablespacesQuery() {
        val query = dialect.getTablespacesQuery()
        assertTrue(query.contains("pg_tablespace"))
        assertTrue(query.contains("pg_tablespace_size"))
    }

    @Test
    @DisplayName("Tables in tablespace query should use pg_class")
    fun testTablesInTablespaceQuery() {
        val query = dialect.getTablesInTablespaceQuery()
        assertTrue(query.contains("pg_class"))
        assertTrue(query.contains("pg_tablespace"))
        assertTrue(query.contains("t.spcname = ?"))
    }

    @Test
    @DisplayName("Admin check query should check rolsuper")
    fun testAdminCheckQuery() {
        val query = dialect.getAdminCheckQuery()
        assertTrue(query.contains("pg_roles"))
        assertTrue(query.contains("rolsuper = true"))
    }

    // ==================== Select With Limit Tests ====================

    @Test
    @DisplayName("Select with limit should use LIMIT clause")
    fun testSelectWithLimit() {
        val sql = dialect.getSelectWithLimitSql("\"public\".\"users\"", 100)
        assertEquals("SELECT * FROM \"public\".\"users\" LIMIT 100", sql)
    }

    @Test
    @DisplayName("Select with limit should handle large limits")
    fun testSelectWithLargeLimit() {
        val sql = dialect.getSelectWithLimitSql("\"schema\".\"table\"", 50000)
        assertTrue(sql.contains("LIMIT 50000"))
    }

    // ==================== Multi-Column Index Tests ====================

    @Test
    @DisplayName("Create index with multiple columns should include all columns")
    fun testCreateIndexMultipleColumns() {
        val sql = dialect.getCreateIndexSql("public", "users", "idx_name_email",
            listOf("name", "email", "created_at"), false)
        assertTrue(sql.contains("\"name\", \"email\", \"created_at\""))
    }

    @Test
    @DisplayName("Create unique index with multiple columns")
    fun testCreateUniqueIndexMultipleColumns() {
        val sql = dialect.getCreateIndexSql("public", "users", "idx_uniq",
            listOf("col1", "col2"), true)
        assertTrue(sql.contains("CREATE UNIQUE INDEX"))
        assertTrue(sql.contains("\"col1\", \"col2\""))
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Quote identifier with special characters")
    fun testQuoteIdentifierSpecialChars() {
        val result = dialect.quoteIdentifier("table-name")
        assertEquals("\"table-name\"", result)
    }

    @Test
    @DisplayName("Quote identifier with spaces")
    fun testQuoteIdentifierWithSpaces() {
        val result = dialect.quoteIdentifier("table name")
        assertEquals("\"table name\"", result)
    }

    @Test
    @DisplayName("Format grantee should lowercase mixed case username")
    fun testFormatGranteeMixedCase() {
        val grantee = dialect.formatGrantee("AdminUser", "localhost")
        assertEquals("adminuser", grantee)
    }

    @Test
    @DisplayName("JDBC URL with custom port")
    fun testJdbcUrlCustomPort() {
        val url = dialect.getJdbcUrl("192.168.1.100", 15432, "production")
        assertEquals("jdbc:postgresql://192.168.1.100:15432/production", url)
    }

    @Test
    @DisplayName("Kill session with different PID values")
    fun testKillSessionVariousPids() {
        assertEquals("SELECT pg_terminate_backend(1)", dialect.getKillSessionSql(1, null))
        assertEquals("SELECT pg_terminate_backend(999999)", dialect.getKillSessionSql(999999, null))
        assertEquals("SELECT pg_terminate_backend(0)", dialect.getKillSessionSql(0, null))
    }

    @Test
    @DisplayName("Grant SQL with empty database should use CURRENT_DATABASE()")
    fun testGrantSqlEmptyDatabaseForDbPrivilege() {
        val sql = dialect.getGrantSql(listOf("CONNECT"), "", "*", "testuser", "localhost")
        assertTrue(sql.contains("ON DATABASE CURRENT_DATABASE()"))
    }

    @Test
    @DisplayName("Revoke SQL with TEMPORARY privilege")
    fun testRevokeSqlTemporaryPrivilege() {
        val sql = dialect.getRevokeSql(listOf("TEMPORARY"), "testdb", "*", "testuser", "localhost")
        assertTrue(sql.contains("REVOKE TEMPORARY ON DATABASE"))
    }

    @Test
    @DisplayName("Grant SQL should handle TEMP as database privilege")
    fun testGrantSqlTempPrivilege() {
        val sql = dialect.getGrantSql(listOf("TEMP"), "testdb", "*", "testuser", "localhost")
        assertTrue(sql.contains("ON DATABASE"))
    }

    @Test
    @DisplayName("Grant SQL for specific table should use full qualified name")
    fun testGrantSqlSpecificTable() {
        val sql = dialect.getGrantSql(listOf("SELECT", "UPDATE"), "myschema", "mytable", "user1", "localhost")
        assertTrue(sql.contains("\"myschema\".\"mytable\""))
        assertTrue(sql.contains("TO \"user1\""))
    }

    @Test
    @DisplayName("Revoke SQL for specific table should use full qualified name")
    fun testRevokeSqlSpecificTable() {
        val sql = dialect.getRevokeSql(listOf("DELETE"), "myschema", "mytable", "user1", "localhost")
        assertTrue(sql.contains("\"myschema\".\"mytable\""))
        assertTrue(sql.contains("FROM \"user1\""))
    }

    @Test
    @DisplayName("Create tablespace with custom location")
    fun testCreateTablespaceFullLocation() {
        val sql = dialect.getCreateTablespaceSql("myspace", "/var/lib/pgsql/myspace", null)
        assertTrue(sql.contains("LOCATION '/var/lib/pgsql/myspace'"))
    }

    @Test
    @DisplayName("Drop index should include schema name")
    fun testDropIndexWithSchema() {
        val sql = dialect.getDropIndexSql("myschema", "mytable", "idx_test")
        assertTrue(sql.contains("DROP INDEX IF EXISTS \"myschema\".\"idx_test\""))
    }

    // ==================== Password Expiry Date Calculation Tests ====================

    @Test
    @DisplayName("Password expire SQL should calculate future date")
    fun testPasswordExpireFutureDate() {
        val sql = dialect.getAlterUserPasswordExpireSql("testuser", "localhost", 30)
        assertTrue(sql.contains("VALID UNTIL"))
        // Should contain a future date in format YYYY-MM-DD HH:MM:SS
        assertTrue(sql.matches(Regex(".*VALID UNTIL '\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}'.*")))
    }

    @Test
    @DisplayName("Password expire SQL with zero days should set immediate expiry")
    fun testPasswordExpireZeroDays() {
        val sql = dialect.getAlterUserPasswordExpireSql("testuser", "localhost", 0)
        assertTrue(sql.contains("VALID UNTIL"))
    }
}
