package com.dbaccman.routes

import com.dbaccman.service.SessionService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.isAdmin
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sessionRoutes() {
    val sessionService = SessionService()

    route("/sessions") {
        authenticate("auth-jwt") {
            get {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val sessions = sessionService.getActiveSessions(sessionId)
                    call.respond(sessions)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch sessions")))
                }
            }

            get("/stats") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val stats = sessionService.getSessionStats(sessionId)
                    call.respond(stats)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch session stats")))
                }
            }

            get("/long-running") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val threshold = call.request.queryParameters["threshold"]?.toIntOrNull() ?: 60
                    val sessions = sessionService.getLongRunningQueries(sessionId, threshold)
                    call.respond(sessions)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch long running queries")))
                }
            }

            delete("/{pid}") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@delete
                    }
                    val sessionId = call.getSessionId()
                    val pid = call.parameters["pid"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid PID"))
                    sessionService.killSession(sessionId, pid)
                    call.respond(mapOf("message" to "Session killed successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to kill session")))
                }
            }

            delete("/{pid}/query") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@delete
                    }
                    val sessionId = call.getSessionId()
                    val pid = call.parameters["pid"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid PID"))
                    sessionService.killQuery(sessionId, pid)
                    call.respond(mapOf("message" to "Query killed successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to kill query")))
                }
            }
        }
    }
}
