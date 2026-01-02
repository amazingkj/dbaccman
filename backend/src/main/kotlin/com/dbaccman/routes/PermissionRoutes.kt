package com.dbaccman.routes

import com.dbaccman.model.GrantPermissionRequest
import com.dbaccman.model.MySQLPrivileges
import com.dbaccman.model.RevokePermissionRequest
import com.dbaccman.service.PermissionService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.handleAdminRoute
import com.dbaccman.util.handleAdminMutationRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("PermissionRoutes")

fun Route.permissionRoutes() {
    val permissionService = PermissionService()

    route("/permissions") {
        authenticate("auth-jwt") {
            get("/{userAtHost}") {
                call.handleAdminRoute(logger, "Failed to fetch permissions") {
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"]
                        ?: throw IllegalArgumentException("User not specified")
                    val (username, host) = parseUserAtHostPerm(userAtHost)
                    val permissions = permissionService.getUserPermissions(sessionId, username, host)
                    call.respond(permissions)
                }
            }

            post("/grant") {
                call.handleAdminMutationRoute(logger, "Failed to grant permissions") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<GrantPermissionRequest>()
                    permissionService.grantPermission(sessionId, request)
                    call.respond(MessageResponse("Permissions granted successfully"))
                }
            }

            post("/revoke") {
                call.handleAdminMutationRoute(logger, "Failed to revoke permissions") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<RevokePermissionRequest>()
                    permissionService.revokePermission(sessionId, request)
                    call.respond(MessageResponse("Permissions revoked successfully"))
                }
            }

            get("/available") {
                call.respond(AvailablePrivilegesResponse(
                    all = MySQLPrivileges.ALL,
                    readOnly = MySQLPrivileges.READ_ONLY,
                    readWrite = MySQLPrivileges.READ_WRITE,
                    ddl = MySQLPrivileges.DDL
                ))
            }

            get("/databases") {
                call.handleAdminRoute(logger, "Failed to fetch databases") {
                    val sessionId = call.getSessionId()
                    val databases = permissionService.getDatabases(sessionId)
                    call.respond(databases)
                }
            }
        }
    }
}

private fun parseUserAtHostPerm(userAtHost: String): Pair<String, String> {
    val parts = userAtHost.split("@")
    return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(userAtHost, "%")
}