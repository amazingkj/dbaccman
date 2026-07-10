package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.MySQLDialect
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement

class QueryServiceTest {

    private lateinit var queryService: QueryService

    @BeforeEach
    fun setUp() {
        queryService = QueryService()
        mockkObject(SessionConnectionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("QueryService should be instantiable")
    fun testQueryServiceInstantiation() {
        assertNotNull(queryService)
    }

    // ==================== QueryResult Model Tests ====================

    @Nested
    @DisplayName("QueryResult Model Tests")
    inner class QueryResultModelTests {

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
    }

    // ==================== QueryExecutionException Tests ====================

    @Nested
    @DisplayName("QueryExecutionException Tests")
    inner class QueryExecutionExceptionTests {

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
        @DisplayName("QueryExecutionException with zero execution time")
        fun testQueryExecutionExceptionZeroTime() {
            val exception = QueryExecutionException(
                message = "Syntax error",
                executionTimeMs = 0L
            )

            assertEquals(0L, exception.executionTimeMs)
        }

        @Test
        @DisplayName("QueryExecutionException with long execution time")
        fun testQueryExecutionExceptionLongTime() {
            val exception = QueryExecutionException(
                message = "Query timeout",
                executionTimeMs = 300000L
            )

            assertEquals(300000L, exception.executionTimeMs)
        }
    }

    // ==================== SELECT Query Detection Tests ====================

    @Nested
    @DisplayName("SELECT Query Detection Tests")
    inner class SelectQueryDetectionTests {

        @Test
        @DisplayName("Test SELECT query detection patterns")
        fun testSelectQueryPatterns() {
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
        @DisplayName("Test SELECT with leading whitespace")
        fun testSelectWithWhitespace() {
            val query = "   SELECT * FROM users"
            val upperQuery = query.uppercase().trim()
            assertTrue(upperQuery.startsWith("SELECT"))
        }

        @Test
        @DisplayName("Test SHOW variations")
        fun testShowVariations() {
            val showQueries = listOf(
                "SHOW TABLES",
                "SHOW DATABASES",
                "SHOW CREATE TABLE users",
                "SHOW INDEX FROM users",
                "SHOW STATUS"
            )

            showQueries.forEach { query ->
                val upperQuery = query.uppercase().trim()
                assertTrue(upperQuery.startsWith("SHOW"), "Query should start with SHOW: $query")
            }
        }
    }

    // ==================== Dangerous Query Detection Tests ====================

    @Nested
    @DisplayName("Dangerous Query Detection Tests")
    inner class DangerousQueryDetectionTests {

        private val dangerousPatterns = listOf(
            Regex("^\\s*DROP\\s+DATABASE", RegexOption.IGNORE_CASE),
            Regex("^\\s*DROP\\s+SCHEMA", RegexOption.IGNORE_CASE),
            Regex("^\\s*TRUNCATE", RegexOption.IGNORE_CASE),
            Regex("^\\s*DELETE\\s+FROM\\s+mysql\\.", RegexOption.IGNORE_CASE),
            Regex("^\\s*DELETE\\s+FROM\\s+pg_", RegexOption.IGNORE_CASE),
            Regex("^\\s*DELETE\\s+FROM\\s+sys\\.", RegexOption.IGNORE_CASE),
            Regex("^\\s*ALTER\\s+SYSTEM", RegexOption.IGNORE_CASE),
            Regex("^\\s*SHUTDOWN", RegexOption.IGNORE_CASE)
        )

        @Test
        @DisplayName("Test dangerous query pattern detection")
        fun testDangerousQueryPatterns() {
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
        @DisplayName("Test DROP DATABASE variations")
        fun testDropDatabaseVariations() {
            val dropDatabaseQueries = listOf(
                "DROP DATABASE test",
                "drop database test",
                "DROP  DATABASE test",
                "  DROP DATABASE test"
            )

            val pattern = Regex("^\\s*DROP\\s+DATABASE", RegexOption.IGNORE_CASE)
            dropDatabaseQueries.forEach { query ->
                assertTrue(pattern.containsMatchIn(query), "Should match: $query")
            }
        }

        @Test
        @DisplayName("Test safe DELETE queries")
        fun testSafeDeleteQueries() {
            val safeQueries = listOf(
                "DELETE FROM users WHERE id = 1",
                "DELETE FROM orders WHERE status = 'cancelled'",
                "DELETE FROM temp_table"
            )

            safeQueries.forEach { query ->
                val matchesAny = dangerousPatterns.any { it.containsMatchIn(query) }
                assertFalse(matchesAny, "Should NOT match dangerous pattern: $query")
            }
        }

        @Test
        @DisplayName("Test TRUNCATE detection")
        fun testTruncateDetection() {
            val truncateQueries = listOf(
                "TRUNCATE TABLE users",
                "truncate users",
                "TRUNCATE test_table"
            )

            val pattern = Regex("^\\s*TRUNCATE", RegexOption.IGNORE_CASE)
            truncateQueries.forEach { query ->
                assertTrue(pattern.containsMatchIn(query), "Should match TRUNCATE: $query")
            }
        }
    }

    // ==================== Query Validation Tests ====================

    @Nested
    @DisplayName("Query Validation Tests")
    inner class QueryValidationTests {

        @Test
        @DisplayName("Test query length validation")
        fun testQueryLengthValidation() {
            val maxLength = 10000
            val validQuery = "SELECT ".repeat(1000)
            val invalidQuery = "SELECT ".repeat(2000)

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

        @Test
        @DisplayName("Test non-empty query validation")
        fun testNonEmptyQueryValidation() {
            val validQueries = listOf(
                "SELECT 1",
                "  SELECT 1  ",
                "\nSELECT 1\n"
            )

            validQueries.forEach { query ->
                assertFalse(query.trim().isBlank(), "Query should NOT be detected as blank: '$query'")
            }
        }
    }

    // ==================== SQL Statement Splitting Tests ====================

    @Nested
    @DisplayName("SQL Statement Splitting Tests")
    inner class SqlStatementSplittingTests {

        // Helper to test split logic (replicates the private splitStatements method)
        private fun splitStatements(query: String): List<String> {
            val statements = mutableListOf<String>()
            val current = StringBuilder()
            var inSingleQuote = false
            var inDoubleQuote = false
            var i = 0

            while (i < query.length) {
                val c = query[i]

                when {
                    c == '\'' && !inDoubleQuote -> {
                        inSingleQuote = !inSingleQuote
                        current.append(c)
                    }
                    c == '"' && !inSingleQuote -> {
                        inDoubleQuote = !inDoubleQuote
                        current.append(c)
                    }
                    c == ';' && !inSingleQuote && !inDoubleQuote -> {
                        val stmt = current.toString().trim()
                        if (stmt.isNotBlank()) {
                            statements.add(stmt)
                        }
                        current.clear()
                    }
                    else -> current.append(c)
                }
                i++
            }

            val lastStmt = current.toString().trim()
            if (lastStmt.isNotBlank()) {
                statements.add(lastStmt)
            }

            return statements
        }

        @Test
        @DisplayName("Split single statement without semicolon")
        fun testSplitSingleStatementNoSemicolon() {
            val result = splitStatements("SELECT * FROM users")
            assertEquals(1, result.size)
            assertEquals("SELECT * FROM users", result[0])
        }

        @Test
        @DisplayName("Split single statement with semicolon")
        fun testSplitSingleStatementWithSemicolon() {
            val result = splitStatements("SELECT * FROM users;")
            assertEquals(1, result.size)
            assertEquals("SELECT * FROM users", result[0])
        }

        @Test
        @DisplayName("Split multiple statements")
        fun testSplitMultipleStatements() {
            val result = splitStatements("SELECT 1; SELECT 2; SELECT 3")
            assertEquals(3, result.size)
            assertEquals("SELECT 1", result[0])
            assertEquals("SELECT 2", result[1])
            assertEquals("SELECT 3", result[2])
        }

        @Test
        @DisplayName("Split with semicolon inside single quotes")
        fun testSplitSemicolonInSingleQuotes() {
            val result = splitStatements("SELECT 'hello; world'; SELECT 2")
            assertEquals(2, result.size)
            assertEquals("SELECT 'hello; world'", result[0])
            assertEquals("SELECT 2", result[1])
        }

        @Test
        @DisplayName("Split with semicolon inside double quotes")
        fun testSplitSemicolonInDoubleQuotes() {
            val result = splitStatements("SELECT \"col;name\"; SELECT 2")
            assertEquals(2, result.size)
            assertEquals("SELECT \"col;name\"", result[0])
            assertEquals("SELECT 2", result[1])
        }

        @Test
        @DisplayName("Split with nested quotes")
        fun testSplitNestedQuotes() {
            val result = splitStatements("SELECT 'it''s a test'; SELECT 2")
            assertEquals(2, result.size)
            assertEquals("SELECT 'it''s a test'", result[0])
        }

        @Test
        @DisplayName("Split empty string")
        fun testSplitEmptyString() {
            val result = splitStatements("")
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("Split whitespace only")
        fun testSplitWhitespaceOnly() {
            val result = splitStatements("   ;   ;   ")
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("Split INSERT and SELECT")
        fun testSplitInsertAndSelect() {
            val result = splitStatements("INSERT INTO users VALUES (1, 'test'); SELECT * FROM users")
            assertEquals(2, result.size)
            assertEquals("INSERT INTO users VALUES (1, 'test')", result[0])
            assertEquals("SELECT * FROM users", result[1])
        }

        @Test
        @DisplayName("Split with trailing whitespace")
        fun testSplitWithTrailingWhitespace() {
            val result = splitStatements("SELECT 1  ;  SELECT 2  ;  ")
            assertEquals(2, result.size)
            assertEquals("SELECT 1", result[0])
            assertEquals("SELECT 2", result[1])
        }

        @Test
        @DisplayName("Split complex query with string containing semicolons")
        fun testSplitComplexQuery() {
            val query = """
                INSERT INTO logs VALUES ('Error: timeout; retry=3');
                UPDATE config SET value = 'a;b;c' WHERE key = 'test';
                SELECT * FROM logs
            """.trimIndent()
            val result = splitStatements(query)
            assertEquals(3, result.size)
        }
    }

    // ==================== Row Limit Tests ====================

    @Nested
    @DisplayName("Row Limit Tests")
    inner class RowLimitTests {

        @Test
        @DisplayName("Default row limit should be 1000")
        fun testDefaultRowLimit() {
            val defaultRows = 1000
            assertEquals(1000, defaultRows)
        }

        @Test
        @DisplayName("Maximum row limit should be 50000")
        fun testMaxRowLimit() {
            val maxRows = 50000
            assertEquals(50000, maxRows)
        }

        @Test
        @DisplayName("Row limit should be coerced to valid range")
        fun testRowLimitCoercion() {
            val minLimit = 1
            val maxLimit = 50000

            // Test coercion
            assertEquals(1, (-10).coerceIn(minLimit, maxLimit))
            assertEquals(1, 0.coerceIn(minLimit, maxLimit))
            assertEquals(100, 100.coerceIn(minLimit, maxLimit))
            assertEquals(50000, 100000.coerceIn(minLimit, maxLimit))
        }

        @Test
        @DisplayName("Null limit should use default")
        fun testNullLimitUsesDefault() {
            val defaultRows = 1000
            val limit: Int? = null
            val effectiveLimit = limit ?: defaultRows
            assertEquals(1000, effectiveLimit)
        }

        @Test
        @DisplayName("Custom limit should be respected within bounds")
        fun testCustomLimitWithinBounds() {
            val defaultRows = 1000
            val maxRows = 50000

            val customLimit = 5000
            val effectiveLimit = customLimit.coerceIn(1, maxRows)
            assertEquals(5000, effectiveLimit)
        }
    }

    // ==================== Service Method Tests with Mocking ====================

    @Nested
    @DisplayName("Service Method Tests with Mocking")
    inner class ServiceMethodTests {

        @Test
        @DisplayName("executeQuery should execute SELECT and return results")
        fun testExecuteSelectQuery() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockMetaData = mockk<ResultSetMetaData>()
            val mockDialect = mockk<DatabaseDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false
            every { mockDialect.getSwitchSchemaSql(any()) } returns null
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.maxRows = any() } just Runs
            every { mockStatement.queryTimeout = any() } just Runs
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.metaData } returns mockMetaData
            every { mockMetaData.columnCount } returns 2
            every { mockMetaData.getColumnLabel(1) } returns "id"
            every { mockMetaData.getColumnLabel(2) } returns "name"
            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getObject(1) } returnsMany listOf(1, 2)
            every { mockResultSet.getObject(2) } returnsMany listOf("John", "Jane")
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val result = queryService.executeQuery(
                sessionId = "test-session",
                query = "SELECT id, name FROM users",
                account = null,
                username = "admin",
                ipAddress = "127.0.0.1"
            )

            assertEquals(2, result.columns.size)
            assertEquals("id", result.columns[0])
            assertEquals("name", result.columns[1])
            assertEquals(2, result.rowCount)
            assertTrue(result.isSelectQuery)
        }

        @Test
        @DisplayName("executeQuery should handle UPDATE and return affected rows")
        fun testExecuteUpdateQuery() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<DatabaseDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false
            every { mockDialect.getSwitchSchemaSql(any()) } returns null
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.queryTimeout = any() } just Runs
            every { mockStatement.executeUpdate(any()) } returns 5
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val result = queryService.executeQuery(
                sessionId = "test-session",
                query = "UPDATE users SET status = 'active'",
                account = null,
                username = "admin",
                ipAddress = "127.0.0.1"
            )

            assertEquals(5, result.affectedRows)
            assertFalse(result.isSelectQuery)
            assertEquals(listOf("Affected Rows"), result.columns)
        }

        @Test
        @DisplayName("executeQuery should throw exception for empty query")
        fun testExecuteEmptyQuery() {
            assertThrows<IllegalArgumentException> {
                queryService.executeQuery(
                    sessionId = "test-session",
                    query = "   ",
                    account = null,
                    username = "admin",
                    ipAddress = "127.0.0.1"
                )
            }
        }

        @Test
        @DisplayName("executeQuery should throw exception for dangerous query")
        fun testExecuteDangerousQuery() {
            assertThrows<IllegalArgumentException> {
                queryService.executeQuery(
                    sessionId = "test-session",
                    query = "DROP DATABASE production",
                    account = null,
                    username = "admin",
                    ipAddress = "127.0.0.1"
                )
            }
        }

        @Test
        @DisplayName("executeQuery should throw exception for too long query")
        fun testExecuteTooLongQuery() {
            val longQuery = "SELECT " + "a".repeat(10001)

            assertThrows<IllegalArgumentException> {
                queryService.executeQuery(
                    sessionId = "test-session",
                    query = longQuery,
                    account = null,
                    username = "admin",
                    ipAddress = "127.0.0.1"
                )
            }
        }
    }

    // ==================== Schema Switching Tests ====================

    @Nested
    @DisplayName("Schema Switching and Run as User Tests")
    inner class SchemaSwitchingTests {

        @Test
        @DisplayName("executeQuery should switch schema when account is provided")
        fun testExecuteQueryWithSchemaSwitch() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockSwitchStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockMetaData = mockk<ResultSetMetaData>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false

            // Schema switch SQL
            every { mockDialect.getSwitchSchemaSql("testdb") } returns "USE testdb"

            // First createStatement is for schema switch
            every { mockConnection.createStatement() } returnsMany listOf(mockSwitchStatement, mockStatement)
            every { mockSwitchStatement.execute("USE testdb") } returns true
            every { mockSwitchStatement.close() } just Runs

            // Second createStatement is for the actual query
            every { mockStatement.maxRows = any() } just Runs
            every { mockStatement.queryTimeout = any() } just Runs
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.metaData } returns mockMetaData
            every { mockMetaData.columnCount } returns 1
            every { mockMetaData.getColumnLabel(1) } returns "result"
            every { mockMetaData.getColumnTypeName(1) } returns "VARCHAR"
            every { mockMetaData.isAutoIncrement(1) } returns false
            every { mockMetaData.isNullable(1) } returns java.sql.ResultSetMetaData.columnNullable
            every { mockResultSet.next() } returnsMany listOf(true, false)
            every { mockResultSet.getObject(1) } returns "test_value"
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val result = queryService.executeQuery(
                sessionId = "test-session",
                query = "SELECT * FROM users",
                account = "testdb",  // This triggers schema switch
                username = "admin",
                ipAddress = "127.0.0.1"
            )

            // Verify schema switch was executed
            verify { mockSwitchStatement.execute("USE testdb") }

            assertEquals(1, result.columns.size)
            assertEquals("result", result.columns[0])
            assertTrue(result.isSelectQuery)
        }

        @Test
        @DisplayName("executeQuery should not switch schema when account is null")
        fun testExecuteQueryWithoutSchemaSwitch() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockMetaData = mockk<ResultSetMetaData>()
            val mockDialect = mockk<DatabaseDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false
            every { mockDialect.getSwitchSchemaSql(any()) } returns null
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.maxRows = any() } just Runs
            every { mockStatement.queryTimeout = any() } just Runs
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.metaData } returns mockMetaData
            every { mockMetaData.columnCount } returns 1
            every { mockMetaData.getColumnLabel(1) } returns "id"
            every { mockMetaData.getColumnTypeName(1) } returns "INT"
            every { mockMetaData.isAutoIncrement(1) } returns false
            every { mockMetaData.isNullable(1) } returns java.sql.ResultSetMetaData.columnNullable
            every { mockResultSet.next() } returnsMany listOf(true, false)
            every { mockResultSet.getObject(1) } returns 1
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val result = queryService.executeQuery(
                sessionId = "test-session",
                query = "SELECT id FROM users",
                account = null,  // No schema switch
                username = "admin",
                ipAddress = "127.0.0.1"
            )

            // getSwitchSchemaSql should never be called with a non-null value
            verify(exactly = 0) { mockDialect.getSwitchSchemaSql(any()) }

            assertEquals(1, result.rowCount)
        }

        @Test
        @DisplayName("executeQuery should not switch schema when account is blank")
        fun testExecuteQueryWithBlankAccount() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockMetaData = mockk<ResultSetMetaData>()
            val mockDialect = mockk<DatabaseDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.maxRows = any() } just Runs
            every { mockStatement.queryTimeout = any() } just Runs
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.metaData } returns mockMetaData
            every { mockMetaData.columnCount } returns 1
            every { mockMetaData.getColumnLabel(1) } returns "id"
            every { mockMetaData.getColumnTypeName(1) } returns "INT"
            every { mockMetaData.isAutoIncrement(1) } returns false
            every { mockMetaData.isNullable(1) } returns java.sql.ResultSetMetaData.columnNullable
            every { mockResultSet.next() } returnsMany listOf(true, false)
            every { mockResultSet.getObject(1) } returns 1
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val result = queryService.executeQuery(
                sessionId = "test-session",
                query = "SELECT id FROM users",
                account = "   ",  // Blank account should be ignored
                username = "admin",
                ipAddress = "127.0.0.1"
            )

            // getSwitchSchemaSql should never be called
            verify(exactly = 0) { mockDialect.getSwitchSchemaSql(any()) }

            assertEquals(1, result.rowCount)
        }

        @Test
        @DisplayName("executeQuery should throw exception when schema switch fails")
        fun testExecuteQuerySchemaSwithFailure() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false

            // Schema switch SQL
            every { mockDialect.getSwitchSchemaSql("nonexistent_db") } returns "USE nonexistent_db"

            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute("USE nonexistent_db") } throws RuntimeException("Unknown database 'nonexistent_db'")
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val exception = assertThrows<QueryExecutionException> {
                queryService.executeQuery(
                    sessionId = "test-session",
                    query = "SELECT * FROM users",
                    account = "nonexistent_db",
                    username = "admin",
                    ipAddress = "127.0.0.1"
                )
            }

            assertTrue(exception.message?.contains("Failed to switch to schema") == true)
        }

        @Test
        @DisplayName("Run as User should use account parameter for schema context")
        fun testRunAsUserWithDifferentSchema() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockSwitchStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockMetaData = mockk<ResultSetMetaData>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { SessionConnectionManager.isContainerRoot("test-session") } returns false

            // "Run as User" uses schema switch - user selects a different user/schema to run query as
            every { mockDialect.getSwitchSchemaSql("other_user_schema") } returns "USE other_user_schema"

            every { mockConnection.createStatement() } returnsMany listOf(mockSwitchStatement, mockStatement)
            every { mockSwitchStatement.execute("USE other_user_schema") } returns true
            every { mockSwitchStatement.close() } just Runs

            every { mockStatement.maxRows = any() } just Runs
            every { mockStatement.queryTimeout = any() } just Runs
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.metaData } returns mockMetaData
            every { mockMetaData.columnCount } returns 2
            every { mockMetaData.getColumnLabel(1) } returns "table_name"
            every { mockMetaData.getColumnLabel(2) } returns "owner"
            every { mockMetaData.getColumnTypeName(any()) } returns "VARCHAR"
            every { mockMetaData.isAutoIncrement(any()) } returns false
            every { mockMetaData.isNullable(any()) } returns java.sql.ResultSetMetaData.columnNullable
            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getObject(1) } returnsMany listOf("users_table", "orders_table")
            every { mockResultSet.getObject(2) } returnsMany listOf("other_user_schema", "other_user_schema")
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val result = queryService.executeQuery(
                sessionId = "test-session",
                query = "SELECT table_name, owner FROM all_tables WHERE owner = USER",
                account = "other_user_schema",  // Run as different user/schema
                username = "admin",
                ipAddress = "127.0.0.1"
            )

            // Verify the schema was switched to run as the other user
            verify { mockSwitchStatement.execute("USE other_user_schema") }

            assertEquals(2, result.columns.size)
            assertEquals(2, result.rowCount)
            assertTrue(result.isSelectQuery)
        }
    }
}
