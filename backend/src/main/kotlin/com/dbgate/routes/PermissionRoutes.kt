package com.dbgate.routes

import com.dbgate.model.GrantPermissionRequest
import com.dbgate.model.MySQLPrivileges
import com.dbgate.model.RevokePermissionRequest
import com.dbgate.service.PermissionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.permissionRoutes() {
    val permissionService = PermissionService()

    route("/permissions") {
        authenticate("auth-jwt") {
            // Get user permissions
            get("/{userAtHost}") {
                try {
                    val userAtHost = call.parameters["userAtHost"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User not specified"))

                    val (username, host) = parseUserAtHostForPermission(userAtHost)
                    val permissions = permissionService.getUserPermissions(username, host)
                    call.respond(permissions)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Failed to fetch permissions"))
                    )
                }
            }

            // Grant permissions
            post("/grant") {
                try {
                    val request = call.receive<GrantPermissionRequest>()
                    permissionService.grantPermission(request)
                    call.respond(mapOf("message" to "Permissions granted successfully"))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Failed to grant permissions"))
                    )
                }
            }

            // Revoke permissions
            post("/revoke") {
                try {
                    val request = call.receive<RevokePermissionRequest>()
                    permissionService.revokePermission(request)
                    call.respond(mapOf("message" to "Permissions revoked successfully"))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Failed to revoke permissions"))
                    )
                }
            }

            // Get available privileges
            get("/available") {
                call.respond(
                    mapOf(
                        "all" to MySQLPrivileges.ALL,
                        "readOnly" to MySQLPrivileges.READ_ONLY,
                        "readWrite" to MySQLPrivileges.READ_WRITE,
                        "ddl" to MySQLPrivileges.DDL
                    )
                )
            }

            // Get available databases
            get("/databases") {
                try {
                    val databases = permissionService.getDatabases()
                    call.respond(databases)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Failed to fetch databases"))
                    )
                }
            }
        }
    }
}

private fun parseUserAtHostForPermission(userAtHost: String): Pair<String, String> {
    val parts = userAtHost.split("@")
    return if (parts.size == 2) {
        Pair(parts[0], parts[1])
    } else {
        Pair(userAtHost, "%")
    }
}
