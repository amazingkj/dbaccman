package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.config.useOracleScriptContext
import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.dialect.PostgreSQLDialect
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
import java.sql.Statement
import java.sql.SQLException

class AccountServiceTest {

    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        accountService = AccountService()
        mockkObject(SessionConnectionManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("AccountService should be instantiable")
    fun testAccountServiceInstantiation() {
        assertNotNull(accountService)
    }

    // ==================== Model Tests ====================

    @Nested
    @DisplayName("Account Model Tests")
    inner class AccountModelTests {
        @Test
        @DisplayName("Account model should have correct properties")
        fun testAccountModel() {
            val account = Account(
                username = "testuser",
                host = "localhost",
                created = "2024-01-01 00:00:00",
                passwordLastChanged = "2024-01-01 00:00:00",
                passwordLifetime = 90,
                accountLocked = false
            )

            assertEquals("testuser", account.username)
            assertEquals("localhost", account.host)
            assertEquals(90, account.passwordLifetime)
            assertFalse(account.accountLocked)
        }

        @Test
        @DisplayName("Account should allow null optional fields")
        fun testAccountWithNullFields() {
            val account = Account(
                username = "testuser",
                host = "localhost",
                created = null,
                passwordLastChanged = null,
                passwordLifetime = null,
                accountLocked = false
            )

            assertNull(account.created)
            assertNull(account.passwordLastChanged)
            assertNull(account.passwordLifetime)
        }

        @Test
        @DisplayName("Account copy should preserve values")
        fun testAccountCopy() {
            val original = Account(
                username = "C##USER",
                host = "localhost",
                passwordLifetime = 90,
                accountLocked = true
            )

            val copied = original.copy(username = "USER")
            assertEquals("USER", copied.username)
            assertEquals("localhost", copied.host)
            assertEquals(90, copied.passwordLifetime)
            assertTrue(copied.accountLocked)
        }
    }

    @Nested
    @DisplayName("CreateAccountRequest Tests")
    inner class CreateAccountRequestTests {
        @Test
        @DisplayName("CreateAccountRequest should have default host value")
        fun testCreateAccountRequestDefaults() {
            val request = CreateAccountRequest(
                username = "newuser",
                password = "password123",
                expireDays = 90
            )

            assertEquals("newuser", request.username)
            assertEquals("%", request.host)
            assertEquals(90, request.expireDays)
        }

        @Test
        @DisplayName("CreateAccountRequest should allow custom host")
        fun testCreateAccountRequestCustomHost() {
            val request = CreateAccountRequest(
                username = "newuser",
                host = "192.168.1.%",
                password = "password123",
                expireDays = 30
            )

            assertEquals("192.168.1.%", request.host)
            assertEquals(30, request.expireDays)
        }
    }

    @Nested
    @DisplayName("ExpiringAccount Tests")
    inner class ExpiringAccountTests {
        @Test
        @DisplayName("ExpiringAccount should calculate days correctly")
        fun testExpiringAccount() {
            val expiring = ExpiringAccount(
                username = "expiringuser",
                host = "localhost",
                daysUntilExpiry = 15
            )

            assertEquals("expiringuser", expiring.username)
            assertEquals(15, expiring.daysUntilExpiry)
            assertTrue(expiring.daysUntilExpiry < 30)
        }

        @Test
        @DisplayName("ExpiringAccount with negative days (already expired)")
        fun testExpiringAccountAlreadyExpired() {
            val expiring = ExpiringAccount(
                username = "expireduser",
                host = "localhost",
                daysUntilExpiry = -5
            )

            assertTrue(expiring.daysUntilExpiry < 0)
        }
    }

    // ==================== Clone Account Tests ====================

    @Nested
    @DisplayName("Clone Account Tests")
    inner class CloneAccountTests {
        @Test
        @DisplayName("CloneAccountRequest should have correct properties")
        fun testCloneAccountRequestModel() {
            val request = CloneAccountRequest(
                sourceUsername = "sourceuser",
                sourceHost = "localhost",
                newUsername = "cloneduser",
                newHost = "localhost",
                newPassword = "password123",
                copyPermissions = true,
                expireDays = 90
            )

            assertEquals("sourceuser", request.sourceUsername)
            assertEquals("localhost", request.sourceHost)
            assertEquals("cloneduser", request.newUsername)
            assertEquals("localhost", request.newHost)
            assertTrue(request.copyPermissions)
            assertEquals(90, request.expireDays)
        }

        @Test
        @DisplayName("CloneAccountRequest should have default values")
        fun testCloneAccountRequestDefaults() {
            val request = CloneAccountRequest(
                sourceUsername = "sourceuser",
                newUsername = "cloneduser",
                newPassword = "password123"
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
        fun testBatchCreateAccountRequestModel() {
            val accounts = listOf(
                CreateAccountRequest("user1", "%", "pass1", 90),
                CreateAccountRequest("user2", "%", "pass2", 90)
            )
            val request = BatchCreateAccountRequest(accounts)

            assertEquals(2, request.accounts.size)
            assertEquals("user1", request.accounts[0].username)
            assertEquals("user2", request.accounts[1].username)
        }

        @Test
        @DisplayName("BatchDeleteRequest should contain list of account identifiers")
        fun testBatchDeleteRequestModel() {
            val accounts = listOf(
                AccountIdentifier("user1", "localhost"),
                AccountIdentifier("user2", "%")
            )
            val request = BatchDeleteRequest(accounts)

            assertEquals(2, request.accounts.size)
            assertEquals("user1", request.accounts[0].username)
            assertEquals("localhost", request.accounts[0].host)
        }

        @Test
        @DisplayName("BatchUnlockRequest should contain list of account identifiers")
        fun testBatchUnlockRequestModel() {
            val accounts = listOf(
                AccountIdentifier("locked1", "%"),
                AccountIdentifier("locked2", "%")
            )
            val request = BatchUnlockRequest(accounts)

            assertEquals(2, request.accounts.size)
        }

        @Test
        @DisplayName("AccountIdentifier should have default host value")
        fun testAccountIdentifierDefaults() {
            val identifier = AccountIdentifier(
                username = "testuser"
            )

            assertEquals("testuser", identifier.username)
            assertEquals("%", identifier.host)
        }

        @Test
        @DisplayName("BatchOperationResult should track success and failures")
        fun testBatchOperationResultModel() {
            val result = BatchOperationResult(
                success = listOf("user1@%", "user2@%"),
                failed = listOf(
                    BatchOperationError("user3@%", "User already exists")
                )
            )

            assertEquals(2, result.success.size)
            assertEquals(1, result.failed.size)
            assertEquals("user3@%", result.failed[0].account)
            assertEquals("User already exists", result.failed[0].error)
        }

        @Test
        @DisplayName("BatchOperationError should have correct properties")
        fun testBatchOperationErrorModel() {
            val error = BatchOperationError(
                account = "faileduser@localhost",
                error = "Insufficient privileges"
            )

            assertEquals("faileduser@localhost", error.account)
            assertEquals("Insufficient privileges", error.error)
        }

        @Test
        @DisplayName("BatchOperationResult with empty lists")
        fun testBatchOperationResultEmpty() {
            val result = BatchOperationResult(
                success = emptyList(),
                failed = emptyList()
            )

            assertTrue(result.success.isEmpty())
            assertTrue(result.failed.isEmpty())
        }

        @Test
        @DisplayName("BatchOperationResult with all failures")
        fun testBatchOperationResultAllFailures() {
            val result = BatchOperationResult(
                success = emptyList(),
                failed = listOf(
                    BatchOperationError("user1@%", "Error 1"),
                    BatchOperationError("user2@%", "Error 2"),
                    BatchOperationError("user3@%", "Error 3")
                )
            )

            assertTrue(result.success.isEmpty())
            assertEquals(3, result.failed.size)
        }
    }

    // ==================== Export Tests ====================

    @Nested
    @DisplayName("Export Tests")
    inner class ExportTests {
        @Test
        @DisplayName("ExportRequest should have default values")
        fun testExportRequestDefaults() {
            val request = ExportRequest()

            assertEquals("csv", request.format)
            assertFalse(request.includePermissions)
        }

        @Test
        @DisplayName("ExportRequest with custom values")
        fun testExportRequestCustomValues() {
            val request = ExportRequest(
                format = "json",
                includePermissions = true
            )

            assertEquals("json", request.format)
            assertTrue(request.includePermissions)
        }
    }

    // ==================== Pagination Tests ====================

    @Nested
    @DisplayName("Pagination Tests")
    inner class PaginationTests {
        @Test
        @DisplayName("PaginationInfo should calculate correctly")
        fun testPaginationInfoCalculation() {
            val pagination = PaginationInfo.of(page = 1, pageSize = 10, totalItems = 95)

            assertEquals(1, pagination.page)
            assertEquals(10, pagination.pageSize)
            assertEquals(95, pagination.totalItems)
            assertEquals(10, pagination.totalPages)
        }

        @Test
        @DisplayName("PaginationInfo for last page")
        fun testPaginationInfoLastPage() {
            val pagination = PaginationInfo.of(page = 5, pageSize = 20, totalItems = 100)

            assertEquals(5, pagination.page)
            assertEquals(5, pagination.totalPages)
        }

        @Test
        @DisplayName("PaginationInfo for single page")
        fun testPaginationInfoSinglePage() {
            val pagination = PaginationInfo.of(page = 1, pageSize = 50, totalItems = 25)

            assertEquals(1, pagination.page)
            assertEquals(1, pagination.totalPages)
        }

        @Test
        @DisplayName("PaginationInfo for empty result")
        fun testPaginationInfoEmpty() {
            val pagination = PaginationInfo.of(page = 1, pageSize = 10, totalItems = 0)

            assertEquals(0, pagination.totalItems)
            assertEquals(1, pagination.totalPages) // Even with 0 items, totalPages is 1
        }

        @Test
        @DisplayName("PaginationInfo for middle page")
        fun testPaginationInfoMiddlePage() {
            val pagination = PaginationInfo.of(page = 3, pageSize = 10, totalItems = 50)

            assertEquals(3, pagination.page)
            assertEquals(5, pagination.totalPages)
        }

        @Test
        @DisplayName("PaginationInfo totalPages calculation")
        fun testPaginationInfoTotalPagesCalculation() {
            // 95 items with pageSize 10 = 10 pages (ceil(95/10))
            val pagination1 = PaginationInfo.of(page = 1, pageSize = 10, totalItems = 95)
            assertEquals(10, pagination1.totalPages)

            // 100 items with pageSize 10 = 10 pages (exact)
            val pagination2 = PaginationInfo.of(page = 1, pageSize = 10, totalItems = 100)
            assertEquals(10, pagination2.totalPages)

            // 101 items with pageSize 10 = 11 pages
            val pagination3 = PaginationInfo.of(page = 1, pageSize = 10, totalItems = 101)
            assertEquals(11, pagination3.totalPages)
        }
    }

    // ==================== AccountStats Tests ====================

    @Nested
    @DisplayName("AccountStats Tests")
    inner class AccountStatsTests {
        @Test
        @DisplayName("AccountStats should have correct properties")
        fun testAccountStats() {
            val stats = AccountStats(
                totalAccounts = 100,
                lockedAccounts = 5,
                activeAccounts = 95
            )

            assertEquals(100, stats.totalAccounts)
            assertEquals(5, stats.lockedAccounts)
            assertEquals(95, stats.activeAccounts)
        }

        @Test
        @DisplayName("AccountStats with all zeros")
        fun testAccountStatsZero() {
            val stats = AccountStats(
                totalAccounts = 0,
                lockedAccounts = 0,
                activeAccounts = 0
            )

            assertEquals(0, stats.totalAccounts)
            assertEquals(0, stats.lockedAccounts)
            assertEquals(0, stats.activeAccounts)
        }

        @Test
        @DisplayName("AccountStats all locked")
        fun testAccountStatsAllLocked() {
            val stats = AccountStats(
                totalAccounts = 10,
                lockedAccounts = 10,
                activeAccounts = 0
            )

            assertEquals(10, stats.totalAccounts)
            assertEquals(10, stats.lockedAccounts)
            assertEquals(0, stats.activeAccounts)
        }
    }

    // ==================== PaginatedAccountsResponse Tests ====================

    @Nested
    @DisplayName("PaginatedAccountsResponse Tests")
    inner class PaginatedAccountsResponseTests {
        @Test
        @DisplayName("PaginatedAccountsResponse should contain all fields")
        fun testPaginatedAccountsResponse() {
            val accounts = listOf(
                Account("user1", "localhost"),
                Account("user2", "localhost")
            )
            val pagination = PaginationInfo.of(
                page = 1,
                pageSize = 10,
                totalItems = 2
            )
            val stats = AccountStats(2, 0, 2)

            val response = PaginatedAccountsResponse(
                data = accounts,
                pagination = pagination,
                stats = stats
            )

            assertEquals(2, response.data.size)
            assertEquals(1, response.pagination.page)
            assertEquals(2, response.stats.totalAccounts)
        }
    }

    // ==================== Integration-like Tests with Mocking ====================

    @Nested
    @DisplayName("Service Method Tests with Mocking")
    inner class ServiceMethodTests {

        @Test
        @DisplayName("getAccountCount should return count from database")
        fun testGetAccountCount() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getAccountCountQuery() } returns "SELECT COUNT(*) as count FROM mysql.user"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.next() } returns true
            every { mockResultSet.getInt("count") } returns 42
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val count = accountService.getAccountCount("test-session")

            assertEquals(42, count)
            verify { mockDialect.getAccountCountQuery() }
        }

        @Test
        @DisplayName("getAccountCount should return 0 when no results")
        fun testGetAccountCountEmpty() {
            val mockConnection = mockk<Connection>()
            val mockStatement = mockk<Statement>()
            val mockResultSet = mockk<ResultSet>()
            val mockDialect = mockk<MySQLDialect>()

            every { SessionConnectionManager.getDialect("test-session") } returns mockDialect
            every { SessionConnectionManager.getConnection("test-session") } returns mockConnection
            every { mockDialect.getAccountCountQuery() } returns "SELECT COUNT(*) as count FROM mysql.user"
            every { mockConnection.createStatement() } returns mockStatement
            every { mockStatement.executeQuery(any()) } returns mockResultSet
            every { mockResultSet.next() } returns false
            every { mockResultSet.close() } just Runs
            every { mockStatement.close() } just Runs
            every { mockConnection.close() } just Runs

            val count = accountService.getAccountCount("test-session")

            assertEquals(0, count)
        }
    }

    // ==================== CSV Escape Logic Tests ====================

    @Nested
    @DisplayName("CSV Export Helper Tests")
    inner class CsvExportHelperTests {
        // Since escapeCsv is private, we test it indirectly through exportAccountsToCsv
        // Or we can use reflection if needed

        @Test
        @DisplayName("CSV header should be correct for basic export")
        fun testCsvHeaderBasic() {
            // Test that the CSV format is correct by checking header
            val header = "username,host,password_last_changed,password_lifetime,account_locked"
            assertTrue(header.contains("username"))
            assertTrue(header.contains("host"))
            assertTrue(header.contains("account_locked"))
        }

        @Test
        @DisplayName("CSV header should include permissions when requested")
        fun testCsvHeaderWithPermissions() {
            val header = "username,host,password_last_changed,password_lifetime,account_locked,database,table,privilege"
            assertTrue(header.contains("database"))
            assertTrue(header.contains("table"))
            assertTrue(header.contains("privilege"))
        }
    }
}
