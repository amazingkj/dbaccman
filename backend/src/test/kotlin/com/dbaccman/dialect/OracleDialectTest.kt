package com.dbaccman.dialect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows

class OracleDialectTest {

    private lateinit var dialect: OracleDialect

    @BeforeEach
    fun setUp() {
        dialect = OracleDialect()
    }

    // ==================== Connection Settings Tests ====================

    @Test
    @DisplayName("Oracle dialect type should be ORACLE")
    fun testDialectType() {
        assertEquals(DatabaseType.ORACLE, dialect.type)
    }

    @Test
    @DisplayName("JDBC URL should be correctly formatted with service name")
    fun testJdbcUrlWithDatabase() {
        val url = dialect.getJdbcUrl("localhost", 1521, "ORCL")
        assertEquals("jdbc:oracle:thin:@//localhost:1521/ORCL", url)
    }

    @Test
    @DisplayName("JDBC URL should use ORCL as default service name")
    fun testJdbcUrlWithoutDatabase() {
        val url = dialect.getJdbcUrl("localhost", 1521, null)
        assertEquals("jdbc:oracle:thin:@//localhost:1521/ORCL", url)
    }

    @Test
    @DisplayName("Driver class name should be Oracle JDBC driver")
    fun testDriverClassName() {
        assertEquals("oracle.jdbc.OracleDriver", dialect.getDriverClassName())
    }

    @Test
    @DisplayName("Connection test query should be SELECT 1 FROM DUAL")
    fun testConnectionTestQuery() {
        assertEquals("SELECT 1 FROM DUAL", dialect.getConnectionTestQuery())
    }

    // ==================== Identifier Quoting Tests ====================

    @Test
    @DisplayName("Identifier should be quoted with double quotes")
    fun testQuoteIdentifier() {
        assertEquals("\"USERNAME\"", dialect.quoteIdentifier("USERNAME"))
    }

    @Test
    @DisplayName("Double quotes in identifier should be escaped")
    fun testQuoteIdentifierWithDoubleQuote() {
        assertEquals("\"TEST\"\"NAME\"", dialect.quoteIdentifier("TEST\"NAME"))
    }

    // ==================== Session Query Tests ====================

    @Test
    @DisplayName("Kill session SQL should require serialNum")
    fun testKillSessionSqlRequiresSerialNum() {
        val exception = assertThrows<IllegalArgumentException> {
            dialect.getKillSessionSql(123, null)
        }
        assertTrue(exception.message!!.contains("SERIAL#"))
    }

    @Test
    @DisplayName("Kill session SQL should use ALTER SYSTEM KILL SESSION")
    fun testKillSessionSql() {
        val sql = dialect.getKillSessionSql(123, 456)
        assertEquals("ALTER SYSTEM KILL SESSION '123,456' IMMEDIATE", sql)
    }

    @Test
    @DisplayName("Kill query SQL should require serialNum")
    fun testKillQuerySqlRequiresSerialNum() {
        val exception = assertThrows<IllegalArgumentException> {
            dialect.getKillQuerySql(123, null)
        }
        assertTrue(exception.message!!.contains("SERIAL#"))
    }

    @Test
    @DisplayName("Kill query SQL should use ALTER SYSTEM CANCEL SQL")
    fun testKillQuerySql() {
        val sql = dialect.getKillQuerySql(123, 456)
        assertEquals("ALTER SYSTEM CANCEL SQL '123,456'", sql)
    }

    @Test
    @DisplayName("Active sessions query should select from V-SESSION")
    fun testActiveSessionsQuery() {
        val query = dialect.getActiveSessionsQuery()
        assertTrue(query.contains("V\$SESSION"))
        assertTrue(query.contains("pid"))
        assertTrue(query.contains("serial_num"))
        assertTrue(query.contains("sess_user"))
    }

    // ==================== Account Query Tests ====================

    @Test
    @DisplayName("Create user SQL should be properly formatted")
    fun testCreateUserSql() {
        val sql = dialect.getCreateUserSql("TESTUSER", "localhost", "password123")
        assertTrue(sql.contains("CREATE USER"))
        assertTrue(sql.contains("\"TESTUSER\""))
        assertTrue(sql.contains("IDENTIFIED BY"))
    }

    @Test
    @DisplayName("Alter user password SQL should be properly formatted")
    fun testAlterUserPasswordSql() {
        val sql = dialect.getAlterUserPasswordSql("TESTUSER", "localhost", "newpass")
        assertTrue(sql.contains("ALTER USER"))
        assertTrue(sql.contains("\"TESTUSER\""))
        assertTrue(sql.contains("IDENTIFIED BY"))
    }

    @Test
    @DisplayName("Password expire SQL should use PROFILE DEFAULT")
    fun testPasswordExpireSql() {
        val sql = dialect.getAlterUserPasswordExpireSql("TESTUSER", "localhost", 90)
        assertTrue(sql.contains("ALTER USER"))
        assertTrue(sql.contains("PROFILE DEFAULT"))
    }

    @Test
    @DisplayName("Expire password immediately SQL should use PASSWORD EXPIRE")
    fun testExpirePasswordSql() {
        val sql = dialect.getExpirePasswordSql("TESTUSER", "localhost")
        assertTrue(sql.contains("PASSWORD EXPIRE"))
    }

    @Test
    @DisplayName("Drop user SQL should use DROP USER CASCADE")
    fun testDropUserSql() {
        val sql = dialect.getDropUserSql("TESTUSER", "localhost")
        assertTrue(sql.contains("DROP USER"))
        assertTrue(sql.contains("\"TESTUSER\""))
        assertTrue(sql.contains("CASCADE"))
    }

    @Test
    @DisplayName("Unlock account SQL should use ACCOUNT UNLOCK")
    fun testUnlockAccountSql() {
        val sql = dialect.getUnlockAccountSql("TESTUSER", "localhost")
        assertTrue(sql.contains("ACCOUNT UNLOCK"))
    }

    @Test
    @DisplayName("Flush privileges SQL should return null for Oracle")
    fun testFlushPrivilegesSqlReturnsNull() {
        assertNull(dialect.getFlushPrivilegesSql())
    }

    @Test
    @DisplayName("Set default tablespace should use ALTER USER DEFAULT TABLESPACE")
    fun testSetDefaultTablespaceSql() {
        val sql = dialect.getSetDefaultTablespaceSql("TESTUSER", "localhost", "USERS")
        assertTrue(sql!!.contains("ALTER USER"))
        assertTrue(sql.contains("DEFAULT TABLESPACE"))
        assertTrue(sql.contains("\"USERS\""))
    }

    @Test
    @DisplayName("Set tablespace quota should use ALTER USER QUOTA")
    fun testSetTablespaceQuotaSql() {
        val sql = dialect.getSetTablespaceQuotaSql("TESTUSER", "localhost", "USERS", "UNLIMITED")
        assertTrue(sql!!.contains("ALTER USER"))
        assertTrue(sql.contains("QUOTA UNLIMITED ON"))
        assertTrue(sql.contains("\"USERS\""))
    }

    // ==================== Oracle-specific Methods Tests ====================

    @Test
    @DisplayName("Container name SQL should query SYS_CONTEXT")
    fun testContainerNameSql() {
        val sql = dialect.getContainerNameSql()
        assertTrue(sql.contains("SYS_CONTEXT"))
        assertTrue(sql.contains("CON_NAME"))
    }

    @Test
    @DisplayName("Enable local user SQL should set _ORACLE_SCRIPT")
    fun testEnableLocalUserSql() {
        val sql = dialect.getEnableLocalUserSql()
        assertTrue(sql.contains("_ORACLE_SCRIPT"))
        assertTrue(sql.contains("true"))
    }

    @Test
    @DisplayName("Disable local user SQL should unset _ORACLE_SCRIPT")
    fun testDisableLocalUserSql() {
        val sql = dialect.getDisableLocalUserSql()
        assertTrue(sql.contains("_ORACLE_SCRIPT"))
        assertTrue(sql.contains("false"))
    }

    // ==================== Permission Query Tests ====================

    @Test
    @DisplayName("Format grantee should use uppercase username")
    fun testFormatGrantee() {
        val grantee = dialect.formatGrantee("testuser", "localhost")
        assertEquals("TESTUSER", grantee)
    }

    @Test
    @DisplayName("Grant SQL should be properly formatted for ANY TABLE")
    fun testGrantSqlForSchema() {
        val sql = dialect.getGrantSql(listOf("SELECT", "INSERT"), "TESTSCHEMA", "*", "TESTUSER", "localhost")
        assertTrue(sql.contains("GRANT SELECT, INSERT"))
        assertTrue(sql.contains("ANY TABLE"))
        assertTrue(sql.contains("TO \"TESTUSER\""))
    }

    @Test
    @DisplayName("Grant SQL should be properly formatted for table")
    fun testGrantSqlForTable() {
        val sql = dialect.getGrantSql(listOf("SELECT"), "HR", "EMPLOYEES", "TESTUSER", "localhost")
        assertTrue(sql.contains("\"HR\".\"EMPLOYEES\""))
    }

    @Test
    @DisplayName("Revoke SQL should be properly formatted")
    fun testRevokeSql() {
        val sql = dialect.getRevokeSql(listOf("DELETE"), "HR", "*", "TESTUSER", "localhost")
        assertTrue(sql.contains("REVOKE DELETE"))
        assertTrue(sql.contains("FROM \"TESTUSER\""))
    }

    @Test
    @DisplayName("Show databases query should select from DBA_USERS")
    fun testShowDatabasesQuery() {
        val query = dialect.getShowDatabasesQuery()
        assertTrue(query.contains("DBA_USERS"))
    }

    // ==================== Table/Index Query Tests ====================

    @Test
    @DisplayName("Create index SQL should be properly formatted")
    fun testCreateIndexSql() {
        val sql = dialect.getCreateIndexSql("HR", "EMPLOYEES", "IDX_NAME", listOf("LAST_NAME"), false)
        assertTrue(sql.contains("CREATE INDEX"))
        assertTrue(sql.contains("\"IDX_NAME\""))
        assertTrue(sql.contains("\"HR\".\"EMPLOYEES\""))
    }

    @Test
    @DisplayName("Create unique index SQL should include UNIQUE")
    fun testCreateUniqueIndexSql() {
        val sql = dialect.getCreateIndexSql("HR", "EMPLOYEES", "IDX_EMAIL", listOf("EMAIL"), true)
        assertTrue(sql.contains("CREATE UNIQUE INDEX"))
    }

    @Test
    @DisplayName("Drop index SQL should handle index name with owner prefix")
    fun testDropIndexSqlWithOwner() {
        val sql = dialect.getDropIndexSql("HR", "EMPLOYEES", "HR.IDX_NAME")
        assertTrue(sql.contains("DROP INDEX"))
        assertTrue(sql.contains("\"HR\".\"IDX_NAME\""))
    }

    @Test
    @DisplayName("Drop index SQL should use schema prefix when no owner in name")
    fun testDropIndexSqlWithoutOwner() {
        val sql = dialect.getDropIndexSql("HR", "EMPLOYEES", "IDX_NAME")
        assertTrue(sql.contains("\"HR\".\"IDX_NAME\""))
    }

    // ==================== Tablespace Query Tests ====================

    @Test
    @DisplayName("Create tablespace SQL should use DATAFILE")
    fun testCreateTablespaceSql() {
        val sql = dialect.getCreateTablespaceSql("MYSPACE", null, null)
        assertTrue(sql.contains("CREATE TABLESPACE"))
        assertTrue(sql.contains("\"MYSPACE\""))
        assertTrue(sql.contains("DATAFILE"))
        assertTrue(sql.contains("AUTOEXTEND ON"))
    }

    @Test
    @DisplayName("Create tablespace SQL with custom datafile")
    fun testCreateTablespaceSqlWithDatafile() {
        val sql = dialect.getCreateTablespaceSql("MYSPACE", "/oradata/myspace.dbf", null)
        assertTrue(sql.contains("'/oradata/myspace.dbf'"))
    }

    @Test
    @DisplayName("Drop tablespace SQL should include INCLUDING CONTENTS AND DATAFILES")
    fun testDropTablespaceSql() {
        val sql = dialect.getDropTablespaceSql("MYSPACE")
        assertTrue(sql.contains("DROP TABLESPACE"))
        assertTrue(sql.contains("INCLUDING CONTENTS AND DATAFILES"))
    }

    @Test
    @DisplayName("Move table to tablespace SQL should use MOVE TABLESPACE")
    fun testMoveTableToTablespaceSql() {
        val sql = dialect.getMoveTableToTablespaceSql("HR", "EMPLOYEES", "NEWSPACE")
        assertTrue(sql.contains("ALTER TABLE"))
        assertTrue(sql.contains("MOVE TABLESPACE"))
        assertTrue(sql.contains("\"NEWSPACE\""))
    }

    // ==================== System Schema/User Tests ====================

    @Test
    @DisplayName("System schemas should include Oracle system tablespaces")
    fun testSystemSchemas() {
        val schemas = dialect.getSystemSchemas()
        assertTrue(schemas.contains("SYSTEM"))
        assertTrue(schemas.contains("SYS"))
        assertTrue(schemas.contains("SYSAUX"))
        assertTrue(schemas.contains("TEMP"))
    }

    @Test
    @DisplayName("System users should include Oracle system users")
    fun testSystemUsers() {
        val users = dialect.getSystemUsers()
        assertTrue(users.contains("SYS"))
        assertTrue(users.contains("SYSTEM"))
        assertTrue(users.contains("DBSNMP"))
        assertTrue(users.contains("XDB"))
    }

    // ==================== Schema Switch Tests ====================

    @Test
    @DisplayName("Switch schema SQL should use ALTER SESSION SET CURRENT_SCHEMA")
    fun testSwitchSchemaSql() {
        val sql = dialect.getSwitchSchemaSql("HR")
        assertTrue(sql.contains("ALTER SESSION SET CURRENT_SCHEMA"))
        assertTrue(sql.contains("\"HR\""))
    }
}
