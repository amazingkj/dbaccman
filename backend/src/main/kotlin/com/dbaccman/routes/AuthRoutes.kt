package com.dbaccman.routes

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.model.ConnectionLoginRequest
import com.dbaccman.model.ConnectionLoginResponse
import com.dbaccman.model.PasswordExpiryInfo
import com.dbaccman.service.PrivilegeService
import com.dbaccman.util.AuditLogger
import com.dbaccman.util.JwtUtil
import com.dbaccman.util.getSessionId
import com.dbaccman.util.getUsername
import com.dbaccman.util.getRole
import com.dbaccman.util.getDbHost
import com.dbaccman.util.getDbPort
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes() {
    val privilegeService = PrivilegeService()

    route("/auth") {
        post("/login") {
            val request = call.receive<ConnectionLoginRequest>()

            try {
                // Attempt to create a session with provided credentials
                val sessionId = SessionConnectionManager.createSession(
                    host = request.host,
                    port = request.port,
                    username = request.username,
                    password = request.password
                )

                // Detect user role based on MySQL privileges
                val role = privilegeService.detectRole(sessionId)

                // Get password expiry days
                val passwordExpiryDays = privilegeService.getPasswordExpiryDays(sessionId, request.username)

                // Generate JWT token
                val token = JwtUtil.generateToken(
                    username = request.username,
                    role = role,
                    sessionId = sessionId,
                    host = request.host,
                    port = request.port
                )

                AuditLogger.log(
                    "LOGIN",
                    "User ${request.username} connected to ${request.host}:${request.port} as $role"
                )

                call.respond(
                    ConnectionLoginResponse(
                        token = token,
                        username = request.username,
                        role = role,
                        host = request.host,
                        port = request.port,
                        passwordExpiryDays = passwordExpiryDays
                    )
                )
            } catch (e: Exception) {
                AuditLogger.log(
                    "LOGIN_FAILED",
                    "Failed login for ${request.username}@${request.host}:${request.port} - ${e.message}"
                )

                // Translate MySQL errors to user-friendly messages
                val errorMessage = when {
                    e.message?.contains("Access denied") == true ->
                        "Invalid username or password"
                    e.message?.contains("Communications link failure") == true ->
                        "Cannot connect to MySQL server at ${request.host}:${request.port}"
                    e.message?.contains("Unknown host") == true ->
                        "Unknown host: ${request.host}"
                    e.message?.contains("Connection refused") == true ->
                        "Connection refused. Check if MySQL is running on port ${request.port}"
                    e.message?.contains("connect timed out") == true ->
                        "Connection timed out. Server may be unreachable"
                    else ->
                        "Connection failed: ${e.message?.take(100) ?: "Unknown error"}"
                }

                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to errorMessage))
            }
        }

        post("/logout") {
            // Get session ID from token and cleanup
            val authHeader = call.request.header("Authorization")
            if (authHeader?.startsWith("Bearer ") == true) {
                val token = authHeader.substring(7)
                val sessionId = JwtUtil.getSessionIdFromToken(token)
                if (sessionId != null) {
                    SessionConnectionManager.closeSession(sessionId)
                    AuditLogger.log("LOGOUT", "Session $sessionId closed")
                }
            }
            call.respond(mapOf("message" to "Logged out successfully"))
        }

        authenticate("auth-jwt") {
            get("/me") {
                try {
                    val sessionId = call.getSessionId()
                    val username = call.getUsername()
                    val role = call.getRole()
                    val host = call.getDbHost()
                    val port = call.getDbPort()

                    // Check if session is still valid
                    if (!SessionConnectionManager.hasSession(sessionId)) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Session expired"))
                        return@get
                    }

                    call.respond(mapOf(
                        "username" to username,
                        "role" to role,
                        "host" to host,
                        "port" to port
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token"))
                }
            }

            // Endpoint for regular users to check their password expiry
            get("/password-expiry") {
                try {
                    val sessionId = call.getSessionId()
                    val username = call.getUsername()
                    val host = call.getDbHost()

                    // Check if session is still valid
                    if (!SessionConnectionManager.hasSession(sessionId)) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Session expired"))
                        return@get
                    }

                    val expiryInfo = privilegeService.getMyPasswordExpiry(sessionId, username, host)
                    call.respond(expiryInfo)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to get password expiry info")
                    )
                }
            }
        }
    }
}
