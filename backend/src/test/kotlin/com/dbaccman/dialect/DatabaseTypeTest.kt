package com.dbaccman.dialect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows

class DatabaseTypeTest {

    // ==================== Enum Value Tests ====================

    @Test
    @DisplayName("DatabaseType should have MYSQL value")
    fun testMySQLExists() {
        assertNotNull(DatabaseType.MYSQL)
    }

    @Test
    @DisplayName("DatabaseType should have ORACLE value")
    fun testOracleExists() {
        assertNotNull(DatabaseType.ORACLE)
    }

    @Test
    @DisplayName("DatabaseType should have POSTGRESQL value")
    fun testPostgreSQLExists() {
        assertNotNull(DatabaseType.POSTGRESQL)
    }

    @Test
    @DisplayName("DatabaseType should have exactly 3 values")
    fun testEnumCount() {
        assertEquals(3, DatabaseType.entries.size)
    }

    // ==================== fromString Tests ====================

    @Test
    @DisplayName("fromString should parse MYSQL")
    fun testFromStringMySQL() {
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromString("MYSQL"))
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromString("mysql"))
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromString("MySQL"))
    }

    @Test
    @DisplayName("fromString should parse MARIADB as MYSQL")
    fun testFromStringMariaDB() {
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromString("MARIADB"))
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromString("mariadb"))
    }

    @Test
    @DisplayName("fromString should parse ORACLE")
    fun testFromStringOracle() {
        assertEquals(DatabaseType.ORACLE, DatabaseType.fromString("ORACLE"))
        assertEquals(DatabaseType.ORACLE, DatabaseType.fromString("oracle"))
        assertEquals(DatabaseType.ORACLE, DatabaseType.fromString("Oracle"))
    }

    @Test
    @DisplayName("fromString should parse POSTGRESQL")
    fun testFromStringPostgreSQL() {
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString("POSTGRESQL"))
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString("postgresql"))
    }

    @Test
    @DisplayName("fromString should parse POSTGRES alias")
    fun testFromStringPostgres() {
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString("POSTGRES"))
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString("postgres"))
    }

    @Test
    @DisplayName("fromString should parse PG alias")
    fun testFromStringPG() {
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString("PG"))
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString("pg"))
    }

    @Test
    @DisplayName("fromString should throw exception for unsupported type")
    fun testFromStringUnsupported() {
        val exception = assertThrows<IllegalArgumentException> {
            DatabaseType.fromString("SQLSERVER")
        }
        assertTrue(exception.message!!.contains("Unsupported database type"))
    }

    // ==================== Display Name Tests ====================

    @Test
    @DisplayName("MySQL display name should be MySQL")
    fun testMySQLDisplayName() {
        assertEquals("MySQL", DatabaseType.MYSQL.displayName)
    }

    @Test
    @DisplayName("Oracle display name should be Oracle")
    fun testOracleDisplayName() {
        assertEquals("Oracle", DatabaseType.ORACLE.displayName)
    }

    @Test
    @DisplayName("PostgreSQL display name should be PostgreSQL")
    fun testPostgreSQLDisplayName() {
        assertEquals("PostgreSQL", DatabaseType.POSTGRESQL.displayName)
    }

    // ==================== Default Port Tests ====================

    @Test
    @DisplayName("MySQL default port should be 3306")
    fun testMySQLDefaultPort() {
        assertEquals(3306, DatabaseType.MYSQL.defaultPort)
    }

    @Test
    @DisplayName("Oracle default port should be 1521")
    fun testOracleDefaultPort() {
        assertEquals(1521, DatabaseType.ORACLE.defaultPort)
    }

    @Test
    @DisplayName("PostgreSQL default port should be 5432")
    fun testPostgreSQLDefaultPort() {
        assertEquals(5432, DatabaseType.POSTGRESQL.defaultPort)
    }

    // ==================== Enum Consistency Tests ====================

    @Test
    @DisplayName("All database types should have unique display names")
    fun testUniqueDisplayNames() {
        val displayNames = DatabaseType.entries.map { it.displayName }
        assertEquals(displayNames.size, displayNames.distinct().size)
    }

    @Test
    @DisplayName("All database types should have unique default ports")
    fun testUniqueDefaultPorts() {
        val ports = DatabaseType.entries.map { it.defaultPort }
        assertEquals(ports.size, ports.distinct().size)
    }

    @Test
    @DisplayName("All database types should have valid ports")
    fun testValidPorts() {
        DatabaseType.entries.forEach { type ->
            assertTrue(type.defaultPort > 0)
            assertTrue(type.defaultPort < 65536)
        }
    }

    @Test
    @DisplayName("All database types should have non-empty display names")
    fun testNonEmptyDisplayNames() {
        DatabaseType.entries.forEach { type ->
            assertTrue(type.displayName.isNotBlank())
        }
    }
}
