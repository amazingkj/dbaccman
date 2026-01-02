package com.dbaccman.routes

import com.dbaccman.service.TableService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.handleAdminRoute
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

            get("/{database}/{table}/data") {
                call.handleAdminRoute(logger, "Failed to fetch table data") {
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"]
                        ?: throw IllegalArgumentException("Database not specified")
                    val table = call.parameters["table"]
                        ?: throw IllegalArgumentException("Table not specified")
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val data = tableService.getTableData(sessionId, database, table, limit)
                    call.respond(data)
                }
            }
        }

    }
}