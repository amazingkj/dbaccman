package com.dbaccman.util

import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariPoolMXBean
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * HikariCP connection pool statistics.
 */
@Serializable
data class PoolStats(
    val poolName: String,
    val activeConnections: Int,
    val idleConnections: Int,
    val totalConnections: Int,
    val threadsAwaitingConnection: Int,
    val maxPoolSize: Int,
    val minIdle: Int,
    val connectionTimeout: Long,
    val idleTimeout: Long,
    val maxLifetime: Long
)

/**
 * Summary of all connection pools.
 */
@Serializable
data class PoolSummary(
    val totalPools: Int,
    val totalActiveConnections: Int,
    val totalIdleConnections: Int,
    val totalConnections: Int,
    val pools: List<PoolStats>
)

/**
 * Monitor for HikariCP connection pools.
 * Provides metrics and health status for all connection pools.
 */
object HikariMonitor {
    private val logger = LoggerFactory.getLogger(HikariMonitor::class.java)

    /**
     * Get statistics for a single HikariDataSource.
     */
    fun getPoolStats(dataSource: HikariDataSource): PoolStats? {
        return try {
            val poolMXBean: HikariPoolMXBean? = dataSource.hikariPoolMXBean

            if (poolMXBean == null) {
                // Pool not yet initialized
                return PoolStats(
                    poolName = dataSource.poolName ?: "unknown",
                    activeConnections = 0,
                    idleConnections = 0,
                    totalConnections = 0,
                    threadsAwaitingConnection = 0,
                    maxPoolSize = dataSource.maximumPoolSize,
                    minIdle = dataSource.minimumIdle,
                    connectionTimeout = dataSource.connectionTimeout,
                    idleTimeout = dataSource.idleTimeout,
                    maxLifetime = dataSource.maxLifetime
                )
            }

            PoolStats(
                poolName = dataSource.poolName ?: "unknown",
                activeConnections = poolMXBean.activeConnections,
                idleConnections = poolMXBean.idleConnections,
                totalConnections = poolMXBean.totalConnections,
                threadsAwaitingConnection = poolMXBean.threadsAwaitingConnection,
                maxPoolSize = dataSource.maximumPoolSize,
                minIdle = dataSource.minimumIdle,
                connectionTimeout = dataSource.connectionTimeout,
                idleTimeout = dataSource.idleTimeout,
                maxLifetime = dataSource.maxLifetime
            )
        } catch (e: Exception) {
            logger.warn("Error getting pool stats for ${dataSource.poolName}", e)
            null
        }
    }

    /**
     * Check if a pool is healthy.
     * Pool is considered unhealthy if:
     * - All connections are in use
     * - Threads are waiting for connections
     */
    fun isPoolHealthy(stats: PoolStats): Boolean {
        // Pool is unhealthy if all connections are active and threads are waiting
        if (stats.activeConnections >= stats.maxPoolSize && stats.threadsAwaitingConnection > 0) {
            return false
        }

        // Pool is also unhealthy if total connections exceed max
        if (stats.totalConnections > stats.maxPoolSize) {
            return false
        }

        return true
    }

    /**
     * Get health status string for a pool.
     */
    fun getPoolHealthStatus(stats: PoolStats): String {
        return when {
            stats.threadsAwaitingConnection > 0 -> "DEGRADED"
            stats.activeConnections >= stats.maxPoolSize * 0.9 -> "WARNING"
            else -> "HEALTHY"
        }
    }

    /**
     * Calculate pool utilization percentage.
     */
    fun getPoolUtilization(stats: PoolStats): Double {
        if (stats.maxPoolSize == 0) return 0.0
        return (stats.activeConnections.toDouble() / stats.maxPoolSize) * 100
    }

    /**
     * Log pool statistics for monitoring.
     */
    fun logPoolStats(stats: PoolStats) {
        val utilization = getPoolUtilization(stats)
        val status = getPoolHealthStatus(stats)

        logger.info(
            "Pool[{}] status={} active={}/{} idle={} waiting={} utilization={:.1f}%",
            stats.poolName,
            status,
            stats.activeConnections,
            stats.maxPoolSize,
            stats.idleConnections,
            stats.threadsAwaitingConnection,
            utilization
        )
    }
}
