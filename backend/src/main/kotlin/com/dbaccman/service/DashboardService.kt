package com.dbaccman.service

import com.dbaccman.model.DashboardStats

class DashboardService {

    private val accountService = AccountService()
    private val sessionService = SessionService()
    private val tableService = TableService()

    fun getDashboardStats(sessionId: String): DashboardStats {
        // Get accounts count
        val accounts = accountService.getAllAccounts(sessionId)
        val totalAccounts = accounts.size

        // Get expiring accounts
        val expiringAccounts = accountService.getExpiringAccounts(sessionId, 30)

        // Get sessions
        val sessions = sessionService.getActiveSessions(sessionId)
        // Idle session states: MySQL uses 'Sleep', Oracle uses 'INACTIVE', PostgreSQL uses 'idle'
        val activeSessions = sessions.filter { it.command != "Sleep" && it.command != "INACTIVE" && it.command != "idle" }
        val slowQueries = activeSessions.filter { it.time > 60 }
        val longRunningSessions = slowQueries.sortedByDescending { it.time }.take(5)

        // Get database info
        val databases = tableService.getDatabases(sessionId)
        val totalTables = databases.sumOf { it.tableCount }

        return DashboardStats(
            totalAccounts = totalAccounts,
            activeSessions = activeSessions.size,
            expiringSoon = expiringAccounts.size,
            slowQueries = slowQueries.size,
            totalDatabases = databases.size,
            totalTables = totalTables,
            expiringAccounts = expiringAccounts.take(5),
            longRunningSessions = longRunningSessions,
            topDatabases = databases.take(5)
        )
    }
}
