package com.dbaccman.routes

import com.dbaccman.service.DashboardService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.handleAuthenticatedRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DashboardRoutes")

fun Route.dashboardRoutes() {
    val dashboardService = DashboardService()

    route("/dashboard") {
        authenticate("auth-jwt") {
            get("/stats") {
                call.handleAuthenticatedRoute(logger, "Failed to fetch dashboard stats") {
                    val sessionId = call.getSessionId()
                    val stats = dashboardService.getDashboardStats(sessionId)
                    call.respond(stats)
                }
            }
        }
    }
}
