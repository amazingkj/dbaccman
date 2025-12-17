package com.dbaccman.routes

import com.dbaccman.model.CreateTablespaceRequest
import com.dbaccman.model.TableLocationRequest
import com.dbaccman.service.TablespaceService
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

private val logger = LoggerFactory.getLogger("TablespaceRoutes")

fun Route.tablespaceRoutes() {
    val tablespaceService = TablespaceService()

    route("/tablespaces") {
        authenticate("auth-jwt") {
            get {
                call.handleAdminRoute(logger, "Failed to fetch tablespaces") {
                    val sessionId = call.getSessionId()
                    val tablespaces = tablespaceService.getTablespaces(sessionId)
                    call.respond(tablespaces)
                }
            }

            post {
                call.handleAdminMutationRoute(logger, "Failed to create tablespace") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<CreateTablespaceRequest>()
                    tablespaceService.createTablespace(sessionId, request)
                    call.respond(HttpStatusCode.Created, mapOf("message" to "Tablespace created successfully"))
                }
            }

            delete("/{name}") {
                call.handleAdminMutationRoute(logger, "Failed to drop tablespace") {
                    val sessionId = call.getSessionId()
                    val name = call.parameters["name"]
                        ?: throw IllegalArgumentException("Tablespace name not specified")
                    tablespaceService.dropTablespace(sessionId, name)
                    call.respond(mapOf("message" to "Tablespace dropped successfully"))
                }
            }

            get("/{name}/tables") {
                call.handleAdminRoute(logger, "Failed to fetch tables in tablespace") {
                    val sessionId = call.getSessionId()
                    val name = call.parameters["name"]
                        ?: throw IllegalArgumentException("Tablespace name not specified")
                    val tables = tablespaceService.getTablesInTablespace(sessionId, name)
                    call.respond(tables)
                }
            }

            post("/move-table") {
                call.handleAdminMutationRoute(logger, "Failed to move table") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<TableLocationRequest>()
                    tablespaceService.moveTableToTablespace(sessionId, request)
                    call.respond(mapOf("message" to "Table moved successfully"))
                }
            }
        }
    }
}