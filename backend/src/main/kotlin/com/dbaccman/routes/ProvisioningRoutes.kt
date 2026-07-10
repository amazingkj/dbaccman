package com.dbaccman.routes

import com.dbaccman.model.ProvisionRequest
import com.dbaccman.service.ProvisioningService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.handleAdminMutationRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ProvisioningRoutes")

fun Route.provisioningRoutes() {
    val provisioningService = ProvisioningService()

    route("/provisioning") {
        authenticate("auth-jwt") {
            post("/preview") {
                call.handleAdminMutationRoute(logger, "Failed to build provisioning plan") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<ProvisionRequest>()
                    call.respond(provisioningService.preview(sessionId, request))
                }
            }

            post("/execute") {
                call.handleAdminMutationRoute(logger, "Provisioning failed") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<ProvisionRequest>()
                    // Always 200: per-step status (including failures) is in the body
                    call.respond(provisioningService.execute(sessionId, request))
                }
            }
        }
    }
}
