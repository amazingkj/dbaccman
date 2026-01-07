package com.dbaccman.routes

import com.dbaccman.model.*
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

                    // Check for pagination parameters
                    val page = call.request.queryParameters["page"]?.toIntOrNull()
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()
                    val sortBy = call.request.queryParameters["sortBy"]
                    val sortOrder = call.request.queryParameters["sortOrder"] ?: "asc"
                    val filter = call.request.queryParameters["filter"]  // "expiring" or "locked"

                    call.response.header(HttpHeaders.CacheControl, "no-cache, no-store, must-revalidate")
                    call.response.header(HttpHeaders.Pragma, "no-cache")
                    call.response.header(HttpHeaders.Expires, "0")

                    if (page != null && pageSize != null) {
                        // Return paginated response
                        val validPage = maxOf(1, page)
                        val validPageSize = pageSize.coerceIn(1, 100)
                        val result = accountService.getPaginatedAccounts(sessionId, validPage, validPageSize, sortBy, sortOrder, filter)
                        call.respond(result)
                    } else {
                        // Return all accounts (backward compatibility)
                        val accounts = accountService.getAllAccounts(sessionId)
                        call.respond(accounts)
                    }
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
                    call.response.header(HttpHeaders.CacheControl, "no-cache, no-store, must-revalidate")
                    call.response.header(HttpHeaders.Pragma, "no-cache")
                    call.response.header(HttpHeaders.Expires, "0")
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
                    call.respond(MessageResponse("Password changed successfully"))
                }
            }

            delete("/{userAtHost}") {
                call.handleAdminMutationRoute(logger, "Failed to delete account") {
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"]
                        ?: throw IllegalArgumentException("User not specified")
                    val (username, host) = parseUserAtHost(userAtHost)
                    accountService.deleteAccount(sessionId, username, host)
                    call.respond(MessageResponse("Account deleted successfully"))
                }
            }

            post("/{userAtHost}/unlock") {
                call.handleAdminMutationRoute(logger, "Failed to unlock account") {
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"]
                        ?: throw IllegalArgumentException("User not specified")
                    val (username, host) = parseUserAtHost(userAtHost)
                    accountService.unlockAccount(sessionId, username, host)
                    call.respond(MessageResponse("Account unlocked successfully"))
                }
            }

            post("/tablespace") {
                call.handleAdminMutationRoute(logger, "Failed to set tablespace") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<SetTablespaceRequest>()
                    accountService.setDefaultTablespace(
                        sessionId,
                        request.username,
                        request.host,
                        request.tablespace,
                        request.quota
                    )
                    call.respond(MessageResponse("Tablespace set successfully"))
                }
            }

            // ==================== Clone Account ====================

            post("/clone") {
                call.handleAdminMutationRoute(logger, "Failed to clone account") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<CloneAccountRequest>()
                    val account = accountService.cloneAccount(sessionId, request)
                    call.respond(HttpStatusCode.Created, account)
                }
            }

            // ==================== Batch Operations ====================

            post("/batch/create") {
                call.handleAdminMutationRoute(logger, "Failed to batch create accounts") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<BatchCreateAccountRequest>()
                    val result = accountService.batchCreateAccounts(sessionId, request)
                    call.respond(result)
                }
            }

            post("/batch/delete") {
                call.handleAdminMutationRoute(logger, "Failed to batch delete accounts") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<BatchDeleteRequest>()
                    val result = accountService.batchDeleteAccounts(sessionId, request)
                    call.respond(result)
                }
            }

            post("/batch/unlock") {
                call.handleAdminMutationRoute(logger, "Failed to batch unlock accounts") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<BatchUnlockRequest>()
                    val result = accountService.batchUnlockAccounts(sessionId, request)
                    call.respond(result)
                }
            }

            // ==================== Export ====================

            get("/export") {
                call.handleAdminRoute(logger, "Failed to export accounts") {
                    val sessionId = call.getSessionId()
                    val includePermissions = call.request.queryParameters["includePermissions"]?.toBoolean() ?: false
                    val format = call.request.queryParameters["format"] ?: "csv"

                    when (format.lowercase()) {
                        "csv" -> {
                            val csv = accountService.exportAccountsToCsv(sessionId, includePermissions)
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    "accounts.csv"
                                ).toString()
                            )
                            call.respondText(csv, ContentType.Text.CSV)
                        }
                        else -> {
                            call.respond(HttpStatusCode.BadRequest, MessageResponse("Unsupported format: $format"))
                        }
                    }
                }
            }
        }
    }
}

private fun parseUserAtHost(userAtHost: String): Pair<String, String> {
    val parts = userAtHost.split("@")
    return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(userAtHost, "%")
}