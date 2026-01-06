package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthScore(
    val total: Int,                    // 종합 점수 (0-100)
    val accountScore: Int,             // 계정 점수 (0-40)
    val sessionScore: Int,             // 세션 점수 (0-30)
    val storageScore: Int,             // 스토리지 점수 (0-30)
    val status: String,                // "healthy", "warning", "critical"
    val issues: List<String>           // 문제점 목록
)

@Serializable
data class DashboardStats(
    val totalAccounts: Int,
    val activeSessions: Int,
    val expiringSoon: Int,
    val slowQueries: Int,
    val lockedAccounts: Int,           // 추가: 잠긴 계정 수
    val totalDatabases: Int,
    val totalTables: Int,
    val tablespaceUsage: Int,          // 추가: 최대 Tablespace 사용률 (%)
    val criticalTablespaces: Int,      // 추가: 90% 이상 Tablespace 수
    val healthScore: HealthScore,      // 추가: Health 점수
    val expiringAccounts: List<ExpiringAccount>,
    val longRunningSessions: List<SessionInfo>,
    val topDatabases: List<DatabaseInfo>
)
