package com.dbaccman.exception

import com.dbaccman.util.CircuitBreakerOpenException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.sql.SQLException

private val logger = LoggerFactory.getLogger("ErrorHandler")

/**
 * Standard error response format for all API errors.
 */
@Serializable
data class ErrorResponse(
    val error: String,
    val errorCode: String,
    val details: Map<String, String>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Configures centralized error handling for the application.
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        // Handle our custom exceptions
        exception<AppException> { call, cause ->
            logger.warn("AppException: ${cause.errorCode} - ${cause.message}")

            val details = mutableMapOf<String, String>()
            when (cause) {
                is ValidationException -> cause.field?.let { details["field"] = it }
                is NotFoundException -> cause.resourceType?.let { details["resourceType"] = it }
                is DatabaseException -> {
                    cause.sqlState?.let { details["sqlState"] = it }
                    cause.vendorCode?.let { details["vendorCode"] = it.toString() }
                }
                is RateLimitException -> details["retryAfterSeconds"] = cause.retryAfterSeconds.toString()
                else -> {}
            }

            call.respond(
                cause.statusCode,
                ErrorResponse(
                    error = cause.message,
                    errorCode = cause.errorCode,
                    details = details.ifEmpty { null }
                )
            )
        }

        // Handle Circuit Breaker exceptions
        exception<CircuitBreakerOpenException> { call, cause ->
            logger.warn("CircuitBreakerOpen: ${cause.message}")

            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    error = cause.message ?: "Service temporarily unavailable",
                    errorCode = "CIRCUIT_BREAKER_OPEN",
                    details = mapOf(
                        "retryAfterMs" to cause.retryAfterMs.toString()
                    )
                )
            )
        }

        // Handle SQL exceptions
        exception<SQLException> { call, cause ->
            logger.error("SQLException: ${cause.sqlState} - ${cause.message}", cause)

            val (statusCode, errorCode, message) = categorizeSqlException(cause)

            call.respond(
                statusCode,
                ErrorResponse(
                    error = message,
                    errorCode = errorCode,
                    details = mapOf(
                        "sqlState" to (cause.sqlState ?: "unknown"),
                        "vendorCode" to cause.errorCode.toString()
                    )
                )
            )
        }

        // Handle IllegalStateException (often session errors)
        exception<IllegalStateException> { call, cause ->
            logger.warn("IllegalStateException: ${cause.message}")

            val isSessionError = cause.message?.contains("Session", ignoreCase = true) == true

            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = cause.message ?: "Invalid state",
                    errorCode = if (isSessionError) "SESSION_ERROR" else "INVALID_STATE"
                )
            )
        }

        // Handle IllegalArgumentException (validation errors)
        exception<IllegalArgumentException> { call, cause ->
            logger.warn("IllegalArgumentException: ${cause.message}")

            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = cause.message ?: "Invalid argument",
                    errorCode = "VALIDATION_ERROR"
                )
            )
        }

        // Handle all other exceptions
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception: ${cause.message}", cause)

            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = "An unexpected error occurred",
                    errorCode = "INTERNAL_ERROR"
                )
            )
        }
    }
}

/**
 * Categorizes SQL exceptions into appropriate HTTP status codes and error messages.
 */
private fun categorizeSqlException(e: SQLException): Triple<HttpStatusCode, String, String> {
    val sqlState = e.sqlState ?: ""
    val message = e.message ?: "Database error"
    val vendorCode = e.errorCode

    return when {
        // Connection errors (08xxx)
        sqlState.startsWith("08") -> Triple(
            HttpStatusCode.ServiceUnavailable,
            "DB_CONNECTION_ERROR",
            "Database connection failed"
        )

        // Authentication/Authorization errors
        sqlState == "28000" || sqlState == "28P01" -> Triple(
            HttpStatusCode.Unauthorized,
            "DB_AUTH_ERROR",
            "Database authentication failed"
        )

        // Insufficient privileges (42xxx)
        sqlState.startsWith("42") && message.contains("privilege", ignoreCase = true) -> Triple(
            HttpStatusCode.Forbidden,
            "DB_PERMISSION_ERROR",
            "Insufficient database privileges"
        )

        // Syntax error (42xxx)
        sqlState.startsWith("42") -> Triple(
            HttpStatusCode.BadRequest,
            "SQL_SYNTAX_ERROR",
            "SQL syntax error: ${extractRelevantMessage(message)}"
        )

        // Constraint violation (23xxx)
        sqlState.startsWith("23") -> Triple(
            HttpStatusCode.Conflict,
            "DB_CONSTRAINT_ERROR",
            "Database constraint violation: ${extractRelevantMessage(message)}"
        )

        // Data exception (22xxx)
        sqlState.startsWith("22") -> Triple(
            HttpStatusCode.BadRequest,
            "DB_DATA_ERROR",
            "Invalid data: ${extractRelevantMessage(message)}"
        )

        // Oracle-specific: User already exists (ORA-01920)
        vendorCode == 1920 -> Triple(
            HttpStatusCode.Conflict,
            "USER_EXISTS",
            "User already exists"
        )

        // Oracle-specific: User does not exist (ORA-01918)
        vendorCode == 1918 -> Triple(
            HttpStatusCode.NotFound,
            "USER_NOT_FOUND",
            "User does not exist"
        )

        // Oracle-specific: Invalid password (ORA-28003, ORA-28007)
        vendorCode in listOf(28003, 28007) -> Triple(
            HttpStatusCode.BadRequest,
            "INVALID_PASSWORD",
            "Password does not meet requirements"
        )

        // Oracle-specific: Account locked (ORA-28000)
        vendorCode == 28000 -> Triple(
            HttpStatusCode.Forbidden,
            "ACCOUNT_LOCKED",
            "Account is locked"
        )

        // MySQL-specific: Access denied (1045)
        vendorCode == 1045 -> Triple(
            HttpStatusCode.Unauthorized,
            "DB_AUTH_ERROR",
            "Access denied for user"
        )

        // MySQL-specific: User already exists (1396)
        vendorCode == 1396 -> Triple(
            HttpStatusCode.Conflict,
            "USER_EXISTS",
            "User already exists"
        )

        // Query timeout
        message.contains("timeout", ignoreCase = true) ||
        message.contains("cancelled", ignoreCase = true) -> Triple(
            HttpStatusCode.GatewayTimeout,
            "QUERY_TIMEOUT",
            "Query execution timed out"
        )

        // Default: Internal server error
        else -> Triple(
            HttpStatusCode.InternalServerError,
            "DB_ERROR",
            "Database error: ${extractRelevantMessage(message)}"
        )
    }
}

/**
 * Extracts a relevant, safe portion of the error message for the response.
 */
private fun extractRelevantMessage(message: String): String {
    // Limit message length and remove potentially sensitive info
    return message
        .take(200)
        .replace(Regex("password|pwd|secret", RegexOption.IGNORE_CASE), "***")
        .replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "[IP]")
}
