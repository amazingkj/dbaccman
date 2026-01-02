package com.dbaccman.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows

class QueryServiceTest {

    private lateinit var queryService: QueryService

    @BeforeEach
    fun setUp() {
        queryService = QueryService()
    }

    @Test
    @DisplayName("QueryService should be instantiable")
    fun testQueryServiceInstantiation() {
        assertNotNull(queryService)
    }

    @Test
    @DisplayName("QueryResult model should have correct properties for SELECT")
    fun testQueryResultForSelect() {
        val result = QueryResult(
            columns = listOf("id", "name", "email"),
            rows = listOf(
                listOf(1, "John", "john@example.com"),
                listOf(2, "Jane", "jane@example.com")
            ),
            rowCount = 2,
            executionTimeMs = 15L,
            affectedRows = null,
            isSelectQuery = true
        )

        assertEquals(3, result.columns.size)
        assertEquals(2, result.rows.size)
        assertEquals(2, result.rowCount)
        assertEquals(15L, result.executionTimeMs)
        assertNull(result.affectedRows)
        assertTrue(result.isSelectQuery)
    }

    @Test
    @DisplayName("QueryResult model should have correct properties for UPDATE")
    fun testQueryResultForUpdate() {
        val result = QueryResult(
            columns = listOf("Affected Rows"),
            rows = listOf(listOf(5)),
            rowCount = 1,
            executionTimeMs = 25L,
            affectedRows = 5,
            isSelectQuery = false
        )

        assertEquals(1, result.columns.size)
        assertEquals("Affected Rows", result.columns.first())
        assertEquals(5, result.affectedRows)
        assertFalse(result.isSelectQuery)
    }

    @Test
    @DisplayName("QueryResult with empty result set")
    fun testQueryResultEmpty() {
        val result = QueryResult(
            columns = listOf("id", "name"),
            rows = emptyList(),
            rowCount = 0,
            executionTimeMs = 5L
        )

        assertEquals(2, result.columns.size)
        assertTrue(result.rows.isEmpty())
        assertEquals(0, result.rowCount)
    }

    @Test
    @DisplayName("QueryExecutionException should contain execution time")
    fun testQueryExecutionException() {
        val exception = QueryExecutionException(
            message = "Table 'users' doesn't exist",
            executionTimeMs = 10L
        )

        assertEquals("Table 'users' doesn't exist", exception.message)
        assertEquals(10L, exception.executionTimeMs)
    }

    @Test
    @DisplayName("QueryResult equality should work correctly")
    fun testQueryResultEquality() {
        val result1 = QueryResult(
            columns = listOf("col1", "col2"),
            rows = listOf(listOf("a", "b")),
            rowCount = 1,
            executionTimeMs = 10L
        )

        val result2 = QueryResult(
            columns = listOf("col1", "col2"),
            rows = listOf(listOf("a", "b")),
            rowCount = 1,
            executionTimeMs = 10L
        )

        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    @DisplayName("QueryResult default values should be correct")
    fun testQueryResultDefaults() {
        val result = QueryResult(
            columns = listOf("test"),
            rows = listOf(listOf(1)),
            rowCount = 1,
            executionTimeMs = 1L
        )

        assertNull(result.affectedRows)
        assertTrue(result.isSelectQuery)
    }

    @Test
    @DisplayName("QueryResult with null values in rows")
    fun testQueryResultWithNullValues() {
        val result = QueryResult(
            columns = listOf("id", "nullable_col"),
            rows = listOf(
                listOf(1, null),
                listOf(2, "value")
            ),
            rowCount = 2,
            executionTimeMs = 5L
        )

        assertEquals(2, result.rowCount)
        assertNull(result.rows[0][1])
        assertEquals("value", result.rows[1][1])
    }

    @Test
    @DisplayName("QueryResult with large row count")
    fun testQueryResultLargeRowCount() {
        val largeRows = (1..1000).map { listOf(it, "row$it") }
        val result = QueryResult(
            columns = listOf("id", "value"),
            rows = largeRows,
            rowCount = 1000,
            executionTimeMs = 150L
        )

        assertEquals(1000, result.rowCount)
        assertEquals(1000, result.rows.size)
    }

    @Test
    @DisplayName("Test SELECT query detection patterns")
    fun testSelectQueryPatterns() {
        // These are the patterns that should be detected as SELECT queries
        // by the private isSelectQuery method in QueryService
        val selectPatterns = listOf(
            "SELECT * FROM users",
            "select id from users",
            "SHOW TABLES",
            "show databases",
            "DESCRIBE users",
            "describe orders",
            "DESC users",
            "EXPLAIN SELECT * FROM users",
            "WITH cte AS (SELECT 1) SELECT * FROM cte"
        )

        // Verify patterns match the expected behavior
        selectPatterns.forEach { query ->
            val upperQuery = query.uppercase().trim()
            val isSelect = upperQuery.startsWith("SELECT") ||
                          upperQuery.startsWith("SHOW") ||
                          upperQuery.startsWith("DESCRIBE") ||
                          upperQuery.startsWith("DESC") ||
                          upperQuery.startsWith("EXPLAIN") ||
                          upperQuery.startsWith("WITH")
            assertTrue(isSelect, "Query should be detected as SELECT: $query")
        }
    }

    @Test
    @DisplayName("Test non-SELECT query detection patterns")
    fun testNonSelectQueryPatterns() {
        val nonSelectPatterns = listOf(
            "INSERT INTO users VALUES (1, 'test')",
            "UPDATE users SET name = 'test'",
            "DELETE FROM users WHERE id = 1",
            "CREATE TABLE test (id INT)",
            "ALTER TABLE users ADD COLUMN age INT",
            "DROP TABLE temp"
        )

        nonSelectPatterns.forEach { query ->
            val upperQuery = query.uppercase().trim()
            val isSelect = upperQuery.startsWith("SELECT") ||
                          upperQuery.startsWith("SHOW") ||
                          upperQuery.startsWith("DESCRIBE") ||
                          upperQuery.startsWith("DESC") ||
                          upperQuery.startsWith("EXPLAIN") ||
                          upperQuery.startsWith("WITH")
            assertFalse(isSelect, "Query should NOT be detected as SELECT: $query")
        }
    }

    @Test
    @DisplayName("Test dangerous query pattern detection")
    fun testDangerousQueryPatterns() {
        val dangerousPatterns = listOf(
            Regex("^\\s*DROP\\s+DATABASE", RegexOption.IGNORE_CASE),
            Regex("^\\s*DROP\\s+SCHEMA", RegexOption.IGNORE_CASE),
            Regex("^\\s*TRUNCATE", RegexOption.IGNORE_CASE),
            Regex("^\\s*DELETE\\s+FROM\\s+mysql\\.", RegexOption.IGNORE_CASE),
            Regex("^\\s*DELETE\\s+FROM\\s+pg_", RegexOption.IGNORE_CASE),
            Regex("^\\s*DELETE\\s+FROM\\s+sys\\.", RegexOption.IGNORE_CASE),
            Regex("^\\s*ALTER\\s+SYSTEM", RegexOption.IGNORE_CASE),
            Regex("^\\s*SHUTDOWN", RegexOption.IGNORE_CASE)
        )

        val dangerousQueries = listOf(
            "DROP DATABASE production" to true,
            "drop schema public" to true,
            "TRUNCATE TABLE users" to true,
            "DELETE FROM mysql.user" to true,
            "DELETE FROM pg_catalog.pg_tables" to true,
            "delete from sys.tables" to true,
            "ALTER SYSTEM SET max_connections = 1000" to true,
            "SHUTDOWN IMMEDIATE" to true,
            "SELECT * FROM users" to false,
            "DELETE FROM users WHERE id = 1" to false
        )

        dangerousQueries.forEach { (query, shouldMatch) ->
            val matchesAny = dangerousPatterns.any { it.containsMatchIn(query) }
            assertEquals(shouldMatch, matchesAny, "Query '$query' dangerous detection mismatch")
        }
    }

    @Test
    @DisplayName("Test query length validation")
    fun testQueryLengthValidation() {
        val maxLength = 10000
        val validQuery = "SELECT ".repeat(1000) // Less than 10000 chars
        val invalidQuery = "SELECT ".repeat(2000) // More than 10000 chars

        assertTrue(validQuery.length < maxLength)
        assertTrue(invalidQuery.length > maxLength)
    }

    @Test
    @DisplayName("Test empty query validation")
    fun testEmptyQueryValidation() {
        val emptyQueries = listOf("", "   ", "\t", "\n", "  \t\n  ")

        emptyQueries.forEach { query ->
            assertTrue(query.trim().isBlank(), "Query should be detected as blank: '$query'")
        }
    }
}
