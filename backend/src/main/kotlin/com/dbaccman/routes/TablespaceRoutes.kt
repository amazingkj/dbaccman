package com.dbaccman.routes

import com.dbaccman.model.CreateTablespaceRequest
import com.dbaccman.model.TableLocationRequest
import com.dbaccman.service.TablespaceService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.isAdmin
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
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val tablespaces = tablespaceService.getTablespaces(sessionId)
                    call.respond(tablespaces)
                } catch (e: Exception) {
                    logger.error("Failed to fetch tablespaces", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch tablespaces")))
                }
            }

            post {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@post
                    }
                    val sessionId = call.getSessionId()
                    val request = call.receive<CreateTablespaceRequest>()
                    tablespaceService.createTablespace(sessionId, request)
                    call.respond(HttpStatusCode.Created, mapOf("message" to "Tablespace created successfully"))
                } catch (e: Exception) {
                    logger.error("Failed to create tablespace", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to create tablespace")))
                }
            }

            delete("/{name}") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@delete
                    }
                    val sessionId = call.getSessionId()
                    val name = call.parameters["name"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Tablespace name not specified")
                    )
                    tablespaceService.dropTablespace(sessionId, name)
                    call.respond(mapOf("message" to "Tablespace dropped successfully"))
                } catch (e: Exception) {
                    logger.error("Failed to drop tablespace", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to drop tablespace")))
                }
            }

            get("/{name}/tables") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val name = call.parameters["name"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Tablespace name not specified")
                    )
                    val tables = tablespaceService.getTablesInTablespace(sessionId, name)
                    call.respond(tables)
                } catch (e: Exception) {
                    logger.error("Failed to fetch tables in tablespace", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch tables")))
                }
            }

            post("/move-table") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@post
                    }
                    val sessionId = call.getSessionId()
                    val request = call.receive<TableLocationRequest>()
                    tablespaceService.moveTableToTablespace(sessionId, request)
                    call.respond(mapOf("message" to "Table moved successfully"))
                } catch (e: Exception) {
                    logger.error("Failed to move table", e)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to move table")))
                }
            }
        }
    }
}
