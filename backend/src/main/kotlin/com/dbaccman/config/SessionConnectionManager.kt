package com.dbaccman.config

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
    var lastAccess: Long = System.currentTimeMillis()
)

object SessionConnectionManager {
    private val logger = LoggerFactory.getLogger(SessionConnectionManager::class.java)
    private val sessionPools = ConcurrentHashMap<String, SessionPool>()
    private val SESSION_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
    private val CLEANUP_INTERVAL_MS = 60 * 1000L // 1 minute

    @Volatile
    private var cleanupThread: Thread? = null

    init {
        startCleanupThread()
    }

    private fun startCleanupThread() {
        cleanupThread = thread(isDaemon = true, name = "session-cleanup") {
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
     * Creates a new session with the given MySQL credentials.
     * Validates the connection before returning the session ID.
     */
    fun createSession(host: String, port: Int, username: String, password: String): String {
        val sessionId = UUID.randomUUID().toString()
        val jdbcUrl = "jdbc:mysql://$host:$port?allowPublicKeyRetrieval=true&useSSL=false"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            driverClassName = "com.mysql.cj.jdbc.Driver"
            maximumPoolSize = 5
            minimumIdle = 1
            isAutoCommit = true
            connectionTimeout = 10000 // 10 seconds
            idleTimeout = 300000 // 5 minutes
            maxLifetime = 900000 // 15 minutes
            connectionTestQuery = "SELECT 1"
            poolName = "session-$sessionId"
        }

        val dataSource = HikariDataSource(config)

        // Validate connection immediately
        try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("SELECT 1")
                }
            }
        } catch (e: Exception) {
            dataSource.close()
            throw e
        }

        sessionPools[sessionId] = SessionPool(dataSource, host, port, username)
        logger.info("Created session $sessionId for $username@$host:$port")

        return sessionId
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
                logger.info("Closed session $sessionId for ${pool.username}@${pool.host}:${pool.port}")
            } catch (e: Exception) {
                logger.error("Error closing session $sessionId", e)
            }
        }
    }

    /**
     * Cleanup expired sessions (idle for more than 30 minutes).
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
