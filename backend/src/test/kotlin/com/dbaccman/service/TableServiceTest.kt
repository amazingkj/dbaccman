package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.model.*
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement

class TableServiceTest {

    private lateinit var tableService: TableService

    @BeforeEach
    fun setUp() {
        tableService = TableService()
        mockkObject(SessionConnectionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("TableService should be instantiable")
    fun testTableServiceInstantiation() {
        assertNotNull(tableService)
    }

    // ==================== DatabaseInfo Model Tests ====================

    @Nested
    @DisplayName("DatabaseInfo Model Tests")
    inner class DatabaseInfoModelTests {

        @Test
        @DisplayName("DatabaseInfo model should have correct properties")
        fun testDatabaseInfoModel() {
            val dbInfo = DatabaseInfo(
                name = "testdb",
                tableCount = 10,
                totalRows = 5000L,
                size = 1024000L
            )

            assertEquals("testdb", dbInfo.name)
            assertEquals(10, dbInfo.tableCount)
            assertEquals(1024000L, dbInfo.size)
        }

        @Test
        @DisplayName("DatabaseInfo equality should work correctly")
        fun testDatabaseInfoEquality() {
            val db1 = DatabaseInfo("testdb", 5, 500L, 1000L)
            val db2 = DatabaseInfo("testdb", 5, 500L, 1000L)

            assertEquals(db1, db2)
            assertEquals(db1.hashCode(), db2.hashCode())
        }

        @Test
        @DisplayName("DatabaseInfo with zero values")
        fun testDatabaseInfoZeroValues() {
            val dbInfo = DatabaseInfo(
                name = "emptydb",
                tableCount = 0,
                totalRows = 0L,
                size = 0L
            )

            assertEquals(0, dbInfo.tableCount)
            assertEquals(0L, dbInfo.totalRows)
            assertEquals(0L, dbInfo.size)
        }

        @Test
        @DisplayName("DatabaseInfo copy should work correctly")
        fun testDatabaseInfoCopy() {
            val original = DatabaseInfo("testdb", 10, 1000L, 2000L)
            val copied = original.copy(tableCount = 20)

            assertEquals("testdb", copied.name)
            assertEquals(20, copied.tableCount)
            assertEquals(1000L, copied.totalRows)
        }
    }

    // ==================== TableInfo Model Tests ====================

    @Nested
    @DisplayName("TableInfo Model Tests")
    inner class TableInfoModelTests {

        @Test
        @DisplayName("TableInfo model should have correct properties")
        fun testTableInfoModel() {
            val tableInfo = TableInfo(
                name = "users",
                engine = "InnoDB",
                rows = 1000L,
                size = 65536L,
                createTime = "2024-01-01 00:00:00"
            )

            assertEquals("users", tableInfo.name)
            assertEquals("InnoDB", tableInfo.engine)
            assertEquals(1000L, tableInfo.rows)
            assertEquals(65536L, tableInfo.size)
            assertEquals("2024-01-01 00:00:00", tableInfo.createTime)
        }

        @Test
        @DisplayName("TableInfo should allow null engine and createTime")
        fun testTableInfoNullValues() {
            val tableInfo = TableInfo(
                name = "temp_table",
                engine = null,
                rows = 0L,
                size = 0L,
                createTime = null
            )

            assertNull(tableInfo.engine)
            assertNull(tableInfo.createTime)
        }

        @Test
        @DisplayName("TableInfo equality should work correctly")
        fun testTableInfoEquality() {
            val table1 = TableInfo("users", "InnoDB", 100L, 1000L, "2024-01-01")
            val table2 = TableInfo("users", "InnoDB", 100L, 1000L, "2024-01-01")

            assertEquals(table1, table2)
            assertEquals(table1.hashCode(), table2.hashCode())
        }

        @Test
        @DisplayName("Table size should be represented in bytes")
        fun testTableSizeInBytes() {
            val oneKB = 1024L
            val oneMB = 1024L * 1024L
            val oneGB = 1024L * 1024L * 1024L

            val smallTable = TableInfo("small", "InnoDB", 10L, oneKB, null)
            val mediumTable = TableInfo("medium", "InnoDB", 1000L, oneMB, null)
            val largeTable = TableInfo("large", "InnoDB", 1000000L, oneGB, null)

            assertEquals(1024L, smallTable.size)
            assertEquals(1048576L, mediumTable.size)
            assertEquals(1073741824L, largeTable.size)
        }
    }

    // ==================== ColumnInfo Model Tests ====================

    @Nested
    @DisplayName("ColumnInfo Model Tests")
    inner class ColumnInfoModelTests {

        @Test
        @DisplayName("ColumnInfo model should have correct properties")
        fun testColumnInfoModel() {
            val columnInfo = ColumnInfo(
                name = "id",
                type = "INT",
                nullable = false,
                key = "PRI",
                defaultValue = null,
                extra = "auto_increment"
            )

            assertEquals("id", columnInfo.name)
            assertEquals("INT", columnInfo.type)
            assertFalse(columnInfo.nullable)
            assertEquals("PRI", columnInfo.key)
            assertNull(columnInfo.defaultValue)
            assertEquals("auto_increment", columnInfo.extra)
        }

        @Test
        @DisplayName("ColumnInfo should handle nullable column")
        fun testColumnInfoNullable() {
            val columnInfo = ColumnInfo(
                name = "description",
                type = "VARCHAR(255)",
                nullable = true,
                key = null,
                defaultValue = "N/A",
                extra = null
            )

            assertTrue(columnInfo.nullable)
            assertNull(columnInfo.key)
            assertEquals("N/A", columnInfo.defaultValue)
            assertNull(columnInfo.extra)
        }

        @Test
        @DisplayName("ColumnInfo equality should work correctly")
        fun testColumnInfoEquality() {
            val col1 = ColumnInfo("name", "VARCHAR(100)", true, null, null, null)
            val col2 = ColumnInfo("name", "VARCHAR(100)", true, null, null, null)

            assertEquals(col1, col2)
            assertEquals(col1.hashCode(), col2.hashCode())
        }
    }

    // ==================== IndexInfo Model Tests ====================

    @Nested
    @DisplayName("IndexInfo Model Tests")
    inner class IndexInfoModelTests {

        @Test
        @DisplayName("IndexInfo model should have correct properties")
        fun testIndexInfoModel() {
            val indexInfo = IndexInfo(
                name = "idx_users_email",
                columns = listOf("email"),
                unique = true,
                type = "BTREE"
            )

            assertEquals("idx_users_email", indexInfo.name)
            assertEquals(1, indexInfo.columns.size)
            assertEquals("email", indexInfo.columns.first())
            assertTrue(indexInfo.unique)
            assertEquals("BTREE", indexInfo.type)
        }

        @Test
        @DisplayName("IndexInfo with multiple columns")
        fun testIndexInfoMultipleColumns() {
            val indexInfo = IndexInfo(
                name = "idx_composite",
                columns = listOf("first_name", "last_name"),
                unique = false,
                type = "BTREE"
            )

            assertEquals(2, indexInfo.columns.size)
            assertEquals("first_name", indexInfo.columns[0])
            assertEquals("last_name", indexInfo.columns[1])
            assertFalse(indexInfo.unique)
        }

        @Test
        @DisplayName("IndexInfo equality should work correctly")
        fun testIndexInfoEquality() {
            val idx1 = IndexInfo("idx_name", listOf("name"), false, "BTREE")
            val idx2 = IndexInfo("idx_name", listOf("name"), false, "BTREE")

            assertEquals(idx1, idx2)
            assertEquals(idx1.hashCode(), idx2.hashCode())
        }
    }

    // ==================== CreateIndexRequest Model Tests ====================

    @Nested
    @DisplayName("CreateIndexRequest Model Tests")
    inner class CreateIndexRequestModelTests {

        @Test
        @DisplayName("CreateIndexRequest model should have correct properties")
        fun testCreateIndexRequestModel() {
            val request = CreateIndexRequest(
                database = "testdb",
                table = "users",
                indexName = "idx_email",
                columns = listOf("email"),
                unique = true
            )

            assertEquals("testdb", request.database)
            assertEquals("users", request.table)
            assertEquals("idx_email", request.indexName)
            assertEquals(1, request.columns.size)
            assertTrue(request.unique)
        }

        @Test
        @DisplayName("CreateIndexRequest should have default unique value of false")
        fun testCreateIndexRequestDefaultUnique() {
            val request = CreateIndexRequest(
                database = "testdb",
                table = "users",
                indexName = "idx_name",
                columns = listOf("name")
            )

            assertFalse(request.unique)
        }

        @Test
        @DisplayName("CreateIndexRequest with composite index")
        fun testCreateIndexRequestComposite() {
            val request = CreateIndexRequest(
                database = "salesdb",
                table = "orders",
                indexName = "idx_customer_date",
                columns = listOf("customer_id", "order_date"),
                unique = false
            )

            assertEquals(2, request.columns.size)
            assertEquals("customer_id", request.columns[0])
            assertEquals("order_date", request.columns[1])
        }

        @Test
        @DisplayName("CreateIndexRequest equality should work correctly")
        fun testCreateIndexRequestEquality() {
            val req1 = CreateIndexRequest("db", "tbl", "idx", listOf("col"), true)
            val req2 = CreateIndexRequest("db", "tbl", "idx", listOf("col"), true)

            assertEquals(req1, req2)
            assertEquals(req1.hashCode(), req2.hashCode())
        }
    }

    // ==================== TableDataResult Model Tests ====================

    @Nested
    @DisplayName("TableDataResult Model Tests")
    inner class TableDataResultModelTests {

        @Test
        @DisplayName("TableDataResult should have correct properties")
        fun testTableDataResultModel() {
            val result = TableDataResult(
                columns = listOf("id", "name", "email"),
                rows = listOf(
                    listOf("1", "John", "john@example.com"),
                    listOf("2", "Jane", "jane@example.com")
                ),
                rowCount = 2
            )

            assertEquals(3, result.columns.size)
            assertEquals(2, result.rows.size)
            assertEquals(2, result.rowCount)
        }

        @Test
        @DisplayName("TableDataResult with null values in rows")
        fun testTableDataResultWithNulls() {
            val result = TableDataResult(
                columns = listOf("id", "optional"),
                rows = listOf(
                    listOf("1", null),
                    listOf("2", "value")
                ),
                rowCount = 2
            )

            assertNull(result.rows[0][1])
            assertEquals("value", result.rows[1][1])
        }

        @Test
        @DisplayName("TableDataResult empty result")
        fun testTableDataResultEmpty() {
            val result = TableDataResult(
                columns = listOf("id", "name"),
                rows = emptyList(),
                rowCount = 0
            )

            assertEquals(2, result.columns.size)
            assertTrue(result.rows.isEmpty())
            assertEquals(0, result.rowCount)
        }
    }

    // ==================== Service Method Tests with Mocking ====================

    @Nested
    @DisplayName("Service Method Tests with Mocking")
    inner class ServiceMethodTests {

        @Test
        @DisplayName("getDatabases should return database list")
        fun testGetDatabases() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getDatabasesQuery() } returns "SELECT name, table_count FROM databases"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getString("name") } returnsMany listOf("db1", "db2")
            every { mockResultSet.getInt("table_count") } returnsMany listOf(5, 10)
            every { mockResultSet.getLong("total_rows") } returnsMany listOf(100L, 200L)
            every { mockResultSet.getLong("total_size") } returnsMany listOf(1000L, 2000L)
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val databases = tableService.getDatabases("test-session")

            assertEquals(2, databases.size)
            assertEquals("db1", databases[0].name)
            assertEquals(5, databases[0].tableCount)
            assertEquals("db2", databases[1].name)
            assertEquals(10, databases[1].tableCount)
        }

        @Test
        @DisplayName("getTables should return table list for database")
        fun testGetTables() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getTablesQuery() } returns "SELECT * FROM tables WHERE database = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "testdb") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet
            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getString("name") } returnsMany listOf("users", "orders")
            every { mockResultSet.getString("engine") } returns "InnoDB"
            every { mockResultSet.getLong("row_count") } returnsMany listOf(100L, 500L)
            every { mockResultSet.getLong("table_size") } returnsMany listOf(10000L, 50000L)
            every { mockResultSet.getString("create_time") } returns "2024-01-01"
            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val tables = tableService.getTables("test-session", "testdb")

            assertEquals(2, tables.size)
            assertEquals("users", tables[0].name)
            assertEquals(100L, tables[0].rows)
            assertEquals("orders", tables[1].name)
            assertEquals(500L, tables[1].rows)
        }

        @Test
        @DisplayName("getTableColumns should return column list")
        fun testGetTableColumns() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getTableColumnsQuery() } returns "SELECT * FROM columns WHERE db = ? AND table = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "testdb") } just Runs
            every { mockPreparedStatement.setString(2, "users") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet
            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getString("name") } returnsMany listOf("id", "name")
            every { mockResultSet.getString("type") } returnsMany listOf("INT", "VARCHAR(100)")
            every { mockResultSet.getBoolean("nullable") } returnsMany listOf(false, true)
            every { mockResultSet.getString("col_key") } returnsMany listOf("PRI", "")
            every { mockResultSet.getString("default_value") } returnsMany listOf(null, null)
            every { mockResultSet.getString("extra") } returnsMany listOf("auto_increment", "")
            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val columns = tableService.getTableColumns("test-session", "testdb", "users")

            assertEquals(2, columns.size)
            assertEquals("id", columns[0].name)
            assertEquals("PRI", columns[0].key)
            assertFalse(columns[0].nullable)
            assertEquals("name", columns[1].name)
            assertTrue(columns[1].nullable)
        }

        @Test
        @DisplayName("getIndexes should return index list")
        fun testGetIndexes() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getIndexesQuery() } returns "SELECT * FROM indexes WHERE db = ? AND table = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "testdb") } just Runs
            every { mockPreparedStatement.setString(2, "users") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet
            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getString("name") } returnsMany listOf("PRIMARY", "idx_email")
            every { mockResultSet.getString("columns") } returnsMany listOf("id", "email")
            every { mockResultSet.getBoolean("is_unique") } returnsMany listOf(true, true)
            every { mockResultSet.getString("type") } returns "BTREE"
            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val indexes = tableService.getIndexes("test-session", "testdb", "users")

            assertEquals(2, indexes.size)
            assertEquals("PRIMARY", indexes[0].name)
            assertTrue(indexes[0].unique)
            assertEquals("idx_email", indexes[1].name)
        }

        @Test
        @DisplayName("createIndex should execute CREATE INDEX SQL")
        fun testCreateIndex() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getCreateIndexSql("testdb", "users", "idx_name", listOf("name"), false) } returns
                    "CREATE INDEX `idx_name` ON `testdb`.`users` (`name`)"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val request = CreateIndexRequest(
                database = "testdb",
                table = "users",
                indexName = "idx_name",
                columns = listOf("name"),
                unique = false
            )

            tableService.createIndex("test-session", request)

            verify { mockStatement.execute("CREATE INDEX `idx_name` ON `testdb`.`users` (`name`)") }
        }

        @Test
        @DisplayName("dropIndex should execute DROP INDEX SQL")
        fun testDropIndex() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getDropIndexSql("testdb", "users", "idx_name") } returns
                    "DROP INDEX `idx_name` ON `testdb`.`users`"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            tableService.dropIndex("test-session", "testdb", "users", "idx_name")

            verify { mockStatement.execute("DROP INDEX `idx_name` ON `testdb`.`users`") }
        }

        @Test
        @DisplayName("getTableData should return limited rows")
        fun testGetTableData() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockMetaData = mockk<ResultSetMetaData>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getSwitchSchemaSql("testdb") } returns "USE `testdb`"
            every { mockDialect.quoteIdentifier("users") } returns "`users`"
            every { mockDialect.getSelectWithLimitSql("`users`", 100) } returns "SELECT * FROM `users` LIMIT 100"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute("USE `testdb`") } returns true
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

            val result = tableService.getTableData("test-session", "testdb", "users", 100)

            assertEquals(2, result.columns.size)
            assertEquals("id", result.columns[0])
            assertEquals("name", result.columns[1])
            assertEquals(2, result.rowCount)
            assertEquals("1", result.rows[0][0])
            assertEquals("John", result.rows[0][1])
        }

        @Test
        @DisplayName("gatherStats should return true for Oracle table-level stats")
        fun testGatherStats() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            // Table-level stats with minimal settings to avoid system overload
            val expectedSql = "BEGIN DBMS_STATS.GATHER_TABLE_STATS(                 ownname => 'TESTSCHEMA',                 tabname => 'TESTTABLE',                 estimate_percent => 1,                 degree => 1,                 method_opt => 'FOR ALL COLUMNS SIZE 1',                 no_invalidate => TRUE             ); END;".replace("  ", " ")
            every { mockDialect.getGatherStatsSql("TESTSCHEMA", "TESTTABLE") } returns expectedSql
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.queryTimeout = any() } just Runs
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val result = tableService.gatherStats("test-session", "TESTSCHEMA", "TESTTABLE")

            assertTrue(result)
            verify { mockStatement.queryTimeout = 30 }
            verify { mockStatement.execute(expectedSql) }
        }

        @Test
        @DisplayName("gatherStats should return true for Oracle schema-level stats")
        fun testGatherStatsSchemaLevel() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            // Schema-level stats with minimal settings
            val expectedSql = "BEGIN DBMS_STATS.GATHER_SCHEMA_STATS(                 ownname => 'TESTSCHEMA',                 estimate_percent => 1,                 degree => 1,                 method_opt => 'FOR ALL COLUMNS SIZE 1',                 no_invalidate => TRUE,                 options => 'GATHER AUTO'             ); END;".replace("  ", " ")
            every { mockDialect.getGatherStatsSql("TESTSCHEMA", null) } returns expectedSql
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.queryTimeout = any() } just Runs
            every { mockStatement.execute(any<String>()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val result = tableService.gatherStats("test-session", "TESTSCHEMA")

            assertTrue(result)
            verify { mockStatement.queryTimeout = 30 }
            verify { mockStatement.execute(expectedSql) }
        }

        @Test
        @DisplayName("gatherStats should return false when not supported")
        fun testGatherStatsNotSupported() {
            val mockConnection = mockk<Connection>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection

            every { mockDialect.getGatherStatsSql("testdb", null) } returns null
            every { mockConnection.close() } just Runs

            val result = tableService.gatherStats("test-session", "testdb")

            assertFalse(result)
        }
    }

    // ==================== Collection Tests ====================

    @Nested
    @DisplayName("Collection Tests")
    inner class CollectionTests {

        @Test
        @DisplayName("List of TableInfo should be sortable by name")
        fun testTableInfoListSorting() {
            val tables = listOf(
                TableInfo("users", "InnoDB", 100L, 1000L, null),
                TableInfo("accounts", "InnoDB", 50L, 500L, null),
                TableInfo("orders", "InnoDB", 200L, 2000L, null)
            )

            val sorted = tables.sortedBy { it.name }

            assertEquals("accounts", sorted[0].name)
            assertEquals("orders", sorted[1].name)
            assertEquals("users", sorted[2].name)
        }

        @Test
        @DisplayName("List of ColumnInfo should preserve order")
        fun testColumnInfoListOrder() {
            val columns = listOf(
                ColumnInfo("id", "INT", false, "PRI", null, "auto_increment"),
                ColumnInfo("name", "VARCHAR(100)", false, null, null, null),
                ColumnInfo("email", "VARCHAR(255)", true, "UNI", null, null)
            )

            assertEquals("id", columns[0].name)
            assertEquals("name", columns[1].name)
            assertEquals("email", columns[2].name)
        }

        @Test
        @DisplayName("List of DatabaseInfo should be filterable by size")
        fun testDatabaseInfoFiltering() {
            val databases = listOf(
                DatabaseInfo("small", 5, 100L, 1000L),
                DatabaseInfo("medium", 20, 1000L, 10000L),
                DatabaseInfo("large", 100, 10000L, 100000L)
            )

            val largeDbsOnly = databases.filter { it.size > 5000L }

            assertEquals(2, largeDbsOnly.size)
            assertEquals("medium", largeDbsOnly[0].name)
            assertEquals("large", largeDbsOnly[1].name)
        }
    }
}
