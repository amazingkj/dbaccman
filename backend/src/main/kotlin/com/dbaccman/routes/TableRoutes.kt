package com.dbaccman.routes

import com.dbaccman.service.TableService
import com.dbaccman.util.InputValidator
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
                    InputValidator.validateIdentifier(database, "database")
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
                    InputValidator.validateIdentifier(database, "database")
                    InputValidator.validateIdentifier(table, "table")
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
                    InputValidator.validateIdentifier(database, "database")
                    InputValidator.validateIdentifier(table, "table")
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
                    InputValidator.validateIdentifier(database, "database")
                    InputValidator.validateIdentifier(table, "table")
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val data = tableService.getTableData(sessionId, database, table, limit.coerceIn(1, 1000))
                    call.respond(data)
                }
            }

            post("/{database}/gather-stats") {
                call.handleAdminRoute(logger, "Failed to gather statistics") {
                    val sessionId = call.getSessionId()
                    val database = call.parameters["database"]
                        ?: throw IllegalArgumentException("Database not specified")
                    InputValidator.validateIdentifier(database, "database")
                    val table = call.request.queryParameters["table"]
                    table?.let { InputValidator.validateIdentifier(it, "table") }
                    val success = tableService.gatherStats(sessionId, database, table)
                    call.respond(mapOf("success" to success))
                }
            }
        }

    }
}