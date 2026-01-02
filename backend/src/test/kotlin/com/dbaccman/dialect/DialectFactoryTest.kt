package com.dbaccman.dialect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows

class DialectFactoryTest {

    @Test
    @DisplayName("DialectFactory should return MySQLDialect for MYSQL type")
    fun testGetMySQLDialect() {
        val dialect = DialectFactory.getDialect(DatabaseType.MYSQL)
        assertNotNull(dialect)
        assertTrue(dialect is MySQLDialect)
        assertEquals(DatabaseType.MYSQL, dialect.type)
    }

    @Test
    @DisplayName("DialectFactory should return PostgreSQLDialect for POSTGRESQL type")
    fun testGetPostgreSQLDialect() {
        val dialect = DialectFactory.getDialect(DatabaseType.POSTGRESQL)
        assertNotNull(dialect)
        assertTrue(dialect is PostgreSQLDialect)
        assertEquals(DatabaseType.POSTGRESQL, dialect.type)
    }

    @Test
    @DisplayName("DialectFactory should return OracleDialect for ORACLE type")
    fun testGetOracleDialect() {
        val dialect = DialectFactory.getDialect(DatabaseType.ORACLE)
        assertNotNull(dialect)
        assertTrue(dialect is OracleDialect)
        assertEquals(DatabaseType.ORACLE, dialect.type)
    }

    @Test
    @DisplayName("DialectFactory should support MYSQL type")
    fun testIsSupportedMySQL() {
        assertTrue(DialectFactory.isSupported(DatabaseType.MYSQL))
    }

    @Test
    @DisplayName("DialectFactory should support POSTGRESQL type")
    fun testIsSupportedPostgreSQL() {
        assertTrue(DialectFactory.isSupported(DatabaseType.POSTGRESQL))
    }

    @Test
    @DisplayName("DialectFactory should support ORACLE type")
    fun testIsSupportedOracle() {
        assertTrue(DialectFactory.isSupported(DatabaseType.ORACLE))
    }

    @Test
    @DisplayName("DialectFactory should return all supported types")
    fun testGetSupportedTypes() {
        val types = DialectFactory.getSupportedTypes()
        assertNotNull(types)
        assertTrue(types.contains(DatabaseType.MYSQL))
        assertTrue(types.contains(DatabaseType.POSTGRESQL))
        assertTrue(types.contains(DatabaseType.ORACLE))
        assertEquals(3, types.size)
    }

    @Test
    @DisplayName("DialectFactory should return same dialect instance")
    fun testDialectSingleton() {
        val dialect1 = DialectFactory.getDialect(DatabaseType.MYSQL)
        val dialect2 = DialectFactory.getDialect(DatabaseType.MYSQL)
        assertSame(dialect1, dialect2)
    }

    @Test
    @DisplayName("Each dialect type should be unique")
    fun testDialectsAreDistinct() {
        val mysqlDialect = DialectFactory.getDialect(DatabaseType.MYSQL)
        val postgresDialect = DialectFactory.getDialect(DatabaseType.POSTGRESQL)
        val oracleDialect = DialectFactory.getDialect(DatabaseType.ORACLE)

        assertNotSame(mysqlDialect, postgresDialect)
        assertNotSame(mysqlDialect, oracleDialect)
        assertNotSame(postgresDialect, oracleDialect)
    }

    @Test
    @DisplayName("Dialects should implement DatabaseDialect interface")
    fun testDialectsImplementInterface() {
        val types = DialectFactory.getSupportedTypes()
        types.forEach { type ->
            val dialect = DialectFactory.getDialect(type)
            assertTrue(dialect is DatabaseDialect)
        }
    }
}
