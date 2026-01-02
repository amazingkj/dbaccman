package com.dbaccman.routes

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.service.QueryExecutionException
import com.dbaccman.service.QueryService
import com.dbaccman.util.AuditLogger
import com.dbaccman.util.getClientIp
import com.dbaccman.util.getSessionId
import com.dbaccman.util.getUsername
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ExecuteQueryRequest(
    val query: String,
    val account: String? = null  // Account/schema to run query as
)

@Serializable
data class SwitchSchemaRequest(
    val schema: String
)

@Serializable
data class SchemaInfo(
    val currentSchema: String,
    val availableSchemas: List<String>
)

@Serializable
data class QueryResultResponse(
    val columns: List<String>,
    val rows: List<List<String?>>,
    val rowCount: Int,
    val executionTimeMs: Long,
    val affectedRows: Int? = null,
    val isSelectQuery: Boolean = true
)

@Serializable
data class QueryErrorResponse(
    val error: String,
    val executionTimeMs: Long? = null
)

fun Route.queryRoutes() {
    val queryService = QueryService()

    route("/query") {
        authenticate("auth-jwt") {
            // Execute SQL query
            post("/execute") {
                try {
                    val sessionId = call.getSessionId()
                    val username = call.getUsername()
                    val clientIp = call.getClientIp()

                    // Check if session is still valid
                    if (!SessionConnectionManager.hasSession(sessionId)) {
                        call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("Session expired"))
                        return@post
                    }

                    val request = call.receive<ExecuteQueryRequest>()

                    val result = queryService.executeQuery(
                        sessionId = sessionId,
                        query = request.query,
                        account = request.account,
                        username = username,
                        ipAddress = clientIp
                    )

                    call.respond(QueryResultResponse(
                        columns = result.columns,
                        rows = result.rows.map { row -> row.map { it?.toString() } },
                        rowCount = result.rowCount,
                        executionTimeMs = result.executionTimeMs,
                        affectedRows = result.affectedRows,
                        isSelectQuery = result.isSelectQuery
                    ))
                } catch (e: QueryExecutionException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        QueryErrorResponse(
                            error = e.message ?: "Query execution failed",
                            executionTimeMs = e.executionTimeMs
                        )
                    )
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        QueryErrorResponse(error = e.message ?: "Invalid query")
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        QueryErrorResponse(error = e.message ?: "An unexpected error occurred")
                    )
                }
            }

            // Get audit logs (admin only)
            get("/audit-logs") {
                try {
                    val sessionId = call.getSessionId()

                    if (!SessionConnectionManager.hasSession(sessionId)) {
                        call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("Session expired"))
                        return@get
                    }

                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val action = call.request.queryParameters["action"]
                    val user = call.request.queryParameters["user"]

                    val logs = AuditLogger.getRecentLogs(
                        limit = limit.coerceIn(1, 500),
                        action = action,
                        user = user
                    )

                    call.respond(logs)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiErrorResponse(e.message ?: "Failed to get audit logs")
                    )
                }
            }

            // Get current schema and available schemas
            get("/schemas") {
                try {
                    val sessionId = call.getSessionId()

                    if (!SessionConnectionManager.hasSession(sessionId)) {
                        call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("Session expired"))
                        return@get
                    }

                    val schemaInfo = useSessionConnectionWithDialect(sessionId) { conn, dialect ->
                        // Get current schema
                        val currentSchema = conn.createStatement().use { stmt ->
                            stmt.executeQuery(dialect.getCurrentSchemaQuery()).use { rs ->
                                if (rs.next()) rs.getString(1) ?: "" else ""
                            }
                        }

                        // Get available schemas
                        val schemas = mutableListOf<String>()
                        conn.createStatement().use { stmt ->
                            stmt.executeQuery(dialect.getAvailableSchemasQuery()).use { rs ->
                                while (rs.next()) {
                                    rs.getString(1)?.let { schemas.add(it) }
                                }
                            }
                        }

                        SchemaInfo(currentSchema, schemas)
                    }

                    call.respond(schemaInfo)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiErrorResponse(e.message ?: "Failed to get schema info")
                    )
                }
            }

            // Switch schema
            post("/switch-schema") {
                val sessionId = call.getSessionId()

                if (!SessionConnectionManager.hasSession(sessionId)) {
                    call.respond(HttpStatusCode.Unauthorized, ApiErrorResponse("Session expired"))
                    return@post
                }

                val request = call.receive<SwitchSchemaRequest>()

                if (request.schema.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("Schema name cannot be empty"))
                    return@post
                }

                try {
                    useSessionConnectionWithDialect(sessionId) { conn, dialect ->
                        val switchSql = dialect.getSwitchSchemaSql(request.schema)
                        if (switchSql != null) {
                            conn.createStatement().use { stmt ->
                                stmt.execute(switchSql)
                            }
                        }
                    }

                    call.respond(SwitchSchemaResponse(success = true, schema = request.schema))
                } catch (e: Exception) {
                    val errorMsg = when {
                        e.message?.contains("ORA-01435") == true -> "Schema '${request.schema}' does not exist"
                        e.message?.contains("ORA-01031") == true -> "Insufficient privileges to switch to schema '${request.schema}'"
                        else -> e.message ?: "Failed to switch schema"
                    }
                    call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(errorMsg))
                }
            }
        }
    }
}