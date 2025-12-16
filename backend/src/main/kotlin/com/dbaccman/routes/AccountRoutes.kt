package com.dbaccman.routes

import com.dbaccman.model.ChangePasswordRequest
import com.dbaccman.model.CreateAccountRequest
import com.dbaccman.service.AccountService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.isAdmin
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AccountRoutes")

fun Route.accountRoutes() {
    val accountService = AccountService()

    route("/accounts") {
        authenticate("auth-jwt") {
            get {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val accounts = accountService.getAllAccounts(sessionId)
                    call.respond(accounts)
                } catch (e: Exception) {
                    logger.error("Failed to fetch accounts", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch accounts")))
                }
            }

            post {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@post
                    }
                    val sessionId = call.getSessionId()
                    val request = call.receive<CreateAccountRequest>()
                    val account = accountService.createAccount(sessionId, request)
                    call.respond(HttpStatusCode.Created, account)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to create account")))
                }
            }

            get("/expiring") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
                    val accounts = accountService.getExpiringAccounts(sessionId, days)
                    call.respond(accounts)
                } catch (e: Exception) {
                    logger.error("Failed to fetch expiring accounts", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch expiring accounts")))
                }
            }

            put("/{userAtHost}/password") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@put
                    }
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"] ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User not specified"))
                    val (username, host) = parseUserAtHost(userAtHost)
                    val request = call.receive<ChangePasswordRequest>()
                    accountService.changePassword(sessionId, username, host, request.password, request.expireImmediately)
                    call.respond(mapOf("message" to "Password changed successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to change password")))
                }
            }

            delete("/{userAtHost}") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@delete
                    }
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User not specified"))
                    val (username, host) = parseUserAtHost(userAtHost)
                    accountService.deleteAccount(sessionId, username, host)
                    call.respond(mapOf("message" to "Account deleted successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to delete account")))
                }
            }

            post("/{userAtHost}/unlock") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@post
                    }
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User not specified"))
                    val (username, host) = parseUserAtHost(userAtHost)
                    accountService.unlockAccount(sessionId, username, host)
                    call.respond(mapOf("message" to "Account unlocked successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to unlock account")))
                }
            }
        }
    }
}

private fun parseUserAtHost(userAtHost: String): Pair<String, String> {
    val parts = userAtHost.split("@")
    return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(userAtHost, "%")
}
