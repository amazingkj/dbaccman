package com.dbaccman.routes

import com.dbaccman.model.CreateIndexRequest
import com.dbaccman.service.TableService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.isAdmin
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TableRoutes")

fun Route.tableRoutes() {
    val tableService = TableService()

    authenticate("auth-jwt") {
        route("/databases") {
            get {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val databases = tableService.getDatabases(sessionId)
                    call.respond(databases)
                } catch (e: Exception) {
                    logger.error("Failed to fetch databases", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch databases")))
                }
            }
        }

        route("/tables") {
            get("/{database}") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Database not specified"))
                    val tables = tableService.getTables(sessionId, database)
                    call.respond(tables)
                } catch (e: Exception) {
                    logger.error("Failed to fetch tables", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch tables")))
                }
            }

            get("/{database}/{table}/columns") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Database not specified"))
                    val table = call.parameters["table"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Table not specified"))
                    val columns = tableService.getTableColumns(sessionId, database, table)
                    call.respond(columns)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch columns")))
                }
            }

            get("/{database}/{table}/indexes") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@get
                    }
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Database not specified"))
                    val table = call.parameters["table"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Table not specified"))
                    val indexes = tableService.getIndexes(sessionId, database, table)
                    call.respond(indexes)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch indexes")))
                }
            }
        }

        route("/indexes") {
            post {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@post
                    }
                    val sessionId = call.getSessionId()
                    val request = call.receive<CreateIndexRequest>()
                    tableService.createIndex(sessionId, request)
                    call.respond(mapOf("message" to "Index created successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to create index")))
                }
            }

            delete("/{database}/{table}/{indexName}") {
                try {
                    if (!call.isAdmin()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                        return@delete
                    }
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Database not specified"))
                    val table = call.parameters["table"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Table not specified"))
                    val indexName = call.parameters["indexName"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Index name not specified"))
                    tableService.dropIndex(sessionId, database, table, indexName)
                    call.respond(mapOf("message" to "Index dropped successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to drop index")))
                }
            }
        }
    }
}
