package com.dbaccman.websocket

import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Event types for real-time updates.
 */
enum class EventType {
    // Session events
    SESSION_CREATED,
    SESSION_DESTROYED,
    SESSION_UPDATED,

    // Account events
    ACCOUNT_CREATED,
    ACCOUNT_DELETED,
    ACCOUNT_UPDATED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,

    // Permission events
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,

    // Database session events
    DB_SESSION_STARTED,
    DB_SESSION_KILLED,

    // System events
    CIRCUIT_BREAKER_OPENED,
    CIRCUIT_BREAKER_CLOSED,
    RATE_LIMIT_EXCEEDED,

    // Heartbeat
    HEARTBEAT
}

/**
 * Represents an event to be broadcast to clients.
 */
@Serializable
data class BroadcastEvent(
    val type: String,
    val data: Map<String, String>? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String? = null
)

/**
 * Manages WebSocket connections and broadcasts events to connected clients.
 */
object EventBroadcaster {
    private val logger = LoggerFactory.getLogger(EventBroadcaster::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    // Map of session ID to their WebSocket sessions
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    private val connectionCounter = AtomicLong(0)

    // Heartbeat interval
    private const val HEARTBEAT_INTERVAL_MS = 30_000L

    @Volatile
    private var heartbeatJob: Job? = null

    /**
     * Register a new WebSocket connection.
     */
    fun registerConnection(sessionId: String, session: WebSocketSession): String {
        val connectionId = "${sessionId}_${connectionCounter.incrementAndGet()}"
        connections[connectionId] = session
        logger.info("WebSocket connection registered: $connectionId (total: ${connections.size})")
        return connectionId
    }

    /**
     * Unregister a WebSocket connection.
     */
    fun unregisterConnection(connectionId: String) {
        connections.remove(connectionId)
        logger.info("WebSocket connection unregistered: $connectionId (total: ${connections.size})")
    }

    /**
     * Broadcast an event to all connected clients.
     */
    suspend fun broadcast(event: BroadcastEvent) {
        if (connections.isEmpty()) return

        val message = json.encodeToString(event)
        val deadConnections = mutableListOf<String>()

        connections.forEach { (connectionId, session) ->
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                logger.warn("Failed to send to $connectionId: ${e.message}")
                deadConnections.add(connectionId)
            }
        }

        // Cleanup dead connections
        deadConnections.forEach { unregisterConnection(it) }
    }

    /**
     * Broadcast an event to a specific session.
     */
    suspend fun broadcastToSession(sessionId: String, event: BroadcastEvent) {
        val targetConnections = connections.filterKeys { it.startsWith("${sessionId}_") }

        if (targetConnections.isEmpty()) return

        val message = json.encodeToString(event)
        val deadConnections = mutableListOf<String>()

        targetConnections.forEach { (connectionId, session) ->
            try {
                session.send(Frame.Text(message))
            } catch (e: Exception) {
                logger.warn("Failed to send to $connectionId: ${e.message}")
                deadConnections.add(connectionId)
            }
        }

        // Cleanup dead connections
        deadConnections.forEach { unregisterConnection(it) }
    }

    /**
     * Broadcast a typed event.
     */
    suspend fun broadcast(type: EventType, data: Map<String, String>? = null, sessionId: String? = null) {
        broadcast(BroadcastEvent(
            type = type.name,
            data = data,
            sessionId = sessionId
        ))
    }

    /**
     * Start the heartbeat job to keep connections alive.
     */
    fun startHeartbeat(scope: CoroutineScope) {
        if (heartbeatJob != null) return

        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    broadcast(EventType.HEARTBEAT, mapOf("connections" to connections.size.toString()))
                } catch (e: Exception) {
                    logger.error("Heartbeat error", e)
                }
            }
        }
        logger.info("WebSocket heartbeat started")
    }

    /**
     * Stop the heartbeat job.
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        logger.info("WebSocket heartbeat stopped")
    }

    /**
     * Get the number of active connections.
     */
    fun getConnectionCount(): Int = connections.size

    /**
     * Close all connections and shutdown.
     */
    suspend fun shutdown() {
        stopHeartbeat()

        connections.forEach { (connectionId, session) ->
            try {
                session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server shutting down"))
            } catch (e: Exception) {
                logger.warn("Error closing connection $connectionId", e)
            }
        }
        connections.clear()
        logger.info("EventBroadcaster shutdown complete")
    }
}
