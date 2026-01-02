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
    @DisplayName("Password expire SQL should use VALID UNTIL")
    fun testPasswordExpireSql() {
        val sql = dialect.getAlterUserPasswordExpireSql("testuser", "localhost", 90)
        assertTrue(sql.contains("ALTER USER"))
        assertTrue(sql.contains("VALID UNTIL"))
        assertTrue(sql.contains("90 days"))
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
}
