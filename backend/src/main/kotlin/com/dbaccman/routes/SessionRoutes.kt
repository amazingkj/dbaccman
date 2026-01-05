package com.dbaccman.routes

import com.dbaccman.service.SessionService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.handleAdminRoute
import com.dbaccman.util.handleAdminMutationRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SessionRoutes")

@Serializable
data class BulkKillRequest(
    val sessions: List<SessionTargetDto>
)

@Serializable
data class SessionTargetDto(
    val pid: Long,
    val serialNum: Long? = null
)

@Serializable
data class BulkKillResponse(
    val success: Int,
    val failed: Int,
    val errors: List<String>,
    val message: String
)

fun Route.sessionRoutes() {
    val sessionService = SessionService()

    route("/sessions") {
        authenticate("auth-jwt") {
            get {
                call.handleAdminRoute(logger, "Failed to fetch sessions") {
                    val sessionId = call.getSessionId()
                    val sessions = sessionService.getActiveSessions(sessionId)
                    call.respond(sessions)
                }
            }

            get("/stats") {
                call.handleAdminRoute(logger, "Failed to fetch session stats") {
                    val sessionId = call.getSessionId()
                    val stats = sessionService.getSessionStats(sessionId)
                    call.respond(stats)
                }
            }

            get("/long-running") {
                call.handleAdminRoute(logger, "Failed to fetch long running queries") {
                    val sessionId = call.getSessionId()
                    val threshold = call.request.queryParameters["threshold"]?.toIntOrNull() ?: 60
                    val sessions = sessionService.getLongRunningQueries(sessionId, threshold)
                    call.respond(sessions)
                }
            }

            delete("/{pid}") {
                call.handleAdminMutationRoute(logger, "Failed to kill session") {
                    val sessionId = call.getSessionId()
                    val pid = call.parameters["pid"]?.toLongOrNull()
                        ?: throw IllegalArgumentException("Invalid PID")
                    val serialNum = call.request.queryParameters["serialNum"]?.toLongOrNull()
                    sessionService.killSession(sessionId, pid, serialNum)
                    call.respond(MessageResponse("Session killed successfully"))
                }
            }

            delete("/{pid}/query") {
                call.handleAdminMutationRoute(logger, "Failed to kill query") {
                    val sessionId = call.getSessionId()
                    val pid = call.parameters["pid"]?.toLongOrNull()
                        ?: throw IllegalArgumentException("Invalid PID")
                    val serialNum = call.request.queryParameters["serialNum"]?.toLongOrNull()
                    sessionService.killQuery(sessionId, pid, serialNum)
                    call.respond(MessageResponse("Query killed successfully"))
                }
            }

            post("/bulk-kill") {
                call.handleAdminMutationRoute(logger, "Failed to bulk kill sessions") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<BulkKillRequest>()

                    if (request.sessions.isEmpty()) {
                        throw IllegalArgumentException("No sessions specified")
                    }

                    val targets = request.sessions.map {
                        SessionService.SessionTarget(it.pid, it.serialNum)
                    }
                    val result = sessionService.killSessions(sessionId, targets)

                    call.respond(BulkKillResponse(
                        success = result.success,
                        failed = result.failed,
                        errors = result.errors,
                        message = "Killed ${result.success} session(s), ${result.failed} failed"
                    ))
                }
            }
        }
    }
}