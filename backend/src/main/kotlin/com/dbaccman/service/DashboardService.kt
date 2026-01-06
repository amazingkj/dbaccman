package com.dbaccman.service

import com.dbaccman.model.*
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min

class DashboardService {

    private val accountService = AccountService()
    private val sessionService = SessionService()
    private val tableService = TableService()
    private val tablespaceService = TablespaceService()

    fun getDashboardStats(sessionId: String): DashboardStats = runBlocking {
        // 병렬 실행: 독립적인 쿼리들을 동시에 실행
        val accountStatsDeferred = async(Dispatchers.IO) {
            accountService.getPaginatedAccounts(sessionId, 1, 1)
        }
        val expiringAccountsDeferred = async(Dispatchers.IO) {
            accountService.getExpiringAccounts(sessionId, 30)
        }
        val sessionStatsDeferred = async(Dispatchers.IO) {
            sessionService.getSessionStats(sessionId)
        }
        val longRunningSessionsDeferred = async(Dispatchers.IO) {
            sessionService.getLongRunningQueries(sessionId, 60)
        }
        val databasesDeferred = async(Dispatchers.IO) {
            tableService.getDatabases(sessionId)
        }
        val tablespacesDeferred = async(Dispatchers.IO) {
            try {
                tablespaceService.getTablespaces(sessionId)
            } catch (e: Exception) {
                emptyList()
            }
        }

        // 모든 결과 대기
        val accountStats = accountStatsDeferred.await()
        val expiringAccounts = expiringAccountsDeferred.await()
        val sessionStats = sessionStatsDeferred.await()
        val longRunningSessions = longRunningSessionsDeferred.await()
            .sortedByDescending { it.time }
            .take(5)
        val databases = databasesDeferred.await()
        val tablespaces = tablespacesDeferred.await()

        // 계산
        val totalAccounts = accountStats.stats.totalAccounts
        val lockedAccounts = accountStats.stats.lockedAccounts
        val totalTables = databases.sumOf { it.tableCount }

        val tablespaceUsages = tablespaces.map { ts ->
            if (ts.fileSize > 0) ((ts.allocatedSize.toDouble() / ts.fileSize) * 100).toInt() else 0
        }
        val maxTablespaceUsage = tablespaceUsages.maxOrNull() ?: 0
        val criticalTablespaces = tablespaceUsages.count { it >= 90 }

        // Health Score 계산
        val healthScore = calculateHealthScore(
            expiringSoon = expiringAccounts.size,
            lockedAccounts = lockedAccounts,
            activeSessions = sessionStats.activeSessions,
            totalSessions = sessionStats.totalSessions,
            slowQueries = sessionStats.longRunningSessions,
            criticalTablespaces = criticalTablespaces,
            maxTablespaceUsage = maxTablespaceUsage
        )

        DashboardStats(
            totalAccounts = totalAccounts,
            activeSessions = sessionStats.activeSessions,
            expiringSoon = expiringAccounts.size,
            slowQueries = sessionStats.longRunningSessions,
            lockedAccounts = lockedAccounts,
            totalDatabases = databases.size,
            totalTables = totalTables,
            tablespaceUsage = maxTablespaceUsage,
            criticalTablespaces = criticalTablespaces,
            healthScore = healthScore,
            expiringAccounts = expiringAccounts.take(5),
            longRunningSessions = longRunningSessions,
            topDatabases = databases.take(5)
        )
    }

    /**
     * Health Score 계산 로직
     *
     * 총 100점 = 계정(40점) + 세션(30점) + 스토리지(30점)
     *
     * 계정 (40점):
     *   - 만료 예정 계정: 개당 -4점 (최대 -20점)
     *   - 잠긴 계정: 개당 -5점 (최대 -20점)
     *
     * 세션 (30점):
     *   - 세션 사용률 80% 이상: 비례 감점 (최대 -15점)
     *   - 장기 실행 쿼리: 개당 -3점 (최대 -15점)
     *
     * 스토리지 (30점):
     *   - 90% 이상 Tablespace: 개당 -10점 (최대 -30점)
     */
    private fun calculateHealthScore(
        expiringSoon: Int,
        lockedAccounts: Int,
        activeSessions: Int,
        totalSessions: Int,
        slowQueries: Int,
        criticalTablespaces: Int,
        maxTablespaceUsage: Int
    ): HealthScore {
        val issues = mutableListOf<String>()

        // === 계정 점수 (40점 만점) ===
        val expiringPenalty = min(expiringSoon * 4, 20)
        val lockedPenalty = min(lockedAccounts * 5, 20)
        val accountScore = max(0, 40 - expiringPenalty - lockedPenalty)

        if (expiringSoon > 0) {
            issues.add("${expiringSoon}개 계정이 30일 내 만료 예정")
        }
        if (lockedAccounts > 0) {
            issues.add("${lockedAccounts}개 계정이 잠김 상태")
        }

        // === 세션 점수 (30점 만점) ===
        val sessionUsagePercent = if (totalSessions > 0) {
            (activeSessions.toDouble() / totalSessions * 100).toInt()
        } else 0

        val sessionUsagePenalty = if (sessionUsagePercent >= 80) {
            min(((sessionUsagePercent - 80) * 0.75).toInt(), 15)
        } else 0

        val slowQueryPenalty = min(slowQueries * 3, 15)
        val sessionScore = max(0, 30 - sessionUsagePenalty - slowQueryPenalty)

        if (sessionUsagePercent >= 80) {
            issues.add("세션 사용률 ${sessionUsagePercent}% (주의 필요)")
        }
        if (slowQueries > 0) {
            issues.add("${slowQueries}개 장기 실행 쿼리 감지")
        }

        // === 스토리지 점수 (30점 만점) ===
        val storagePenalty = min(criticalTablespaces * 10, 30)
        val storageScore = max(0, 30 - storagePenalty)

        if (criticalTablespaces > 0) {
            issues.add("${criticalTablespaces}개 Tablespace가 90% 이상 사용 중")
        }
        if (maxTablespaceUsage >= 95) {
            issues.add("Tablespace 사용률 ${maxTablespaceUsage}% (긴급)")
        }

        // === 종합 점수 ===
        val total = accountScore + sessionScore + storageScore
        val status = when {
            total >= 80 -> "healthy"
            total >= 60 -> "warning"
            else -> "critical"
        }

        return HealthScore(
            total = total,
            accountScore = accountScore,
            sessionScore = sessionScore,
            storageScore = storageScore,
            status = status,
            issues = issues
        )
    }
}
