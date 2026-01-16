package com.dbaccman.routes

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.util.CircuitBreakerRegistry
import com.dbaccman.util.HikariMonitor
import com.dbaccman.websocket.EventBroadcaster
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class SystemHealthResponse(
    val status: String,
    val components: Map<String, ComponentHealth>
)

@Serializable
data class ComponentHealth(
    val status: String,
    val details: Map<String, String>? = null
)

@Serializable
data class CircuitBreakerInfo(
    val name: String,
    val state: String,
    val failureCount: Int,
    val successCount: Int
)

@Serializable
data class MonitoringResponse(
    val sessions: SessionsInfo,
    val connectionPools: ConnectionPoolsInfo,
    val circuitBreakers: List<CircuitBreakerInfo>,
    val websocket: WebSocketInfo
)

@Serializable
data class SessionsInfo(
    val activeCount: Int
)

@Serializable
data class ConnectionPoolsInfo(
    val totalPools: Int,
    val totalActiveConnections: Int,
    val totalIdleConnections: Int,
    val totalConnections: Int
)

@Serializable
data class WebSocketInfo(
    val activeConnections: Int
)

/**
 * Monitoring routes for system health and metrics.
 */
fun Route.monitoringRoutes() {
    route("/monitoring") {
        /**
         * Get overall system health status.
         */
        get("/health") {
            val poolSummary = SessionConnectionManager.getPoolSummary()
            val circuitBreakers = CircuitBreakerRegistry.getAllStatuses()

            // Check for any open circuit breakers
            val openCircuitBreakers = circuitBreakers.filter { it.state.name == "OPEN" }

            // Check for connection pool issues
            val poolIssues = poolSummary.pools.any { pool ->
                !HikariMonitor.isPoolHealthy(pool)
            }

            val overallStatus = when {
                openCircuitBreakers.isNotEmpty() -> "DEGRADED"
                poolIssues -> "WARNING"
                else -> "HEALTHY"
            }

            val components = mutableMapOf<String, ComponentHealth>()

            // Session component
            components["sessions"] = ComponentHealth(
                status = "HEALTHY",
                details = mapOf(
                    "activeSessions" to SessionConnectionManager.getActiveSessionCount().toString()
                )
            )

            // Connection pools component
            val poolStatus = if (poolIssues) "WARNING" else "HEALTHY"
            components["connectionPools"] = ComponentHealth(
                status = poolStatus,
                details = mapOf(
                    "totalPools" to poolSummary.totalPools.toString(),
                    "activeConnections" to poolSummary.totalActiveConnections.toString(),
                    "idleConnections" to poolSummary.totalIdleConnections.toString()
                )
            )

            // Circuit breakers component
            val cbStatus = if (openCircuitBreakers.isNotEmpty()) "DEGRADED" else "HEALTHY"
            components["circuitBreakers"] = ComponentHealth(
                status = cbStatus,
                details = mapOf(
                    "total" to circuitBreakers.size.toString(),
                    "open" to openCircuitBreakers.size.toString()
                )
            )

            // WebSocket component
            components["websocket"] = ComponentHealth(
                status = "HEALTHY",
                details = mapOf(
                    "activeConnections" to EventBroadcaster.getConnectionCount().toString()
                )
            )

            call.respond(SystemHealthResponse(
                status = overallStatus,
                components = components
            ))
        }

        /**
         * Get detailed monitoring information.
         */
        get("/details") {
            val poolSummary = SessionConnectionManager.getPoolSummary()
            val circuitBreakers = CircuitBreakerRegistry.getAllStatuses()

            call.respond(MonitoringResponse(
                sessions = SessionsInfo(
                    activeCount = SessionConnectionManager.getActiveSessionCount()
                ),
                connectionPools = ConnectionPoolsInfo(
                    totalPools = poolSummary.totalPools,
                    totalActiveConnections = poolSummary.totalActiveConnections,
                    totalIdleConnections = poolSummary.totalIdleConnections,
                    totalConnections = poolSummary.totalConnections
                ),
                circuitBreakers = circuitBreakers.map { status ->
                    CircuitBreakerInfo(
                        name = status.name,
                        state = status.state.name,
                        failureCount = status.failureCount,
                        successCount = status.successCount
                    )
                },
                websocket = WebSocketInfo(
                    activeConnections = EventBroadcaster.getConnectionCount()
                )
            ))
        }

        /**
         * Get detailed connection pool statistics.
         */
        get("/pools") {
            val poolSummary = SessionConnectionManager.getPoolSummary()
            call.respond(poolSummary)
        }

        /**
         * Get circuit breaker statuses.
         */
        get("/circuit-breakers") {
            val statuses = CircuitBreakerRegistry.getAllStatuses()
            call.respond(statuses.map { status ->
                CircuitBreakerInfo(
                    name = status.name,
                    state = status.state.name,
                    failureCount = status.failureCount,
                    successCount = status.successCount
                )
            })
        }

        /**
         * Reset a specific circuit breaker.
         */
        post("/circuit-breakers/{name}/reset") {
            val name = call.parameters["name"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Name required"))

            CircuitBreakerRegistry.reset(name)
            call.respond(mapOf("success" to true, "message" to "Circuit breaker '$name' reset"))
        }

        /**
         * Reset all circuit breakers.
         */
        post("/circuit-breakers/reset-all") {
            CircuitBreakerRegistry.resetAll()
            call.respond(mapOf("success" to true, "message" to "All circuit breakers reset"))
        }
    }
}
