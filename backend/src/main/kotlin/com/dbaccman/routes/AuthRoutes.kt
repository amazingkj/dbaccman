package com.dbaccman.routes

import com.dbaccman.config.JwtConfig
import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.DatabaseType
import com.dbaccman.model.ConnectionLoginRequest
import com.dbaccman.model.ConnectionLoginResponse
import com.dbaccman.service.PrivilegeService
import com.dbaccman.util.AuditLogger
import com.dbaccman.util.CsrfUtil
import com.dbaccman.util.JwtUtil
import com.dbaccman.util.getSessionId
import com.dbaccman.util.getUsername
import com.dbaccman.util.getRole
import com.dbaccman.util.getDbHost
import com.dbaccman.util.getDbPort
import com.dbaccman.util.getDbType
import com.dbaccman.util.getClientIp
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes() {
    val privilegeService = PrivilegeService()

    route("/auth") {
        // CSRF token endpoint - generates a new CSRF token for the client
        get("/csrf-token") {
            val token = CsrfUtil.generateToken()
            call.respond(mapOf("csrfToken" to token))
        }

        post("/login") {
            val request = call.receive<ConnectionLoginRequest>()
            val effectivePort = request.getEffectivePort()
            val clientIp = call.getClientIp()

            try {
                // Attempt to create a session with provided credentials
                val sessionId = SessionConnectionManager.createSession(
                    host = request.host,
                    port = effectivePort,
                    username = request.username,
                    password = request.password,
                    dbType = request.dbType,
                    database = request.database
                )

                // Detect user role based on database privileges
                val role = privilegeService.detectRole(sessionId)

                // Get password expiry days
                val passwordExpiryDays = privilegeService.getPasswordExpiryDays(sessionId, request.username)

                // Generate JWT token
                val token = JwtUtil.generateToken(
                    username = request.username,
                    role = role,
                    sessionId = sessionId,
                    host = request.host,
                    port = effectivePort,
                    dbType = request.dbType
                )

                AuditLogger.log(
                    action = "LOGIN",
                    message = "Connected to ${request.host}:$effectivePort (${request.dbType.displayName}) as $role",
                    user = request.username,
                    ipAddress = clientIp
                )

                // Set httpOnly cookie with JWT token
                call.response.cookies.append(
                    Cookie(
                        name = "auth_token",
                        value = token,
                        httpOnly = true,
                        secure = false,  // Set to true in production with HTTPS
                        path = "/",
                        maxAge = (JwtConfig.expirationMs / 1000).toInt(),
                        extensions = mapOf("SameSite" to "Lax")
                    )
                )

                call.respond(
                    ConnectionLoginResponse(
                        token = token,  // Keep for backward compatibility during transition
                        username = request.username,
                        role = role,
                        host = request.host,
                        port = effectivePort,
                        dbType = request.dbType,
                        passwordExpiryDays = passwordExpiryDays
                    )
                )
            } catch (e: Exception) {
                AuditLogger.log(
                    action = "LOGIN_FAILED",
                    message = "Failed to connect to ${request.host}:$effectivePort (${request.dbType.displayName}) - ${e.message}",
                    user = request.username,
                    ipAddress = clientIp
                )

                // Translate database errors to user-friendly messages
                val errorMessage = getConnectionErrorMessage(e, request.host, effectivePort, request.dbType)
                call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse(errorMessage))
            }
        }

        post("/logout") {
            // Get session ID from token (from cookie or header) and cleanup
            val cookieToken = call.request.cookies["auth_token"]
            val authHeader = call.request.header("Authorization")
            val headerToken = if (authHeader?.startsWith("Bearer ") == true) {
                authHeader.substring(7)
            } else null

            val token = cookieToken ?: headerToken
            if (token != null) {
                val sessionId = JwtUtil.getSessionIdFromToken(token)
                if (sessionId != null) {
                    SessionConnectionManager.closeSession(sessionId)
                    AuditLogger.log("LOGOUT", "Session $sessionId closed")
                }
            }

            // Clear the auth cookie
            call.response.cookies.append(
                Cookie(
                    name = "auth_token",
                    value = "",
                    httpOnly = true,
                    secure = false,
                    path = "/",
                    maxAge = 0,
                    extensions = mapOf("SameSite" to "Lax")
                )
            )

            call.respond(MessageResponse("Logged out successfully"))
        }

        authenticate("auth-jwt") {
            get("/me") {
                try {
                    val sessionId = call.getSessionId()
                    val username = call.getUsername()
                    val role = call.getRole()
                    val host = call.getDbHost()
                    val port = call.getDbPort()
                    val dbType = call.getDbType()

                    // Check if session is still valid
                    if (!SessionConnectionManager.hasSession(sessionId)) {
                        call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("Session expired"))
                        return@get
                    }

                    call.respond(UserInfoResponse(
                        username = username,
                        role = role,
                        host = host,
                        port = port,
                        dbType = dbType.name
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("Invalid token"))
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
                        call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("Session expired"))
                        return@get
                    }

                    val expiryInfo = privilegeService.getMyPasswordExpiry(sessionId, username, host)
                    call.respond(expiryInfo)
                } catch (e: Exception) {
                    AuditLogger.log("PASSWORD_EXPIRY_ERROR", "Failed for user: ${e.message}")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiErrorResponse("Failed to get password expiry info: ${e.message?.take(100)}")
                    )
                }
            }
        }
    }
}

/**
 * Translates database connection errors to user-friendly messages.
 */
private fun getConnectionErrorMessage(e: Exception, host: String, port: Int, dbType: DatabaseType): String {
    val message = e.message ?: "Unknown error"

    return when {
        // MySQL errors
        message.contains("Access denied") ->
            "Invalid username or password"
        message.contains("Communications link failure") ->
            "Cannot connect to ${dbType.displayName} server at $host:$port. Check if the server is running."
        message.contains("Unknown database") ->
            "Database not found. Check the database/schema name."
        message.contains("Unknown host") ->
            "Unknown host: $host"
        message.contains("Connection refused") ->
            "Connection refused. Check if ${dbType.displayName} is running on $host:$port"
        message.contains("connect timed out") ->
            "Connection timed out. Server at $host:$port may be unreachable."

        // Oracle errors
        message.contains("ORA-01017") ->
            "Invalid username or password"
        message.contains("ORA-01045") ->
            "User lacks CREATE SESSION privilege. Contact your DBA to grant login permission."
        message.contains("ORA-12541") ->
            "No listener. Check if Oracle listener is running on $host:$port"
        message.contains("ORA-12514") ->
            "Service not found. The specified service name is not registered with the listener on $host:$port. Check your Oracle SID/Service Name."
        message.contains("ORA-12505") ->
            "SID not found. The specified SID is not recognized by the listener on $host:$port"
        message.contains("ORA-12170") ->
            "Connection timeout. Cannot reach Oracle server at $host:$port"
        message.contains("ORA-28000") ->
            "Account is locked. Contact your DBA to unlock the account."
        message.contains("ORA-28001") ->
            "Password has expired. Please change your password."

        // PostgreSQL errors
        message.contains("FATAL: password authentication failed") ->
            "Invalid username or password"
        message.contains("FATAL: database") && message.contains("does not exist") ->
            "Database not found. Check the database name."
        message.contains("FATAL: role") && message.contains("does not exist") ->
            "User not found. Check the username."
        message.contains("Connection to $host:$port refused") || message.contains("Connection refused") ->
            "Connection refused. Check if ${dbType.displayName} is running on $host:$port"
        message.contains("The connection attempt failed") ->
            "Cannot connect to ${dbType.displayName} server at $host:$port"

        // Generic - show more detail for debugging
        else ->
            "Connection failed: ${message.take(200)}"
    }
}