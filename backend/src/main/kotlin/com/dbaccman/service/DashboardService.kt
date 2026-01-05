package com.dbaccman.service

import com.dbaccman.model.DashboardStats

class DashboardService {

    private val accountService = AccountService()
    private val sessionService = SessionService()
    private val tableService = TableService()

    fun getDashboardStats(sessionId: String): DashboardStats {
        // Optimized: Use COUNT(*) query instead of fetching all accounts
        val totalAccounts = accountService.getAccountCount(sessionId)

        // Get expiring accounts (still needed for actual data display)
        val expiringAccounts = accountService.getExpiringAccounts(sessionId, 30)

        // Optimized: Use getSessionStats() for counts, getLongRunningQueries() for data
        val sessionStats = sessionService.getSessionStats(sessionId)
        val longRunningSessions = sessionService.getLongRunningQueries(sessionId, 60)
            .sortedByDescending { it.time }
            .take(5)

        // Get database info (still needed for actual data display)
        val databases = tableService.getDatabases(sessionId)
        val totalTables = databases.sumOf { it.tableCount }

        return DashboardStats(
            totalAccounts = totalAccounts,
            activeSessions = sessionStats.activeSessions,
            expiringSoon = expiringAccounts.size,
            slowQueries = sessionStats.longRunningSessions,
            totalDatabases = databases.size,
            totalTables = totalTables,
            expiringAccounts = expiringAccounts.take(5),
            longRunningSessions = longRunningSessions,
            topDatabases = databases.take(5)
        )
    }
}
