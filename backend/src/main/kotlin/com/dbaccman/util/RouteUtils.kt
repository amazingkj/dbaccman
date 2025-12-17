package com.dbaccman.util

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Exception thrown when admin access is required but not available.
 */
class AdminAccessRequiredException : Exception("Admin access required")

/**
 * Exception thrown when session has expired.
 */
class SessionExpiredException : Exception("Session expired")

/**
 * Requires admin access, throws AdminAccessRequiredException if not admin.
 */
fun ApplicationCall.requireAdmin() {
    if (!isAdmin()) {
        throw AdminAccessRequiredException()
    }
}

/**
 * Standard error response structure.
 */
data class ErrorResponse(val error: String)

/**
 * Handles the common pattern of try-catch with error responses for admin routes.
 */
suspend inline fun ApplicationCall.handleAdminRoute(
    logger: Logger? = null,
    errorMessage: String = "Operation failed",
    crossinline block: suspend () -> Unit
) {
    try {
        requireAdmin()
        block()
    } catch (e: AdminAccessRequiredException) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
    } catch (e: SessionExpiredException) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Session expired"))
    } catch (e: IllegalStateException) {
        logger?.error(errorMessage, e)
        respond(HttpStatusCode.Unauthorized, mapOf("error" to (e.message ?: "Authentication error")))
    } catch (e: Exception) {
        logger?.error(errorMessage, e)
        respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: errorMessage)))
    }
}

/**
 * Handles the common pattern of try-catch with error responses for authenticated routes.
 */
suspend inline fun ApplicationCall.handleAuthenticatedRoute(
    logger: Logger? = null,
    errorMessage: String = "Operation failed",
    crossinline block: suspend () -> Unit
) {
    try {
        block()
    } catch (e: SessionExpiredException) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Session expired"))
    } catch (e: IllegalStateException) {
        logger?.error(errorMessage, e)
        respond(HttpStatusCode.Unauthorized, mapOf("error" to (e.message ?: "Authentication error")))
    } catch (e: Exception) {
        logger?.error(errorMessage, e)
        respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: errorMessage)))
    }
}

/**
 * Handles routes that may return BadRequest on failure (e.g., create/update operations).
 */
suspend inline fun ApplicationCall.handleAdminMutationRoute(
    logger: Logger? = null,
    errorMessage: String = "Operation failed",
    crossinline block: suspend () -> Unit
) {
    try {
        requireAdmin()
        block()
    } catch (e: AdminAccessRequiredException) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
    } catch (e: SessionExpiredException) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Session expired"))
    } catch (e: IllegalStateException) {
        logger?.error(errorMessage, e)
        respond(HttpStatusCode.Unauthorized, mapOf("error" to (e.message ?: "Authentication error")))
    } catch (e: Exception) {
        logger?.error(errorMessage, e)
        respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: errorMessage)))
    }
}