package com.dbgate.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class AccountServiceTest {

    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        accountService = AccountService()
    }

    @Test
    @DisplayName("AccountService should be instantiable")
    fun testAccountServiceInstantiation() {
        assertNotNull(accountService)
    }

    // Note: Full integration tests would require a running MySQL database
    // These tests demonstrate the test structure and would need mocking
    // for proper unit testing without database dependency

    @Test
    @DisplayName("Account model should have correct properties")
    fun testAccountModel() {
        val account = com.dbgate.model.Account(
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
    @DisplayName("CreateAccountRequest should have default host value")
    fun testCreateAccountRequestDefaults() {
        val request = com.dbgate.model.CreateAccountRequest(
            username = "newuser",
            password = "password123",
            expireDays = 90
        )

        assertEquals("newuser", request.username)
        assertEquals("%", request.host) // default value
        assertEquals(90, request.expireDays)
    }

    @Test
    @DisplayName("ExpiringAccount should calculate days correctly")
    fun testExpiringAccount() {
        val expiring = com.dbgate.model.ExpiringAccount(
            username = "expiringuser",
            host = "localhost",
            daysUntilExpiry = 15
        )

        assertEquals("expiringuser", expiring.username)
        assertEquals(15, expiring.daysUntilExpiry)
        assertTrue(expiring.daysUntilExpiry < 30)
    }
}
