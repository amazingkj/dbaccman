package com.dbgate.routes

import com.dbgate.model.CreateIndexRequest
import com.dbgate.service.TableService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.tableRoutes() {
    val tableService = TableService()

    authenticate("auth-jwt") {
        route("/databases") {
            get {
                try {
                    val databases = tableService.getDatabases()
                    call.respond(databases)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch databases")))
                }
            }
        }

        route("/tables") {
            get("/{database}") {
                try {
                    val database = call.parameters["database"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Database not specified"))
                    val tables = tableService.getTables(database)
                    call.respond(tables)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch tables")))
                }
            }

            get("/{database}/{table}/columns") {
                try {
                    val database = call.parameters["database"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Database not specified"))
                    val table = call.parameters["table"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Table not specified"))
                    val columns = tableService.getTableColumns(database, table)
                    call.respond(columns)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch columns")))
                }
            }

            get("/{database}/{table}/indexes") {
                try {
                    val database = call.parameters["database"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Database not specified"))
                    val table = call.parameters["table"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Table not specified"))
                    val indexes = tableService.getIndexes(database, table)
                    call.respond(indexes)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch indexes")))
                }
            }
        }

        route("/indexes") {
            post {
                try {
                    val request = call.receive<CreateIndexRequest>()
                    tableService.createIndex(request)
                    call.respond(mapOf("message" to "Index created successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to create index")))
                }
            }

            delete("/{database}/{table}/{indexName}") {
                try {
                    val database = call.parameters["database"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Database not specified"))
                    val table = call.parameters["table"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Table not specified"))
                    val indexName = call.parameters["indexName"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Index name not specified"))
                    tableService.dropIndex(database, table, indexName)
                    call.respond(mapOf("message" to "Index dropped successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to drop index")))
                }
            }
        }
    }
}
