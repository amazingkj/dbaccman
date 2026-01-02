package com.dbaccman.service

import com.dbaccman.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class TableServiceTest {

    private lateinit var tableService: TableService

    @BeforeEach
    fun setUp() {
        tableService = TableService()
    }

    @Test
    @DisplayName("TableService should be instantiable")
    fun testTableServiceInstantiation() {
        assertNotNull(tableService)
    }

    // ==================== DatabaseInfo Model Tests ====================

    @Test
    @DisplayName("DatabaseInfo model should have correct properties")
    fun testDatabaseInfoModel() {
        val dbInfo = DatabaseInfo(
            name = "testdb",
            tableCount = 10,
            size = 1024000L
        )

        assertEquals("testdb", dbInfo.name)
        assertEquals(10, dbInfo.tableCount)
        assertEquals(1024000L, dbInfo.size)
    }

    @Test
    @DisplayName("DatabaseInfo equality should work correctly")
    fun testDatabaseInfoEquality() {
        val db1 = DatabaseInfo("testdb", 5, 1000L)
        val db2 = DatabaseInfo("testdb", 5, 1000L)

        assertEquals(db1, db2)
        assertEquals(db1.hashCode(), db2.hashCode())
    }

    @Test
    @DisplayName("DatabaseInfo with zero values")
    fun testDatabaseInfoZeroValues() {
        val dbInfo = DatabaseInfo(
            name = "emptydb",
            tableCount = 0,
            size = 0L
        )

        assertEquals(0, dbInfo.tableCount)
        assertEquals(0L, dbInfo.size)
    }

    // ==================== TableInfo Model Tests ====================

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

    // ==================== ColumnInfo Model Tests ====================

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

    // ==================== IndexInfo Model Tests ====================

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

    // ==================== CreateIndexRequest Model Tests ====================

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

    // ==================== Size Formatting Tests ====================

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

    // ==================== Collection Tests ====================

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
}
