package com.dbaccman.routes

import com.dbaccman.model.GrantPermissionRequest
import com.dbaccman.model.MySQLPrivileges
import com.dbaccman.model.RevokePermissionRequest
import com.dbaccman.service.PermissionService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.isAdmin
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.permissionRoutes() {
    val permissionService = PermissionService()

    route("/permissions") {
        authenticate("auth-jwt") {
            get("/{userAtHost}") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val userAtHost = call.parameters["userAtHost"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User not specified"))
                    val (username, host) = parseUserAtHostPerm(userAtHost)
                    val permissions = permissionService.getUserPermissions(sessionId, username, host)
                    call.respond(permissions)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch permissions")))
                }
            }

            post("/grant") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@post
                    }
                    val sessionId = call.getSessionId()
                    val request = call.receive<GrantPermissionRequest>()
                    permissionService.grantPermission(sessionId, request)
                    call.respond(mapOf("message" to "Permissions granted successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to grant permissions")))
                }
            }

            post("/revoke") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@post
                    }
                    val sessionId = call.getSessionId()
                    val request = call.receive<RevokePermissionRequest>()
                    permissionService.revokePermission(sessionId, request)
                    call.respond(mapOf("message" to "Permissions revoked successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to revoke permissions")))
                }
            }

            get("/available") {
                call.respond(mapOf(
                    "all" to MySQLPrivileges.ALL,
                    "readOnly" to MySQLPrivileges.READ_ONLY,
                    "readWrite" to MySQLPrivileges.READ_WRITE,
                    "ddl" to MySQLPrivileges.DDL
                ))
            }

            get("/databases") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val databases = permissionService.getDatabases(sessionId)
                    call.respond(databases)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch databases")))
                }
            }
        }
    }
}

private fun parseUserAtHostPerm(userAtHost: String): Pair<String, String> {
    val parts = userAtHost.split("@")
    return if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(userAtHost, "%")
}
