package com.dbgate.routes

import com.dbgate.model.LoginRequest
import com.dbgate.model.LoginResponse
import com.dbgate.model.UserInfo
import com.dbgate.service.AccountService
import com.dbgate.util.AuditLogger
import com.dbgate.util.JwtUtil
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes() {
    val accountService = AccountService()

    route("/auth") {
        post("/login") {
            val request = call.receive<LoginRequest>()
            val isValid = accountService.validateCredentials(request.username, request.password)

            if (isValid) {
                val token = JwtUtil.generateToken(request.username, "admin")
                AuditLogger.log("LOGIN", "User ${request.username} logged in successfully")
                call.respond(
                    LoginResponse(
                        token = token,
                        username = request.username,
                        role = "admin"
                    )
                )
            } else {
                AuditLogger.log("LOGIN_FAILED", "Failed login attempt for ${request.username}")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
            }
        }

        post("/logout") {
            AuditLogger.log("LOGOUT", "User logged out")
            call.respond(mapOf("message" to "Logged out successfully"))
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.payload?.getClaim("username")?.asString()
                val role = principal?.payload?.getClaim("role")?.asString()

                if (username != null && role != null) {
                    call.respond(UserInfo(username = username, role = role))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                }
            }
        }
    }
}
