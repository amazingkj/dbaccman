package com.dbgate.routes

import com.dbgate.service.SessionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sessionRoutes() {
    val sessionService = SessionService()

    route("/sessions") {
        authenticate("auth-jwt") {
            // Get all active sessions
            get {
                try {
                    val sessions = sessionService.getActiveSessions()
                    call.respond(sessions)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Failed to fetch sessions"))
                    )
                }
            }

            // Get session statistics
            get("/stats") {
                try {
                    val stats = sessionService.getSessionStats()
                    call.respond(stats)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Failed to fetch session stats"))
                    )
                }
            }

            // Get long running queries
            get("/long-running") {
                try {
                    val threshold = call.request.queryParameters["threshold"]?.toIntOrNull() ?: 60
                    val sessions = sessionService.getLongRunningQueries(threshold)
                    call.respond(sessions)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Failed to fetch long running queries"))
                    )
                }
            }

            // Kill session
            delete("/{pid}") {
                try {
                    val pid = call.parameters["pid"]?.toLongOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid PID")
                        )

                    sessionService.killSession(pid)
                    call.respond(mapOf("message" to "Session killed successfully"))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Failed to kill session"))
                    )
                }
            }

            // Kill query only (not the connection)
            delete("/{pid}/query") {
                try {
                    val pid = call.parameters["pid"]?.toLongOrNull()
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid PID")
                        )

                    sessionService.killQuery(pid)
                    call.respond(mapOf("message" to "Query killed successfully"))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Failed to kill query"))
                    )
                }
            }
        }
    }
}
