package com.dbaccman.config

import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.DatabaseType
import com.dbaccman.dialect.DialectFactory
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.util.CircuitBreakerRegistry
import com.dbaccman.util.CircuitBreakerOpenException
import com.dbaccman.util.HikariMonitor
import com.dbaccman.util.PoolStats
import com.dbaccman.util.PoolSummary
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.concurrent.thread

data class SessionPool(
    val dataSource: HikariDataSource,
    val host: String,
    val port: Int,
    val username: String,
    val dbType: DatabaseType,
    val dialect: DatabaseDialect,
    val isContainerRoot: Boolean = false,  // Oracle CDB root flag
    var lastAccess: Long = System.currentTimeMillis()
)

object SessionConnectionManager {
    private val logger = LoggerFactory.getLogger(SessionConnectionManager::class.java)
    private val sessionPools = ConcurrentHashMap<String, SessionPool>()
    private val SESSION_TIMEOUT_MS = 35 * 60 * 1000L // 35 minutes
    private val CLEANUP_INTERVAL_MS = 60 * 1000L // 1 minute

    @Volatile
    private var cleanupThread: Thread? = null

    init {
        startCleanupThread()
    }

    private fun startCleanupThread() {
        cleanupThread = thread(isDaemon = true, name = "session-cleanup") {
            logger.info("Session cleanup thread started (timeout: ${SESSION_TIMEOUT_MS / 60000}min, interval: ${CLEANUP_INTERVAL_MS / 1000}s)")
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(CLEANUP_INTERVAL_MS)
                    cleanupExpiredSessions()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    logger.error("Error during session cleanup", e)
                }
            }
        }
    }

    /**
     * Creates a new session with the given database credentials.
     * Validates the connection before returning the session ID.
     * Uses Circuit Breaker pattern to prevent cascading failures.
     */
    fun createSession(
        host: String,
        port: Int,
        username: String,
        password: String,
        dbType: DatabaseType = DatabaseType.MYSQL,
        database: String? = null
    ): String {
        // Get circuit breaker for this database endpoint
        val circuitBreaker = CircuitBreakerRegistry.getDatabaseBreaker(host, port, dbType.name)

        return circuitBreaker.execute {
            val sessionId = UUID.randomUUID().toString()
            val dialect = DialectFactory.getDialect(dbType)
            val jdbcUrl = dialect.getJdbcUrl(host, port, database)

            val config = HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = username
                this.password = password
                driverClassName = dialect.getDriverClassName()
                maximumPoolSize = 5
                minimumIdle = 1
                isAutoCommit = true
                connectionTimeout = 10000 // 10 seconds
                idleTimeout = 10 * 60 * 1000 // 10 minutes
                maxLifetime = 30 * 60 * 1000 // 30 minutes
                connectionTestQuery = dialect.getConnectionTestQuery()
                poolName = "session-$sessionId"
            }

            var dataSource: HikariDataSource? = null
            var isContainerRoot = false

            try {
                dataSource = HikariDataSource(config)

                // Validate connection and detect Oracle CDB root
                dataSource.connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute(dialect.getConnectionTestQuery())
                    }

                    // Check if Oracle CDB root
                    if (dialect is OracleDialect) {
                        try {
                            conn.createStatement().use { stmt ->
                                stmt.executeQuery(dialect.getContainerNameSql()).use { rs ->
                                    if (rs.next()) {
                                        val containerName = rs.getString(1)
                                        logger.info("Oracle container name detected: '$containerName'")
                                        isContainerRoot = containerName == "CDB\$ROOT"
                                        if (isContainerRoot) {
                                            logger.info("Connected to Oracle CDB root - C## prefix will be used for user management")
                                        } else {
                                            logger.info("Connected to Oracle PDB '$containerName' - local users will be created")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore - might not have access to this info
                            logger.warn("Could not determine Oracle container type: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                // Ensure dataSource is closed on any error
                try {
                    dataSource?.close()
                } catch (closeEx: Exception) {
                    logger.error("Error closing dataSource during cleanup", closeEx)
                }
                throw e
            }

            // At this point, dataSource is guaranteed non-null and validated
            sessionPools[sessionId] = SessionPool(
                dataSource = dataSource,
                host = host,
                port = port,
                username = username,
                dbType = dbType,
                dialect = dialect,
                isContainerRoot = isContainerRoot
            )
            logger.info("Created session $sessionId for $username@$host:$port (${dbType.displayName})")

            sessionId
        }
    }

    /**
     * Gets a connection from the session's pool.
     */
    fun getConnection(sessionId: String): Connection {
        val pool = sessionPools[sessionId]
            ?: throw IllegalStateException("Session not found or expired: $sessionId")
        pool.lastAccess = System.currentTimeMillis()
        return pool.dataSource.connection
    }

    /**
     * Gets the dialect for the session.
     */
    fun getDialect(sessionId: String): DatabaseDialect {
        val pool = sessionPools[sessionId]
            ?: throw IllegalStateException("Session not found or expired: $sessionId")
        return pool.dialect
    }

    /**
     * Gets the database type for the session.
     */
    fun getDatabaseType(sessionId: String): DatabaseType {
        val pool = sessionPools[sessionId]
            ?: throw IllegalStateException("Session not found or expired: $sessionId")
        return pool.dbType
    }

    /**
     * Checks if session is connected to Oracle CDB root.
     */
    fun isContainerRoot(sessionId: String): Boolean {
        val pool = sessionPools[sessionId]
            ?: throw IllegalStateException("Session not found or expired: $sessionId")
        return pool.isContainerRoot
    }

    /**
     * Gets session info without updating last access time.
     */
    fun getSessionInfo(sessionId: String): SessionPool? {
        return sessionPools[sessionId]
    }

    /**
     * Checks if a session exists.
     */
    fun hasSession(sessionId: String): Boolean {
        return sessionPools.containsKey(sessionId)
    }

    /**
     * Closes a session and releases its connection pool.
     */
    fun closeSession(sessionId: String) {
        sessionPools.remove(sessionId)?.let { pool ->
            try {
                pool.dataSource.close()
                logger.info("Closed session $sessionId for ${pool.username}@${pool.host}:${pool.port} (${pool.dbType.displayName})")
            } catch (e: Exception) {
                logger.error("Error closing session $sessionId", e)
            }
        }
    }

    /**
     * Cleanup expired sessions (idle for more than SESSION_TIMEOUT_MS).
     */
    private fun cleanupExpiredSessions() {
        val expireThreshold = System.currentTimeMillis() - SESSION_TIMEOUT_MS
        val expiredSessions = sessionPools.entries
            .filter { it.value.lastAccess < expireThreshold }
            .map { it.key }

        expiredSessions.forEach { sessionId ->
            logger.info("Cleaning up expired session: $sessionId")
            closeSession(sessionId)
        }

        if (expiredSessions.isNotEmpty()) {
            logger.info("Cleaned up ${expiredSessions.size} expired sessions")
        }
    }

    /**
     * Returns the number of active sessions.
     */
    fun getActiveSessionCount(): Int = sessionPools.size

    /**
     * Get connection pool statistics for all sessions.
     */
    fun getPoolSummary(): PoolSummary {
        val poolStats = sessionPools.values.mapNotNull { pool ->
            HikariMonitor.getPoolStats(pool.dataSource)
        }

        return PoolSummary(
            totalPools = poolStats.size,
            totalActiveConnections = poolStats.sumOf { it.activeConnections },
            totalIdleConnections = poolStats.sumOf { it.idleConnections },
            totalConnections = poolStats.sumOf { it.totalConnections },
            pools = poolStats
        )
    }

    /**
     * Get connection pool statistics for a specific session.
     */
    fun getPoolStats(sessionId: String): PoolStats? {
        val pool = sessionPools[sessionId] ?: return null
        return HikariMonitor.getPoolStats(pool.dataSource)
    }

    /**
     * Log all pool statistics (for monitoring/debugging).
     */
    fun logAllPoolStats() {
        sessionPools.values.forEach { pool ->
            HikariMonitor.getPoolStats(pool.dataSource)?.let { stats ->
                HikariMonitor.logPoolStats(stats)
            }
        }
    }

    /**
     * Shuts down all sessions and the cleanup thread.
     */
    fun shutdown() {
        logger.info("Shutting down SessionConnectionManager...")
        cleanupThread?.interrupt()
        cleanupThread = null

        sessionPools.keys.toList().forEach { sessionId ->
            closeSession(sessionId)
        }

        logger.info("SessionConnectionManager shutdown complete")
    }
}

/**
 * Utility function to execute a block with a session's connection.
 */
inline fun <T> useSessionConnection(sessionId: String, block: (Connection) -> T): T {
    return SessionConnectionManager.getConnection(sessionId).use { conn ->
        block(conn)
    }
}

/**
 * Utility function to execute a block with a session's connection and dialect.
 */
inline fun <T> useSessionConnectionWithDialect(sessionId: String, block: (Connection, DatabaseDialect) -> T): T {
    val dialect = SessionConnectionManager.getDialect(sessionId)
    return SessionConnectionManager.getConnection(sessionId).use { conn ->
        block(conn, dialect)
    }
}

/**
 * Utility function to execute Oracle DDL with _ORACLE_SCRIPT enabled/disabled.
 * Handles CDB root detection and proper cleanup on error.
 */
inline fun <T> useOracleScriptContext(
    sessionId: String,
    crossinline block: (Connection, DatabaseDialect) -> T
): T {
    val isContainerRoot = SessionConnectionManager.isContainerRoot(sessionId)
    val dialect = SessionConnectionManager.getDialect(sessionId)

    return SessionConnectionManager.getConnection(sessionId).use { conn ->
        if (isContainerRoot && dialect is OracleDialect) {
            conn.createStatement().use { stmt ->
                stmt.execute(dialect.getEnableLocalUserSql())
            }
        }
        try {
            block(conn, dialect)
        } finally {
            if (isContainerRoot && dialect is OracleDialect) {
                try {
                    conn.createStatement().use { stmt ->
                        stmt.execute(dialect.getDisableLocalUserSql())
                    }
                } catch (ignored: Exception) {
                    // Cleanup should not throw
                }
            }
        }
    }
}