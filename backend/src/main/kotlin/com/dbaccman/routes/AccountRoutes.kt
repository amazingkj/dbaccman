package com.dbaccman.routes

import com.dbaccman.model.ChangePasswordRequest
import com.dbaccman.model.CreateAccountRequest
import com.dbaccman.service.AccountService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.handleAdminRoute
import com.dbaccman.util.handleAdminMutationRoute
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
                call.handleAdminRoute(logger, "Failed to fetch accounts") {
                    val sessionId = call.getSessionId()
                    val accounts = accountService.getAllAccounts(sessionId)
                    call.respond(accounts)
                }
            }

            post {
                call.handleAdminMutationRoute(logger, "Failed to create account") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<CreateAccountRequest>()
                    val account = accountService.createAccount(sessionId, request)
                    call.respond(HttpStatusCode.Created, account)
                }
            }

            get("/expiring") {
                call.handleAdminRoute(logger, "Failed to fetch expiring accounts") {
                    val sessionId = call.getSessionId()
                    val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
                    val accounts = accountService.getExpiringAccounts(sessionId, days)
                    call.respond(accounts)
                }
            }

            put("/{userAtHost}/password") {
                call.handleAdminMutationRoute(logger, "Failed to change password") {
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"]
                        ?: throw IllegalArgumentException("User not specified")
                    val (username, host) = parseUserAtHost(userAtHost)
                    val request = call.receive<ChangePasswordRequest>()
                    accountService.changePassword(sessionId, username, host, request.password, request.expireImmediately)
                    call.respond(mapOf("message" to "Password changed successfully"))
                }
            }

            delete("/{userAtHost}") {
                call.handleAdminMutationRoute(logger, "Failed to delete account") {
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"]
                        ?: throw IllegalArgumentException("User not specified")
                    val (username, host) = parseUserAtHost(userAtHost)
                    accountService.deleteAccount(sessionId, username, host)
                    call.respond(mapOf("message" to "Account deleted successfully"))
                }
            }

            post("/{userAtHost}/unlock") {
                call.handleAdminMutationRoute(logger, "Failed to unlock account") {
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"]
                        ?: throw IllegalArgumentException("User not specified")
                    val (username, host) = parseUserAtHost(userAtHost)
                    accountService.unlockAccount(sessionId, username, host)
                    call.respond(mapOf("message" to "Account unlocked successfully"))
                }
            }
        }
    }
}

private fun parseUserAtHost(userAtHost: String): Pair<String, String> {
    val parts = userAtHost.split("@")
    return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(userAtHost, "%")
}