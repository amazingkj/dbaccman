package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.dialect.PostgreSQLDialect
import com.dbaccman.model.*
import com.dbaccman.util.AuditLogger
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
import java.sql.Statement

class TablespaceServiceTest {

    private lateinit var tablespaceService: TablespaceService

    @BeforeEach
    fun setUp() {
        tablespaceService = TablespaceService()
        mockkObject(SessionConnectionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
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

    // ==================== Service Method Tests with Mocking ====================

    @Nested
    @DisplayName("Service Method Tests with Mocking")
    inner class ServiceMethodTests {

        @Test
        @DisplayName("getTablespaces should return list from MySQL database")
        fun testGetTablespacesMysql() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getTablespacesQuery() } returns "SELECT * FROM INFORMATION_SCHEMA.INNODB_TABLESPACES"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            // Mock result set to return two tablespaces
            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getString("name") } returnsMany listOf("innodb_system", "innodb_file_per_table")
            every { mockResultSet.getString("space_type") } returnsMany listOf("System", "General")
            every { mockResultSet.getLong("file_size") } returnsMany listOf(104857600L, 209715200L)
            every { mockResultSet.getLong("allocated_size") } returnsMany listOf(104857600L, 104857600L)
            every { mockResultSet.getString("state") } returnsMany listOf("active", "active")

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val tablespaces = tablespaceService.getTablespaces("test-session")

            assertEquals(2, tablespaces.size)
            assertEquals("innodb_system", tablespaces[0].name)
            assertEquals("innodb_file_per_table", tablespaces[1].name)
            verify { mockDialect.getTablespacesQuery() }
        }

        @Test
        @DisplayName("getTablespaces should return list from Oracle database")
        fun testGetTablespacesOracle() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getTablespacesQuery() } returns "SELECT * FROM DBA_TABLESPACES"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet

            every { mockResultSet.next() } returnsMany listOf(true, true, true, false)
            every { mockResultSet.getString("name") } returnsMany listOf("SYSTEM", "USERS", "TEMP")
            every { mockResultSet.getString("space_type") } returnsMany listOf("PERMANENT", "PERMANENT", "TEMPORARY")
            every { mockResultSet.getLong("file_size") } returnsMany listOf(1073741824L, 536870912L, 268435456L)
            every { mockResultSet.getLong("allocated_size") } returnsMany listOf(536870912L, 268435456L, 134217728L)
            every { mockResultSet.getString("state") } returnsMany listOf("ONLINE", "ONLINE", "ONLINE")

            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val tablespaces = tablespaceService.getTablespaces("test-session")

            assertEquals(3, tablespaces.size)
            assertEquals("SYSTEM", tablespaces[0].name)
            assertEquals("USERS", tablespaces[1].name)
            assertEquals("TEMP", tablespaces[2].name)
            verify { mockDialect.getTablespacesQuery() }
        }

        @Test
        @DisplayName("getTablespaces should return empty list when no tablespaces exist")
        fun testGetTablespacesEmpty() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getTablespacesQuery() } returns "SELECT * FROM INFORMATION_SCHEMA.INNODB_TABLESPACES"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.next() } returns false
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val tablespaces = tablespaceService.getTablespaces("test-session")

            assertTrue(tablespaces.isEmpty())
        }

        @Test
        @DisplayName("createTablespace should execute create statement")
        fun testCreateTablespace() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            val request = CreateTablespaceRequest(
                name = "NEW_TS",
                dataFile = "/data/new_ts.ibd",
                engine = "InnoDB"
            )

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getCreateTablespaceSql("NEW_TS", "/data/new_ts.ibd", "InnoDB") } returns
                "CREATE TABLESPACE NEW_TS ADD DATAFILE '/data/new_ts.ibd' ENGINE=InnoDB"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            // Mock AuditLogger
            mockkObject(AuditLogger)
            every { AuditLogger.log(any(), any()) } just Runs

            tablespaceService.createTablespace("test-session", request)

            verify { mockStatement.execute(any()) }
            verify { AuditLogger.log("CREATE_TABLESPACE", "Created tablespace NEW_TS") }
            unmockkObject(AuditLogger)
        }

        @Test
        @DisplayName("dropTablespace should execute drop statement")
        fun testDropTablespace() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<OracleDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getDropTablespaceSql("OLD_TS") } returns
                "DROP TABLESPACE OLD_TS INCLUDING CONTENTS AND DATAFILES"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            mockkObject(AuditLogger)
            every { AuditLogger.log(any(), any()) } just Runs

            tablespaceService.dropTablespace("test-session", "OLD_TS")

            verify { mockStatement.execute(any()) }
            verify { AuditLogger.log("DROP_TABLESPACE", "Dropped tablespace OLD_TS") }
            unmockkObject(AuditLogger)
        }

        @Test
        @DisplayName("getTablesInTablespace should return tables for given tablespace")
        fun testGetTablesInTablespace() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getTablesInTablespaceQuery() } returns
                "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLESPACE_NAME = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "DATA_TS") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet

            every { mockResultSet.next() } returnsMany listOf(true, true, false)
            every { mockResultSet.getString("db_name") } returnsMany listOf("testdb", "testdb")
            every { mockResultSet.getString("table_name") } returnsMany listOf("users", "orders")
            every { mockResultSet.getString("engine") } returnsMany listOf("InnoDB", "InnoDB")
            every { mockResultSet.getLong("rows") } returnsMany listOf(1000L, 5000L)
            every { mockResultSet.getLong("size") } returnsMany listOf(1048576L, 5242880L)
            every { mockResultSet.getString("create_time") } returnsMany listOf("2024-01-01 00:00:00", "2024-01-02 00:00:00")

            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val tables = tablespaceService.getTablesInTablespace("test-session", "DATA_TS")

            assertEquals(2, tables.size)
            assertEquals("testdb.users", tables[0].name)
            assertEquals("testdb.orders", tables[1].name)
            assertEquals("InnoDB", tables[0].engine)
            verify { mockPreparedStatement.setString(1, "DATA_TS") }
        }

        @Test
        @DisplayName("getTablesInTablespace should return empty list when no tables exist")
        fun testGetTablesInTablespaceEmpty() {
            val mockConnection = mockk<Connection>()
            val mockPreparedStatement = mockk<PreparedStatement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getTablesInTablespaceQuery() } returns
                "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLESPACE_NAME = ?"
            every { mockConnection.prepareStatement(any()) } returns mockPreparedStatement
            every { mockPreparedStatement.setString(1, "EMPTY_TS") } just Runs
            every { mockPreparedStatement.executeQuery() } returns mockResultSet
            every { mockResultSet.next() } returns false
            every { mockResultSet.close() } just Runs
            every { mockPreparedStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val tables = tablespaceService.getTablesInTablespace("test-session", "EMPTY_TS")

            assertTrue(tables.isEmpty())
        }

        @Test
        @DisplayName("moveTableToTablespace should execute alter table statement")
        fun testMoveTableToTablespace() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockDialect = mockk<MySQLDialect>()

            val request = TableLocationRequest(
                database = "testdb",
                tableName = "users",
                tablespaceName = "NEW_TS"
            )

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getMoveTableToTablespaceSql("testdb", "users", "NEW_TS") } returns
                "ALTER TABLE testdb.users TABLESPACE NEW_TS"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.execute(any()) } returns true
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            mockkObject(AuditLogger)
            every { AuditLogger.log(any(), any()) } just Runs

            tablespaceService.moveTableToTablespace("test-session", request)

            verify { mockStatement.execute(any()) }
            verify { AuditLogger.log("MOVE_TABLE", "Moved table testdb.users to tablespace NEW_TS") }
            unmockkObject(AuditLogger)
        }
    }
}
