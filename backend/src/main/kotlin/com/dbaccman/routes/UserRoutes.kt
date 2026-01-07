package com.dbaccman.routes

import com.dbaccman.service.UserDataService
import com.dbaccman.util.getSessionId
import com.dbaccman.util.handleAuthenticatedRoute
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserRoutes")

/**
 * Routes for regular users to access their own schema data.
 * These routes don't require admin privileges.
 */
fun Route.userRoutes() {
    val userDataService = UserDataService()

    authenticate("auth-jwt") {
        route("/user") {
            // Get tables owned by the current user
            get("/tables") {
                call.handleAuthenticatedRoute(logger, "Failed to fetch user tables") {
                    val sessionId = call.getSessionId()
                    val tables = userDataService.getMyTables(sessionId)
                    call.respond(tables)
                }
            }

            // Get columns for a specific table owned by the current user
            get("/tables/{table}/columns") {
                call.handleAuthenticatedRoute(logger, "Failed to fetch table columns") {
                    val sessionId = call.getSessionId()
                    val table = call.parameters["table"]
                        ?: throw IllegalArgumentException("Table not specified")
                    val columns = userDataService.getMyTableColumns(sessionId, table)
                    call.respond(columns)
                }
            }

            // Get indexes for a specific table owned by the current user
            get("/tables/{table}/indexes") {
                call.handleAuthenticatedRoute(logger, "Failed to fetch table indexes") {
                    val sessionId = call.getSessionId()
                    val table = call.parameters["table"]
                        ?: throw IllegalArgumentException("Table not specified")
                    val indexes = userDataService.getMyTableIndexes(sessionId, table)
                    call.respond(indexes)
                }
            }

            // Get table data (limited to own tables)
            get("/tables/{table}/data") {
                call.handleAuthenticatedRoute(logger, "Failed to fetch table data") {
                    val sessionId = call.getSessionId()
                    val table = call.parameters["table"]
                        ?: throw IllegalArgumentException("Table not specified")
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val data = userDataService.getMyTableData(sessionId, table, limit)
                    call.respond(data)
                }
            }

            // Get tablespace quotas for the current user
            get("/tablespaces") {
                call.handleAuthenticatedRoute(logger, "Failed to fetch user tablespaces") {
                    val sessionId = call.getSessionId()
                    val tablespaces = userDataService.getMyTablespaces(sessionId)
                    call.respond(tablespaces)
                }
            }

            // Execute query (restricted to own schema)
            post("/query") {
                call.handleAuthenticatedRoute(logger, "Failed to execute query") {
                    val sessionId = call.getSessionId()
                    val request = call.receive<UserQueryRequest>()
                    val result = userDataService.executeUserQuery(sessionId, request.query, request.limit ?: 1000)
                    call.respond(result)
                }
            }
        }
    }
}

data class UserQueryRequest(
    val query: String,
    val limit: Int? = 1000
)
