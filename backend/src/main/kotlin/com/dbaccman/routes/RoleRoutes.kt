package com.dbaccman.routes

import com.dbaccman.model.GrantRoleRequest
import com.dbaccman.model.RevokeRoleRequest
import com.dbaccman.service.RoleService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.handleAdminRoute
import com.dbaccman.util.handleAdminMutationRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("RoleRoutes")

fun Route.roleRoutes() {
    val roleService = RoleService()

    route("/roles") {
        authenticate("auth-jwt") {
            // Get all available roles
            get {
                call.handleAdminRoute(logger, "Failed to fetch roles") {
                    val sessionId = call.getSessionId()
                    val roles = roleService.getAllRoles(sessionId)
                    call.respond(roles)
                }
            }

            // Get common/recommended roles
            get("/common") {
                call.handleAdminRoute(logger, "Failed to fetch common roles") {
                    val sessionId = call.getSessionId()
                    val roles = roleService.getCommonRoles(sessionId)
                    call.respond(roles)
                }
            }

            // Get roles for a specific user
            get("/user/{username}") {
                call.handleAdminRoute(logger, "Failed to fetch user roles") {
                    val sessionId = call.getSessionId()
                    val username = call.parameters["username"]
                        ?: throw IllegalArgumentException("Username not specified")
                    val roles = roleService.getUserRoles(sessionId, username)
                    call.respond(roles)
                }
            }

            // Grant roles to a user
            post("/grant") {
                call.handleAdminMutationRoute(logger, "Failed to grant roles") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<GrantRoleRequest>()
                    roleService.grantRoles(sessionId, request)
                    call.respond(MessageResponse("Roles granted successfully"))
                }
            }

            // Revoke roles from a user
            post("/revoke") {
                call.handleAdminMutationRoute(logger, "Failed to revoke roles") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<RevokeRoleRequest>()
                    roleService.revokeRoles(sessionId, request)
                    call.respond(MessageResponse("Roles revoked successfully"))
                }
            }
        }
    }

    // PDB Management Routes
    route("/pdb") {
        authenticate("auth-jwt") {
            // Get list of PDBs
            get {
                call.handleAdminRoute(logger, "Failed to fetch PDB list") {
                    val sessionId = call.getSessionId()
                    val pdbs = roleService.getPdbList(sessionId)
                    call.respond(pdbs)
                }
            }

            // Get current container
            get("/current") {
                call.handleAdminRoute(logger, "Failed to get current container") {
                    val sessionId = call.getSessionId()
                    val container = roleService.getCurrentContainer(sessionId)
                    call.respond(mapOf("container" to container))
                }
            }

            // Switch to a PDB
            post("/switch/{pdbName}") {
                call.handleAdminMutationRoute(logger, "Failed to switch PDB") {
                    val sessionId = call.getSessionId()
                    val pdbName = call.parameters["pdbName"]
                        ?: throw IllegalArgumentException("PDB name not specified")
                    roleService.switchPdb(sessionId, pdbName)
                    call.respond(MessageResponse("Switched to PDB: $pdbName"))
                }
            }
        }
    }
}
