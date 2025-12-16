package com.dbgate.routes

import com.dbgate.model.ChangePasswordRequest
import com.dbgate.model.CreateAccountRequest
import com.dbgate.service.AccountService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.accountRoutes() {
    val accountService = AccountService()

    route("/accounts") {
        authenticate("auth-jwt") {
            get {
                try {
                    val accounts = accountService.getAllAccounts()
                    call.respond(accounts)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch accounts")))
                }
            }

            post {
                try {
                    val request = call.receive<CreateAccountRequest>()
                    val account = accountService.createAccount(request)
                    call.respond(HttpStatusCode.Created, account)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to create account")))
                }
            }

            get("/expiring") {
                try {
                    val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
                    val accounts = accountService.getExpiringAccounts(days)
                    call.respond(accounts)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch expiring accounts")))
                }
            }

            put("/{userAtHost}/password") {
                try {
                    val userAtHost = call.parameters["userAtHost"] ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User not specified"))
                    val (username, host) = parseUserAtHost(userAtHost)
                    val request = call.receive<ChangePasswordRequest>()
                    accountService.changePassword(username, host, request.password)
                    call.respond(mapOf("message" to "Password changed successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to change password")))
                }
            }

            delete("/{userAtHost}") {
                try {
                    val userAtHost = call.parameters["userAtHost"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User not specified"))
                    val (username, host) = parseUserAtHost(userAtHost)
                    accountService.deleteAccount(username, host)
                    call.respond(mapOf("message" to "Account deleted successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to delete account")))
                }
            }

            post("/{userAtHost}/unlock") {
                try {
                    val userAtHost = call.parameters["userAtHost"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User not specified"))
                    val (username, host) = parseUserAtHost(userAtHost)
                    accountService.unlockAccount(username, host)
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
