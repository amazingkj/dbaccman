package com.dbaccman.exception

import io.ktor.http.*

/**
 * Base exception class for application-specific errors.
 * Each exception type maps to a specific HTTP status code.
 */
sealed class AppException(
    override val message: String,
    val statusCode: HttpStatusCode,
    val errorCode: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * 400 Bad Request - Invalid input from user
 */
class ValidationException(
    message: String,
    val field: String? = null,
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.BadRequest, "VALIDATION_ERROR", cause)

/**
 * 401 Unauthorized - Authentication required or failed
 */
class AuthenticationException(
    message: String = "Authentication required",
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.Unauthorized, "AUTH_ERROR", cause)

/**
 * 403 Forbidden - User doesn't have permission
 */
class AuthorizationException(
    message: String = "Access denied",
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.Forbidden, "FORBIDDEN", cause)

/**
 * 404 Not Found - Resource doesn't exist
 */
class NotFoundException(
    message: String,
    val resourceType: String? = null,
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.NotFound, "NOT_FOUND", cause)

/**
 * 409 Conflict - Resource already exists or state conflict
 */
class ConflictException(
    message: String,
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.Conflict, "CONFLICT", cause)

/**
 * 422 Unprocessable Entity - Business logic error
 */
class BusinessException(
    message: String,
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.UnprocessableEntity, "BUSINESS_ERROR", cause)

/**
 * 503 Service Unavailable - Database connection failed
 */
class DatabaseConnectionException(
    message: String = "Database connection failed",
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.ServiceUnavailable, "DB_CONNECTION_ERROR", cause)

/**
 * 500 Internal Server Error - Unexpected database error
 */
class DatabaseException(
    message: String,
    val sqlState: String? = null,
    val vendorCode: Int? = null,
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.InternalServerError, "DB_ERROR", cause)

/**
 * 504 Gateway Timeout - Query timeout
 */
class QueryTimeoutException(
    message: String = "Query execution timed out",
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.GatewayTimeout, "QUERY_TIMEOUT", cause)

/**
 * 429 Too Many Requests - Rate limit exceeded
 */
class RateLimitException(
    message: String = "Too many requests. Please try again later.",
    val retryAfterSeconds: Int = 60,
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.TooManyRequests, "RATE_LIMIT", cause)

/**
 * 400 Bad Request - Session not found or expired
 */
class SessionException(
    message: String = "Session not found or expired",
    cause: Throwable? = null
) : AppException(message, HttpStatusCode.BadRequest, "SESSION_ERROR", cause)
