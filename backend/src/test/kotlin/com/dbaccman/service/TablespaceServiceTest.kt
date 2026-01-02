package com.dbaccman.service

import com.dbaccman.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class TablespaceServiceTest {

    private lateinit var tablespaceService: TablespaceService

    @BeforeEach
    fun setUp() {
        tablespaceService = TablespaceService()
    }

    @Test
    @DisplayName("TablespaceService should be instantiable")
    fun testTablespaceServiceInstantiation() {
        assertNotNull(tablespaceService)
    }

    // ==================== TablespaceInfo Model Tests ====================

    @Test
    @DisplayName("TablespaceInfo model should have correct properties")
    fun testTablespaceInfoModel() {
        val tsInfo = TablespaceInfo(
            name = "USERS",
            spaceType = "PERMANENT",
            fileSize = 1073741824L,  // 1GB
            allocatedSize = 536870912L,  // 512MB
            state = "ONLINE",
            filePath = "/oradata/users01.dbf"
        )

        assertEquals("USERS", tsInfo.name)
        assertEquals("PERMANENT", tsInfo.spaceType)
        assertEquals(1073741824L, tsInfo.fileSize)
        assertEquals(536870912L, tsInfo.allocatedSize)
        assertEquals("ONLINE", tsInfo.state)
        assertEquals("/oradata/users01.dbf", tsInfo.filePath)
    }

    @Test
    @DisplayName("TablespaceInfo should allow null filePath")
    fun testTablespaceInfoNullFilePath() {
        val tsInfo = TablespaceInfo(
            name = "TEMP",
            spaceType = "TEMPORARY",
            fileSize = 104857600L,
            allocatedSize = 52428800L,
            state = "ONLINE"
        )

        assertNull(tsInfo.filePath)
    }

    @Test
    @DisplayName("TablespaceInfo equality should work correctly")
    fun testTablespaceInfoEquality() {
        val ts1 = TablespaceInfo("DATA", "PERMANENT", 1000L, 500L, "ONLINE", null)
        val ts2 = TablespaceInfo("DATA", "PERMANENT", 1000L, 500L, "ONLINE", null)

        assertEquals(ts1, ts2)
        assertEquals(ts1.hashCode(), ts2.hashCode())
    }

    @Test
    @DisplayName("TablespaceInfo with MySQL InnoDB type")
    fun testTablespaceInfoMySQL() {
        val tsInfo = TablespaceInfo(
            name = "innodb_system",
            spaceType = "System",
            fileSize = 104857600L,
            allocatedSize = 104857600L,
            state = "active"
        )

        assertEquals("innodb_system", tsInfo.name)
        assertEquals("System", tsInfo.spaceType)
    }

    @Test
    @DisplayName("TablespaceInfo with PostgreSQL type")
    fun testTablespaceInfoPostgreSQL() {
        val tsInfo = TablespaceInfo(
            name = "pg_default",
            spaceType = "GENERAL",
            fileSize = 209715200L,
            allocatedSize = 209715200L,
            state = "ACTIVE"
        )

        assertEquals("pg_default", tsInfo.name)
        assertEquals("GENERAL", tsInfo.spaceType)
    }

    // ==================== CreateTablespaceRequest Model Tests ====================

    @Test
    @DisplayName("CreateTablespaceRequest model should have correct properties")
    fun testCreateTablespaceRequestModel() {
        val request = CreateTablespaceRequest(
            name = "NEW_TABLESPACE",
            dataFile = "/oradata/new_ts.dbf",
            engine = "InnoDB"
        )

        assertEquals("NEW_TABLESPACE", request.name)
        assertEquals("/oradata/new_ts.dbf", request.dataFile)
        assertEquals("InnoDB", request.engine)
    }

    @Test
    @DisplayName("CreateTablespaceRequest should have default engine")
    fun testCreateTablespaceRequestDefaults() {
        val request = CreateTablespaceRequest(name = "DEFAULT_TS")

        assertEquals("DEFAULT_TS", request.name)
        assertNull(request.dataFile)
        assertEquals("InnoDB", request.engine)
    }

    @Test
    @DisplayName("CreateTablespaceRequest with custom engine")
    fun testCreateTablespaceRequestCustomEngine() {
        val request = CreateTablespaceRequest(
            name = "MYSPACE",
            dataFile = null,
            engine = "MyISAM"
        )

        assertEquals("MyISAM", request.engine)
    }

    @Test
    @DisplayName("CreateTablespaceRequest equality should work correctly")
    fun testCreateTablespaceRequestEquality() {
        val req1 = CreateTablespaceRequest("TS1", "/data/ts1.ibd", "InnoDB")
        val req2 = CreateTablespaceRequest("TS1", "/data/ts1.ibd", "InnoDB")

        assertEquals(req1, req2)
        assertEquals(req1.hashCode(), req2.hashCode())
    }

    // ==================== TableLocationRequest Model Tests ====================

    @Test
    @DisplayName("TableLocationRequest model should have correct properties")
    fun testTableLocationRequestModel() {
        val request = TableLocationRequest(
            database = "salesdb",
            tableName = "orders",
            tablespaceName = "DATA_TS"
        )

        assertEquals("salesdb", request.database)
        assertEquals("orders", request.tableName)
        assertEquals("DATA_TS", request.tablespaceName)
    }

    @Test
    @DisplayName("TableLocationRequest equality should work correctly")
    fun testTableLocationRequestEquality() {
        val req1 = TableLocationRequest("db", "tbl", "ts")
        val req2 = TableLocationRequest("db", "tbl", "ts")

        assertEquals(req1, req2)
        assertEquals(req1.hashCode(), req2.hashCode())
    }

    @Test
    @DisplayName("TableLocationRequest with Oracle schema format")
    fun testTableLocationRequestOracle() {
        val request = TableLocationRequest(
            database = "HR",
            tableName = "EMPLOYEES",
            tablespaceName = "USERS"
        )

        assertEquals("HR", request.database)
        assertEquals("EMPLOYEES", request.tableName)
        assertEquals("USERS", request.tablespaceName)
    }

    // ==================== Size Calculation Tests ====================

    @Test
    @DisplayName("Tablespace size calculations should be accurate")
    fun testTablespaceSizeCalculations() {
        val oneKB = 1024L
        val oneMB = 1024L * 1024L
        val oneGB = 1024L * 1024L * 1024L

        val smallTS = TablespaceInfo("small", "PERMANENT", oneMB, oneMB / 2, "ONLINE")
        val mediumTS = TablespaceInfo("medium", "PERMANENT", oneGB, oneGB / 4, "ONLINE")
        val largeTS = TablespaceInfo("large", "PERMANENT", 10 * oneGB, 5 * oneGB, "ONLINE")

        assertEquals(1048576L, smallTS.fileSize)
        assertEquals(1073741824L, mediumTS.fileSize)
        assertEquals(10737418240L, largeTS.fileSize)
    }

    @Test
    @DisplayName("Tablespace used percentage can be calculated")
    fun testTablespaceUsageCalculation() {
        val tsInfo = TablespaceInfo(
            name = "DATA",
            spaceType = "PERMANENT",
            fileSize = 1000L,
            allocatedSize = 750L,
            state = "ONLINE"
        )

        val usagePercent = (tsInfo.allocatedSize.toDouble() / tsInfo.fileSize.toDouble()) * 100
        assertEquals(75.0, usagePercent, 0.01)
    }

    // ==================== State Tests ====================

    @Test
    @DisplayName("Different tablespace states should be supported")
    fun testTablespaceStates() {
        val states = listOf("ONLINE", "OFFLINE", "READ ONLY", "active", "ACTIVE")

        states.forEach { state ->
            val ts = TablespaceInfo("test", "PERMANENT", 1000L, 500L, state)
            assertEquals(state, ts.state)
        }
    }

    // ==================== Collection Tests ====================

    @Test
    @DisplayName("List of TablespaceInfo should be sortable by name")
    fun testTablespaceInfoListSorting() {
        val tablespaces = listOf(
            TablespaceInfo("USERS", "PERMANENT", 1000L, 500L, "ONLINE"),
            TablespaceInfo("DATA", "PERMANENT", 2000L, 1000L, "ONLINE"),
            TablespaceInfo("TEMP", "TEMPORARY", 500L, 200L, "ONLINE")
        )

        val sorted = tablespaces.sortedBy { it.name }

        assertEquals("DATA", sorted[0].name)
        assertEquals("TEMP", sorted[1].name)
        assertEquals("USERS", sorted[2].name)
    }

    @Test
    @DisplayName("List of TablespaceInfo should be sortable by size")
    fun testTablespaceInfoListSortingBySize() {
        val tablespaces = listOf(
            TablespaceInfo("SMALL", "PERMANENT", 100L, 50L, "ONLINE"),
            TablespaceInfo("LARGE", "PERMANENT", 1000L, 500L, "ONLINE"),
            TablespaceInfo("MEDIUM", "PERMANENT", 500L, 250L, "ONLINE")
        )

        val sortedBySize = tablespaces.sortedByDescending { it.fileSize }

        assertEquals("LARGE", sortedBySize[0].name)
        assertEquals("MEDIUM", sortedBySize[1].name)
        assertEquals("SMALL", sortedBySize[2].name)
    }
}
