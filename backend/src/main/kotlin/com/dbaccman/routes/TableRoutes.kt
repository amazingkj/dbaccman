package com.dbaccman.routes

import com.dbaccman.model.CreateIndexRequest
import com.dbaccman.service.TableService
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

private val logger = LoggerFactory.getLogger("TableRoutes")

fun Route.tableRoutes() {
    val tableService = TableService()

    authenticate("auth-jwt") {
        route("/databases") {
            get {
                call.handleAdminRoute(logger, "Failed to fetch databases") {
                    val sessionId = call.getSessionId()
                    val databases = tableService.getDatabases(sessionId)
                    call.respond(databases)
                }
            }
        }

        route("/tables") {
            get("/{database}") {
                call.handleAdminRoute(logger, "Failed to fetch tables") {
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"]
                        ?: throw IllegalArgumentException("Database not specified")
                    val tables = tableService.getTables(sessionId, database)
                    call.respond(tables)
                }
            }

            get("/{database}/{table}/columns") {
                call.handleAdminRoute(logger, "Failed to fetch columns") {
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"]
                        ?: throw IllegalArgumentException("Database not specified")
                    val table = call.parameters["table"]
                        ?: throw IllegalArgumentException("Table not specified")
                    val columns = tableService.getTableColumns(sessionId, database, table)
                    call.respond(columns)
                }
            }

            get("/{database}/{table}/indexes") {
                call.handleAdminRoute(logger, "Failed to fetch indexes") {
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"]
                        ?: throw IllegalArgumentException("Database not specified")
                    val table = call.parameters["table"]
                        ?: throw IllegalArgumentException("Table not specified")
                    val indexes = tableService.getIndexes(sessionId, database, table)
                    call.respond(indexes)
                }
            }
        }

        route("/indexes") {
            post {
                call.handleAdminMutationRoute(logger, "Failed to create index") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<CreateIndexRequest>()
                    tableService.createIndex(sessionId, request)
                    call.respond(mapOf("message" to "Index created successfully"))
                }
            }

            delete("/{database}/{table}/{indexName}") {
                call.handleAdminMutationRoute(logger, "Failed to drop index") {
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"]
                        ?: throw IllegalArgumentException("Database not specified")
                    val table = call.parameters["table"]
                        ?: throw IllegalArgumentException("Table not specified")
                    val indexName = call.parameters["indexName"]
                        ?: throw IllegalArgumentException("Index name not specified")
                    tableService.dropIndex(sessionId, database, table, indexName)
                    call.respond(mapOf("message" to "Index dropped successfully"))
                }
            }
        }
    }
}