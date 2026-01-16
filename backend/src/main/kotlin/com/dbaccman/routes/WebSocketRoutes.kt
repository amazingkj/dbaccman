package com.dbaccman.routes

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.websocket.BroadcastEvent
import com.dbaccman.websocket.EventBroadcaster
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WebSocketRoutes")
private val json = Json { ignoreUnknownKeys = true }

/**
 * Configure WebSocket routes for real-time updates.
 */
fun Route.webSocketRoutes() {
    /**
     * WebSocket endpoint for real-time event streaming.
     * Clients connect with their session ID to receive updates.
     *
     * URL: /api/ws/{sessionId}
     */
    webSocket("/ws/{sessionId}") {
        val sessionId = call.parameters["sessionId"] ?: run {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Session ID required"))
            return@webSocket
        }

        // Verify the session exists
        if (!SessionConnectionManager.hasSession(sessionId)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid session"))
            return@webSocket
        }

        // Register the connection
        val connectionId = EventBroadcaster.registerConnection(sessionId, this)
        logger.info("WebSocket connected: $connectionId")

        // Send welcome message
        send(Frame.Text(json.encodeToString(
            BroadcastEvent.serializer(),
            BroadcastEvent(
                type = "CONNECTED",
                data = mapOf(
                    "connectionId" to connectionId,
                    "message" to "Connected to event stream"
                )
            )
        )))

        try {
            // Handle incoming messages (keep connection alive)
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        // Handle ping messages
                        if (text == "ping") {
                            send(Frame.Text(json.encodeToString(
                                BroadcastEvent.serializer(),
                                BroadcastEvent(type = "PONG")
                            )))
                        }
                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {}
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.info("WebSocket closed normally: $connectionId")
        } catch (e: Exception) {
            logger.error("WebSocket error for $connectionId", e)
        } finally {
            EventBroadcaster.unregisterConnection(connectionId)
            logger.info("WebSocket disconnected: $connectionId")
        }
    }

    /**
     * Get WebSocket connection statistics.
     */
    get("/ws/stats") {
        call.respond(mapOf(
            "activeConnections" to EventBroadcaster.getConnectionCount()
        ))
    }
}
