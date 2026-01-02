package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class DashboardStats(
    val totalAccounts: Int,
    val activeSessions: Int,
    val expiringSoon: Int,
    val slowQueries: Int,
    val totalDatabases: Int,
    val totalTables: Int,
    val expiringAccounts: List<ExpiringAccount>,
    val longRunningSessions: List<SessionInfo>,
    val topDatabases: List<DatabaseInfo>
)
