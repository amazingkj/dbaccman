package com.dbaccman.model

import com.dbaccman.dialect.DatabaseType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class ModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== Account Model Tests ====================

    @Nested
    @DisplayName("Account Model Tests")
    inner class AccountTests {

        @Test
        @DisplayName("Account should have correct properties")
        fun testAccountProperties() {
            val account = Account(
                username = "testuser",
                host = "localhost",
                created = "2024-01-01",
                passwordLastChanged = "2024-06-01",
                passwordLifetime = 90,
                accountLocked = false
            )

            assertEquals("testuser", account.username)
            assertEquals("localhost", account.host)
            assertEquals("2024-01-01", account.created)
            assertEquals("2024-06-01", account.passwordLastChanged)
            assertEquals(90, account.passwordLifetime)
            assertFalse(account.accountLocked)
        }

        @Test
        @DisplayName("Account should have default values")
        fun testAccountDefaults() {
            val account = Account(username = "user", host = "%")

            assertNull(account.created)
            assertNull(account.passwordLastChanged)
            assertNull(account.passwordLifetime)
            assertFalse(account.accountLocked)
        }

        @Test
        @DisplayName("Account should serialize to JSON correctly")
        fun testAccountSerialization() {
            val account = Account(
                username = "admin",
                host = "%",
                accountLocked = true
            )

            val jsonStr = json.encodeToString(account)
            assertTrue(jsonStr.contains("\"username\":\"admin\""))
            assertTrue(jsonStr.contains("\"host\":\"%\""))
            assertTrue(jsonStr.contains("\"accountLocked\":true"))
        }

        @Test
        @DisplayName("Account should deserialize from JSON correctly")
        fun testAccountDeserialization() {
            val jsonStr = """{"username":"user1","host":"localhost","accountLocked":false}"""
            val account = json.decodeFromString<Account>(jsonStr)

            assertEquals("user1", account.username)
            assertEquals("localhost", account.host)
            assertFalse(account.accountLocked)
        }
    }

    // ==================== CreateAccountRequest Tests ====================

    @Nested
    @DisplayName("CreateAccountRequest Tests")
    inner class CreateAccountRequestTests {

        @Test
        @DisplayName("CreateAccountRequest should have correct properties")
        fun testCreateAccountRequestProperties() {
            val request = CreateAccountRequest(
                username = "newuser",
                host = "192.168.1.%",
                password = "securepass",
                expireDays = 60
            )

            assertEquals("newuser", request.username)
            assertEquals("192.168.1.%", request.host)
            assertEquals("securepass", request.password)
            assertEquals(60, request.expireDays)
        }

        @Test
        @DisplayName("CreateAccountRequest should have default values")
        fun testCreateAccountRequestDefaults() {
            val request = CreateAccountRequest(
                username = "user",
                password = "pass"
            )

            assertEquals("%", request.host)
            assertEquals(90, request.expireDays)
        }
    }

    // ==================== ChangePasswordRequest Tests ====================

    @Nested
    @DisplayName("ChangePasswordRequest Tests")
    inner class ChangePasswordRequestTests {

        @Test
        @DisplayName("ChangePasswordRequest should have correct properties")
        fun testChangePasswordRequestProperties() {
            val request = ChangePasswordRequest(
                password = "newpassword",
                expireImmediately = true
            )

            assertEquals("newpassword", request.password)
            assertTrue(request.expireImmediately)
        }

        @Test
        @DisplayName("ChangePasswordRequest should have default expireImmediately as false")
        fun testChangePasswordRequestDefaults() {
            val request = ChangePasswordRequest(password = "pass")
            assertFalse(request.expireImmediately)
        }
    }

    // ==================== ExpiringAccount Tests ====================

    @Nested
    @DisplayName("ExpiringAccount Tests")
    inner class ExpiringAccountTests {

        @Test
        @DisplayName("ExpiringAccount should have correct properties")
        fun testExpiringAccountProperties() {
            val account = ExpiringAccount(
                username = "expiring_user",
                host = "localhost",
                daysUntilExpiry = 7
            )

            assertEquals("expiring_user", account.username)
            assertEquals("localhost", account.host)
            assertEquals(7, account.daysUntilExpiry)
        }

        @Test
        @DisplayName("ExpiringAccount with zero days should be valid")
        fun testExpiringAccountZeroDays() {
            val account = ExpiringAccount("user", "%", 0)
            assertEquals(0, account.daysUntilExpiry)
        }

        @Test
        @DisplayName("ExpiringAccount with negative days should be valid (already expired)")
        fun testExpiringAccountNegativeDays() {
            val account = ExpiringAccount("user", "%", -5)
            assertEquals(-5, account.daysUntilExpiry)
        }
    }

    // ==================== CloneAccountRequest Tests ====================

    @Nested
    @DisplayName("CloneAccountRequest Tests")
    inner class CloneAccountRequestTests {

        @Test
        @DisplayName("CloneAccountRequest should have correct properties")
        fun testCloneAccountRequestProperties() {
            val request = CloneAccountRequest(
                sourceUsername = "source_user",
                sourceHost = "localhost",
                newUsername = "cloned_user",
                newHost = "192.168.%",
                newPassword = "clonepass",
                copyPermissions = true,
                expireDays = 120
            )

            assertEquals("source_user", request.sourceUsername)
            assertEquals("localhost", request.sourceHost)
            assertEquals("cloned_user", request.newUsername)
            assertEquals("192.168.%", request.newHost)
            assertEquals("clonepass", request.newPassword)
            assertTrue(request.copyPermissions)
            assertEquals(120, request.expireDays)
        }

        @Test
        @DisplayName("CloneAccountRequest should have default values")
        fun testCloneAccountRequestDefaults() {
            val request = CloneAccountRequest(
                sourceUsername = "src",
                newUsername = "dst",
                newPassword = "pass"
            )

            assertEquals("%", request.sourceHost)
            assertEquals("%", request.newHost)
            assertTrue(request.copyPermissions)
            assertEquals(90, request.expireDays)
        }
    }

    // ==================== Batch Operation Tests ====================

    @Nested
    @DisplayName("Batch Operation Tests")
    inner class BatchOperationTests {

        @Test
        @DisplayName("BatchCreateAccountRequest should contain list of accounts")
        fun testBatchCreateAccountRequest() {
            val request = BatchCreateAccountRequest(
                accounts = listOf(
                    CreateAccountRequest("user1", "%", "pass1"),
                    CreateAccountRequest("user2", "%", "pass2")
                )
            )

            assertEquals(2, request.accounts.size)
            assertEquals("user1", request.accounts[0].username)
            assertEquals("user2", request.accounts[1].username)
        }

        @Test
        @DisplayName("BatchDeleteRequest should contain list of account identifiers")
        fun testBatchDeleteRequest() {
            val request = BatchDeleteRequest(
                accounts = listOf(
                    AccountIdentifier("user1", "localhost"),
                    AccountIdentifier("user2", "%")
                )
            )

            assertEquals(2, request.accounts.size)
        }

        @Test
        @DisplayName("BatchOperationResult should contain success and failed lists")
        fun testBatchOperationResult() {
            val result = BatchOperationResult(
                success = listOf("user1", "user2"),
                failed = listOf(
                    BatchOperationError("user3", "Access denied")
                )
            )

            assertEquals(2, result.success.size)
            assertEquals(1, result.failed.size)
            assertEquals("user3", result.failed[0].account)
            assertEquals("Access denied", result.failed[0].error)
        }

        @Test
        @DisplayName("AccountIdentifier should have default host")
        fun testAccountIdentifierDefaults() {
            val identifier = AccountIdentifier("user")
            assertEquals("%", identifier.host)
        }
    }

    // ==================== Role Tests ====================

    @Nested
    @DisplayName("Role Tests")
    inner class RoleTests {

        @Test
        @DisplayName("Role should have correct properties")
        fun testRoleProperties() {
            val role = Role(
                name = "DBA",
                isDefault = true,
                isAdmin = true
            )

            assertEquals("DBA", role.name)
            assertTrue(role.isDefault)
            assertTrue(role.isAdmin)
        }

        @Test
        @DisplayName("Role should have default values")
        fun testRoleDefaults() {
            val role = Role(name = "CONNECT")

            assertFalse(role.isDefault)
            assertFalse(role.isAdmin)
        }

        @Test
        @DisplayName("UserRole should have correct properties")
        fun testUserRoleProperties() {
            val userRole = UserRole(
                username = "admin",
                roleName = "DBA",
                isDefault = true,
                isAdmin = true
            )

            assertEquals("admin", userRole.username)
            assertEquals("DBA", userRole.roleName)
            assertTrue(userRole.isDefault)
            assertTrue(userRole.isAdmin)
        }

        @Test
        @DisplayName("GrantRoleRequest should have correct properties")
        fun testGrantRoleRequest() {
            val request = GrantRoleRequest(
                username = "user1",
                host = "localhost",
                roles = listOf("CONNECT", "RESOURCE"),
                withAdminOption = true
            )

            assertEquals("user1", request.username)
            assertEquals("localhost", request.host)
            assertEquals(2, request.roles.size)
            assertTrue(request.withAdminOption)
        }

        @Test
        @DisplayName("GrantRoleRequest should have default values")
        fun testGrantRoleRequestDefaults() {
            val request = GrantRoleRequest(
                username = "user",
                roles = listOf("CONNECT")
            )

            assertEquals("%", request.host)
            assertFalse(request.withAdminOption)
        }

        @Test
        @DisplayName("RevokeRoleRequest should have correct properties")
        fun testRevokeRoleRequest() {
            val request = RevokeRoleRequest(
                username = "user1",
                host = "%",
                roles = listOf("DBA")
            )

            assertEquals("user1", request.username)
            assertEquals(1, request.roles.size)
        }
    }

    // ==================== PDB Info Tests ====================

    @Nested
    @DisplayName("PdbInfo Tests")
    inner class PdbInfoTests {

        @Test
        @DisplayName("PdbInfo should have correct properties")
        fun testPdbInfoProperties() {
            val pdb = PdbInfo(
                name = "ORCLPDB1",
                openMode = "READ WRITE",
                restricted = false
            )

            assertEquals("ORCLPDB1", pdb.name)
            assertEquals("READ WRITE", pdb.openMode)
            assertFalse(pdb.restricted)
        }

        @Test
        @DisplayName("PdbInfo should have default restricted as false")
        fun testPdbInfoDefaults() {
            val pdb = PdbInfo(name = "PDB1", openMode = "MOUNTED")
            assertFalse(pdb.restricted)
        }
    }

    // ==================== Session Model Tests ====================

    @Nested
    @DisplayName("Session Model Tests")
    inner class SessionModelTests {

        @Test
        @DisplayName("SessionInfo should have correct properties")
        fun testSessionInfoProperties() {
            val session = SessionInfo(
                pid = 12345,
                serialNum = 100,
                user = "admin",
                host = "192.168.1.100",
                database = "testdb",
                command = "Query",
                time = 120,
                state = "executing",
                query = "SELECT * FROM users"
            )

            assertEquals(12345, session.pid)
            assertEquals(100, session.serialNum)
            assertEquals("admin", session.user)
            assertEquals("192.168.1.100", session.host)
            assertEquals("testdb", session.database)
            assertEquals("Query", session.command)
            assertEquals(120, session.time)
            assertEquals("executing", session.state)
            assertEquals("SELECT * FROM users", session.query)
        }

        @Test
        @DisplayName("SessionInfo should have nullable fields")
        fun testSessionInfoNullable() {
            val session = SessionInfo(
                pid = 1,
                user = "user",
                host = "localhost",
                database = null,
                command = "Sleep",
                time = 0,
                state = null,
                query = null
            )

            assertNull(session.serialNum)
            assertNull(session.database)
            assertNull(session.state)
            assertNull(session.query)
        }

        @Test
        @DisplayName("SessionStats should have correct properties")
        fun testSessionStatsProperties() {
            val stats = SessionStats(
                totalSessions = 100,
                activeSessions = 25,
                sleepingSessions = 70,
                longRunningSessions = 5
            )

            assertEquals(100, stats.totalSessions)
            assertEquals(25, stats.activeSessions)
            assertEquals(70, stats.sleepingSessions)
            assertEquals(5, stats.longRunningSessions)
        }
    }

    // ==================== Table Model Tests ====================

    @Nested
    @DisplayName("Table Model Tests")
    inner class TableModelTests {

        @Test
        @DisplayName("DatabaseInfo should have correct properties")
        fun testDatabaseInfoProperties() {
            val db = DatabaseInfo(
                name = "testdb",
                tableCount = 50,
                totalRows = 1000000,
                size = 104857600
            )

            assertEquals("testdb", db.name)
            assertEquals(50, db.tableCount)
            assertEquals(1000000, db.totalRows)
            assertEquals(104857600, db.size)
        }

        @Test
        @DisplayName("TableInfo should have correct properties")
        fun testTableInfoProperties() {
            val table = TableInfo(
                name = "users",
                engine = "InnoDB",
                rows = 50000,
                size = 10485760,
                createTime = "2024-01-01 10:00:00"
            )

            assertEquals("users", table.name)
            assertEquals("InnoDB", table.engine)
            assertEquals(50000, table.rows)
            assertEquals(10485760, table.size)
            assertEquals("2024-01-01 10:00:00", table.createTime)
        }

        @Test
        @DisplayName("TableInfo should have nullable fields")
        fun testTableInfoNullable() {
            val table = TableInfo(
                name = "temp",
                engine = null,
                rows = 0,
                size = 0,
                createTime = null
            )

            assertNull(table.engine)
            assertNull(table.createTime)
        }

        @Test
        @DisplayName("IndexInfo should have correct properties")
        fun testIndexInfoProperties() {
            val index = IndexInfo(
                name = "idx_user_email",
                columns = listOf("user_id", "email"),
                unique = true,
                type = "BTREE"
            )

            assertEquals("idx_user_email", index.name)
            assertEquals(2, index.columns.size)
            assertTrue(index.unique)
            assertEquals("BTREE", index.type)
        }

        @Test
        @DisplayName("CreateIndexRequest should have correct properties")
        fun testCreateIndexRequestProperties() {
            val request = CreateIndexRequest(
                database = "mydb",
                table = "users",
                indexName = "idx_name",
                columns = listOf("first_name", "last_name"),
                unique = false
            )

            assertEquals("mydb", request.database)
            assertEquals("users", request.table)
            assertEquals("idx_name", request.indexName)
            assertEquals(2, request.columns.size)
            assertFalse(request.unique)
        }

        @Test
        @DisplayName("CreateIndexRequest should have default unique as false")
        fun testCreateIndexRequestDefaults() {
            val request = CreateIndexRequest(
                database = "db",
                table = "tbl",
                indexName = "idx",
                columns = listOf("col")
            )

            assertFalse(request.unique)
        }

        @Test
        @DisplayName("ColumnInfo should have correct properties")
        fun testColumnInfoProperties() {
            val column = ColumnInfo(
                name = "id",
                type = "BIGINT",
                nullable = false,
                key = "PRI",
                defaultValue = null,
                extra = "auto_increment"
            )

            assertEquals("id", column.name)
            assertEquals("BIGINT", column.type)
            assertFalse(column.nullable)
            assertEquals("PRI", column.key)
            assertNull(column.defaultValue)
            assertEquals("auto_increment", column.extra)
        }
    }

    // ==================== Permission Model Tests ====================

    @Nested
    @DisplayName("Permission Model Tests")
    inner class PermissionModelTests {

        @Test
        @DisplayName("Permission should have correct properties")
        fun testPermissionProperties() {
            val permission = Permission(
                grantee = "'user'@'%'",
                database = "testdb",
                table = "users",
                privilege = "SELECT",
                isGrantable = true
            )

            assertEquals("'user'@'%'", permission.grantee)
            assertEquals("testdb", permission.database)
            assertEquals("users", permission.table)
            assertEquals("SELECT", permission.privilege)
            assertTrue(permission.isGrantable)
        }

        @Test
        @DisplayName("Permission should have default values")
        fun testPermissionDefaults() {
            val permission = Permission(
                grantee = "user",
                database = "db",
                privilege = "SELECT"
            )

            assertEquals("*", permission.table)
            assertFalse(permission.isGrantable)
        }

        @Test
        @DisplayName("GrantPermissionRequest should have correct properties")
        fun testGrantPermissionRequestProperties() {
            val request = GrantPermissionRequest(
                username = "developer",
                host = "192.168.%",
                database = "prod_db",
                table = "orders",
                privileges = listOf("SELECT", "INSERT", "UPDATE")
            )

            assertEquals("developer", request.username)
            assertEquals("192.168.%", request.host)
            assertEquals("prod_db", request.database)
            assertEquals("orders", request.table)
            assertEquals(3, request.privileges.size)
        }

        @Test
        @DisplayName("GrantPermissionRequest should have default values")
        fun testGrantPermissionRequestDefaults() {
            val request = GrantPermissionRequest(
                username = "user",
                privileges = listOf("SELECT")
            )

            assertEquals("%", request.host)
            assertEquals("", request.database)
            assertEquals("*", request.table)
        }

        @Test
        @DisplayName("MySQLPrivileges ALL should contain all privileges")
        fun testMySQLPrivilegesAll() {
            val all = MySQLPrivileges.ALL
            assertTrue(all.contains("SELECT"))
            assertTrue(all.contains("INSERT"))
            assertTrue(all.contains("UPDATE"))
            assertTrue(all.contains("DELETE"))
            assertTrue(all.contains("CREATE"))
            assertTrue(all.contains("DROP"))
            assertTrue(all.contains("INDEX"))
            assertTrue(all.contains("ALTER"))
        }

        @Test
        @DisplayName("MySQLPrivileges READ_ONLY should only contain SELECT")
        fun testMySQLPrivilegesReadOnly() {
            val readOnly = MySQLPrivileges.READ_ONLY
            assertEquals(1, readOnly.size)
            assertEquals("SELECT", readOnly[0])
        }

        @Test
        @DisplayName("MySQLPrivileges READ_WRITE should contain CRUD operations")
        fun testMySQLPrivilegesReadWrite() {
            val readWrite = MySQLPrivileges.READ_WRITE
            assertEquals(4, readWrite.size)
            assertTrue(readWrite.contains("SELECT"))
            assertTrue(readWrite.contains("INSERT"))
            assertTrue(readWrite.contains("UPDATE"))
            assertTrue(readWrite.contains("DELETE"))
        }

        @Test
        @DisplayName("MySQLPrivileges DDL should contain schema modification operations")
        fun testMySQLPrivilegesDdl() {
            val ddl = MySQLPrivileges.DDL
            assertEquals(4, ddl.size)
            assertTrue(ddl.contains("CREATE"))
            assertTrue(ddl.contains("DROP"))
            assertTrue(ddl.contains("INDEX"))
            assertTrue(ddl.contains("ALTER"))
        }
    }

    // ==================== Pagination Model Tests ====================

    @Nested
    @DisplayName("Pagination Model Tests")
    inner class PaginationModelTests {

        @Test
        @DisplayName("PaginationInfo should have correct properties")
        fun testPaginationInfoProperties() {
            val info = PaginationInfo(
                page = 2,
                pageSize = 20,
                totalItems = 100,
                totalPages = 5
            )

            assertEquals(2, info.page)
            assertEquals(20, info.pageSize)
            assertEquals(100, info.totalItems)
            assertEquals(5, info.totalPages)
        }

        @Test
        @DisplayName("PaginationInfo.of should calculate totalPages correctly")
        fun testPaginationInfoOf() {
            val info = PaginationInfo.of(page = 1, pageSize = 10, totalItems = 95)

            assertEquals(1, info.page)
            assertEquals(10, info.pageSize)
            assertEquals(95, info.totalItems)
            assertEquals(10, info.totalPages)  // ceil(95/10) = 10
        }

        @Test
        @DisplayName("PaginationInfo.of should return 1 page for zero items")
        fun testPaginationInfoOfZeroItems() {
            val info = PaginationInfo.of(page = 1, pageSize = 10, totalItems = 0)

            assertEquals(1, info.totalPages)
        }

        @Test
        @DisplayName("PaginationInfo.of should handle exact division")
        fun testPaginationInfoOfExactDivision() {
            val info = PaginationInfo.of(page = 1, pageSize = 25, totalItems = 100)

            assertEquals(4, info.totalPages)  // 100/25 = 4
        }

        @Test
        @DisplayName("PaginationInfo.of should handle single item")
        fun testPaginationInfoOfSingleItem() {
            val info = PaginationInfo.of(page = 1, pageSize = 10, totalItems = 1)

            assertEquals(1, info.totalPages)
        }

        @Test
        @DisplayName("PaginatedAccountsResponse should have correct structure")
        fun testPaginatedAccountsResponse() {
            val response = PaginatedAccountsResponse(
                data = listOf(Account("user1", "%"), Account("user2", "%")),
                pagination = PaginationInfo(1, 10, 2, 1),
                stats = AccountStats(totalAccounts = 100, lockedAccounts = 5, activeAccounts = 95)
            )

            assertEquals(2, response.data.size)
            assertEquals(1, response.pagination.page)
            assertEquals(100, response.stats.totalAccounts)
            assertEquals(5, response.stats.lockedAccounts)
            assertEquals(95, response.stats.activeAccounts)
        }
    }

    // ==================== Connection Model Tests ====================

    @Nested
    @DisplayName("Connection Model Tests")
    inner class ConnectionModelTests {

        @Test
        @DisplayName("ConnectionLoginRequest should have correct properties")
        fun testConnectionLoginRequestProperties() {
            val request = ConnectionLoginRequest(
                host = "db.example.com",
                port = 3306,
                username = "admin",
                password = "secret",
                dbType = DatabaseType.MYSQL,
                database = "production"
            )

            assertEquals("db.example.com", request.host)
            assertEquals(3306, request.port)
            assertEquals("admin", request.username)
            assertEquals("secret", request.password)
            assertEquals(DatabaseType.MYSQL, request.dbType)
            assertEquals("production", request.database)
        }

        @Test
        @DisplayName("ConnectionLoginRequest getEffectivePort should return custom port")
        fun testGetEffectivePortCustom() {
            val request = ConnectionLoginRequest(
                host = "localhost",
                port = 13306,
                username = "user",
                password = "pass"
            )

            assertEquals(13306, request.getEffectivePort())
        }

        @Test
        @DisplayName("ConnectionLoginRequest getEffectivePort should return default MySQL port")
        fun testGetEffectivePortMySqlDefault() {
            val request = ConnectionLoginRequest(
                host = "localhost",
                username = "user",
                password = "pass",
                dbType = DatabaseType.MYSQL
            )

            assertEquals(3306, request.getEffectivePort())
        }

        @Test
        @DisplayName("ConnectionLoginRequest getEffectivePort should return default PostgreSQL port")
        fun testGetEffectivePortPostgreSqlDefault() {
            val request = ConnectionLoginRequest(
                host = "localhost",
                username = "user",
                password = "pass",
                dbType = DatabaseType.POSTGRESQL
            )

            assertEquals(5432, request.getEffectivePort())
        }

        @Test
        @DisplayName("ConnectionLoginRequest getEffectivePort should return default Oracle port")
        fun testGetEffectivePortOracleDefault() {
            val request = ConnectionLoginRequest(
                host = "localhost",
                username = "user",
                password = "pass",
                dbType = DatabaseType.ORACLE
            )

            assertEquals(1521, request.getEffectivePort())
        }

        @Test
        @DisplayName("ConnectionLoginResponse should have correct properties")
        fun testConnectionLoginResponseProperties() {
            val response = ConnectionLoginResponse(
                token = "jwt-token",
                username = "admin",
                role = "DBA",
                host = "localhost",
                port = 3306,
                dbType = DatabaseType.MYSQL,
                passwordExpiryDays = 30
            )

            assertEquals("jwt-token", response.token)
            assertEquals("admin", response.username)
            assertEquals("DBA", response.role)
            assertEquals("localhost", response.host)
            assertEquals(3306, response.port)
            assertEquals(DatabaseType.MYSQL, response.dbType)
            assertEquals(30, response.passwordExpiryDays)
        }

        @Test
        @DisplayName("ConnectionInfo should have correct properties")
        fun testConnectionInfoProperties() {
            val info = ConnectionInfo(
                host = "192.168.1.100",
                port = 5432,
                username = "postgres",
                dbType = DatabaseType.POSTGRESQL
            )

            assertEquals("192.168.1.100", info.host)
            assertEquals(5432, info.port)
            assertEquals("postgres", info.username)
            assertEquals(DatabaseType.POSTGRESQL, info.dbType)
        }

        @Test
        @DisplayName("PasswordExpiryInfo should have correct properties")
        fun testPasswordExpiryInfoProperties() {
            val info = PasswordExpiryInfo(
                username = "admin",
                host = "localhost",
                daysUntilExpiry = 15,
                passwordLastChanged = "2024-06-01",
                isExpired = false
            )

            assertEquals("admin", info.username)
            assertEquals("localhost", info.host)
            assertEquals(15, info.daysUntilExpiry)
            assertEquals("2024-06-01", info.passwordLastChanged)
            assertFalse(info.isExpired)
        }

        @Test
        @DisplayName("PasswordExpiryInfo should handle null values")
        fun testPasswordExpiryInfoNullable() {
            val info = PasswordExpiryInfo(
                username = "user",
                host = "%",
                daysUntilExpiry = null,
                passwordLastChanged = null,
                isExpired = false
            )

            assertNull(info.daysUntilExpiry)
            assertNull(info.passwordLastChanged)
        }
    }

    // ==================== Tablespace Model Tests ====================

    @Nested
    @DisplayName("Tablespace Model Tests")
    inner class TablespaceModelTests {

        @Test
        @DisplayName("TablespaceInfo should have correct properties")
        fun testTablespaceInfoProperties() {
            val tablespace = TablespaceInfo(
                name = "users_ts",
                spaceType = "GENERAL",
                fileSize = 1073741824,
                allocatedSize = 536870912,
                state = "ACTIVE",
                filePath = "/data/users_ts.ibd"
            )

            assertEquals("users_ts", tablespace.name)
            assertEquals("GENERAL", tablespace.spaceType)
            assertEquals(1073741824, tablespace.fileSize)
            assertEquals(536870912, tablespace.allocatedSize)
            assertEquals("ACTIVE", tablespace.state)
            assertEquals("/data/users_ts.ibd", tablespace.filePath)
        }

        @Test
        @DisplayName("TablespaceInfo should have nullable filePath")
        fun testTablespaceInfoNullablePath() {
            val tablespace = TablespaceInfo(
                name = "ts1",
                spaceType = "SYSTEM",
                fileSize = 0,
                allocatedSize = 0,
                state = "ACTIVE"
            )

            assertNull(tablespace.filePath)
        }

        @Test
        @DisplayName("CreateTablespaceRequest should have correct properties")
        fun testCreateTablespaceRequestProperties() {
            val request = CreateTablespaceRequest(
                name = "new_tablespace",
                dataFile = "/data/new_ts.ibd",
                engine = "InnoDB"
            )

            assertEquals("new_tablespace", request.name)
            assertEquals("/data/new_ts.ibd", request.dataFile)
            assertEquals("InnoDB", request.engine)
        }

        @Test
        @DisplayName("CreateTablespaceRequest should have default values")
        fun testCreateTablespaceRequestDefaults() {
            val request = CreateTablespaceRequest(name = "ts")

            assertNull(request.dataFile)
            assertEquals("InnoDB", request.engine)
        }

        @Test
        @DisplayName("TableLocationRequest should have correct properties")
        fun testTableLocationRequestProperties() {
            val request = TableLocationRequest(
                database = "mydb",
                tableName = "large_table",
                tablespaceName = "large_ts"
            )

            assertEquals("mydb", request.database)
            assertEquals("large_table", request.tableName)
            assertEquals("large_ts", request.tablespaceName)
        }
    }

    // ==================== Dashboard Model Tests ====================

    @Nested
    @DisplayName("Dashboard Model Tests")
    inner class DashboardModelTests {

        @Test
        @DisplayName("DashboardStats should have correct properties")
        fun testDashboardStatsProperties() {
            val healthScore = HealthScore(75, 30, 25, 20, "warning", listOf("10개 계정이 30일 내 만료 예정"))

            val stats = DashboardStats(
                totalAccounts = 150,
                activeSessions = 25,
                expiringSoon = 10,
                slowQueries = 3,
                lockedAccounts = 2,
                totalDatabases = 5,
                totalTables = 200,
                tablespaceUsage = 65,
                criticalTablespaces = 0,
                healthScore = healthScore,
                expiringAccounts = listOf(
                    ExpiringAccount("user1", "%", 5),
                    ExpiringAccount("user2", "%", 10)
                ),
                longRunningSessions = listOf(
                    SessionInfo(1, null, "admin", "localhost", "db", "Query", 300, "executing", "SELECT...")
                ),
                topDatabases = listOf(
                    DatabaseInfo("large_db", 100, 5000000, 1073741824)
                )
            )

            assertEquals(150, stats.totalAccounts)
            assertEquals(25, stats.activeSessions)
            assertEquals(10, stats.expiringSoon)
            assertEquals(3, stats.slowQueries)
            assertEquals(2, stats.lockedAccounts)
            assertEquals(5, stats.totalDatabases)
            assertEquals(200, stats.totalTables)
            assertEquals(65, stats.tablespaceUsage)
            assertEquals(0, stats.criticalTablespaces)
            assertEquals(75, stats.healthScore.total)
            assertEquals("warning", stats.healthScore.status)
            assertEquals(2, stats.expiringAccounts.size)
            assertEquals(1, stats.longRunningSessions.size)
            assertEquals(1, stats.topDatabases.size)
        }

        @Test
        @DisplayName("DashboardStats should handle empty lists")
        fun testDashboardStatsEmptyLists() {
            val healthScore = HealthScore(100, 40, 30, 30, "healthy", emptyList())

            val stats = DashboardStats(
                totalAccounts = 0,
                activeSessions = 0,
                expiringSoon = 0,
                slowQueries = 0,
                lockedAccounts = 0,
                totalDatabases = 0,
                totalTables = 0,
                tablespaceUsage = 0,
                criticalTablespaces = 0,
                healthScore = healthScore,
                expiringAccounts = emptyList(),
                longRunningSessions = emptyList(),
                topDatabases = emptyList()
            )

            assertTrue(stats.expiringAccounts.isEmpty())
            assertTrue(stats.longRunningSessions.isEmpty())
            assertTrue(stats.topDatabases.isEmpty())
            assertEquals(100, stats.healthScore.total)
            assertEquals("healthy", stats.healthScore.status)
        }

        @Test
        @DisplayName("HealthScore should calculate correct status")
        fun testHealthScoreStatus() {
            val healthy = HealthScore(85, 38, 27, 20, "healthy", emptyList())
            val warning = HealthScore(65, 25, 20, 20, "warning", listOf("5개 계정이 30일 내 만료 예정"))
            val critical = HealthScore(45, 15, 15, 15, "critical", listOf("많은 계정이 만료 예정", "Tablespace 사용률 높음"))

            assertEquals("healthy", healthy.status)
            assertEquals("warning", warning.status)
            assertEquals("critical", critical.status)
            assertTrue(healthy.total >= 80)
            assertTrue(warning.total >= 60 && warning.total < 80)
            assertTrue(critical.total < 60)
        }
    }

    // ==================== LoginRequest/Response Tests ====================

    @Nested
    @DisplayName("Login Model Tests")
    inner class LoginModelTests {

        @Test
        @DisplayName("LoginRequest should have correct properties")
        fun testLoginRequestProperties() {
            val request = LoginRequest(
                username = "admin",
                password = "secretpass"
            )

            assertEquals("admin", request.username)
            assertEquals("secretpass", request.password)
        }

        @Test
        @DisplayName("LoginResponse should have correct properties")
        fun testLoginResponseProperties() {
            val response = LoginResponse(
                token = "jwt-token-here",
                username = "admin",
                role = "administrator"
            )

            assertEquals("jwt-token-here", response.token)
            assertEquals("admin", response.username)
            assertEquals("administrator", response.role)
        }

        @Test
        @DisplayName("UserInfo should have correct properties")
        fun testUserInfoProperties() {
            val userInfo = UserInfo(
                username = "developer",
                role = "user"
            )

            assertEquals("developer", userInfo.username)
            assertEquals("user", userInfo.role)
        }

        @Test
        @DisplayName("SetTablespaceRequest should have correct properties")
        fun testSetTablespaceRequestProperties() {
            val request = SetTablespaceRequest(
                username = "app_user",
                host = "localhost",
                tablespace = "USERS",
                quota = "100M"
            )

            assertEquals("app_user", request.username)
            assertEquals("localhost", request.host)
            assertEquals("USERS", request.tablespace)
            assertEquals("100M", request.quota)
        }

        @Test
        @DisplayName("SetTablespaceRequest should have default values")
        fun testSetTablespaceRequestDefaults() {
            val request = SetTablespaceRequest(
                username = "user",
                tablespace = "TS"
            )

            assertEquals("%", request.host)
            assertNull(request.quota)
        }

        @Test
        @DisplayName("ExportRequest should have default values")
        fun testExportRequestDefaults() {
            val request = ExportRequest()

            assertEquals("csv", request.format)
            assertFalse(request.includePermissions)
        }

        @Test
        @DisplayName("ExportRequest should accept custom values")
        fun testExportRequestCustomValues() {
            val request = ExportRequest(
                format = "json",
                includePermissions = true
            )

            assertEquals("json", request.format)
            assertTrue(request.includePermissions)
        }
    }
}
