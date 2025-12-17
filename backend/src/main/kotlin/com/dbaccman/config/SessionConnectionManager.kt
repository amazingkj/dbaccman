package com.dbaccman.config

import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.DatabaseType
import com.dbaccman.dialect.DialectFactory
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
     * Creates a new session with the given database credentials.
     * Validates the connection before returning the session ID.
     */
    fun createSession(
        host: String,
        port: Int,
        username: String,
        password: String,
        dbType: DatabaseType = DatabaseType.MYSQL,
        database: String? = null
    ): String {
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
            idleTimeout = 300000 // 5 minutes
            maxLifetime = 900000 // 15 minutes
            connectionTestQuery = dialect.getConnectionTestQuery()
            poolName = "session-$sessionId"
        }

        val dataSource = HikariDataSource(config)

        // Validate connection immediately
        try {
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(dialect.getConnectionTestQuery())
                }
            }
        } catch (e: Exception) {
            dataSource.close()
            throw e
        }

        sessionPools[sessionId] = SessionPool(
            dataSource = dataSource,
            host = host,
            port = port,
            username = username,
            dbType = dbType,
            dialect = dialect
        )
        logger.info("Created session $sessionId for $username@$host:$port (${dbType.displayName})")

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

/**
 * Utility function to execute a block with a session's connection and dialect.
 */
inline fun <T> useSessionConnectionWithDialect(sessionId: String, block: (Connection, DatabaseDialect) -> T): T {
    val dialect = SessionConnectionManager.getDialect(sessionId)
    return SessionConnectionManager.getConnection(sessionId).use { conn ->
        block(conn, dialect)
    }
}