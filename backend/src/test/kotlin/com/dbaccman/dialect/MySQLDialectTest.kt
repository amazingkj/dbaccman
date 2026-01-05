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
}
