package com.dbaccman.util

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.DatabaseType
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.sql.Connection

/**
 * Extension function to get session ID from JWT principal.
 */
fun ApplicationCall.getSessionId(): String {
    val principal = principal<JWTPrincipal>()
        ?: throw IllegalStateException("No JWT principal found")
    return principal.payload.getClaim("sessionId").asString()
        ?: throw IllegalStateException("No session ID in token")
}

/**
 * Extension function to get username from JWT principal.
 */
fun ApplicationCall.getUsername(): String {
    val principal = principal<JWTPrincipal>()
        ?: throw IllegalStateException("No JWT principal found")
    return principal.payload.getClaim("username").asString()
        ?: throw IllegalStateException("No username in token")
}

/**
 * Extension function to get role from JWT principal.
 */
fun ApplicationCall.getRole(): String {
    val principal = principal<JWTPrincipal>()
        ?: throw IllegalStateException("No JWT principal found")
    return principal.payload.getClaim("role").asString()
        ?: throw IllegalStateException("No role in token")
}

/**
 * Extension function to get database host from JWT principal.
 */
fun ApplicationCall.getDbHost(): String {
    val principal = principal<JWTPrincipal>()
        ?: throw IllegalStateException("No JWT principal found")
    return principal.payload.getClaim("dbHost").asString()
        ?: throw IllegalStateException("No dbHost in token")
}

/**
 * Extension function to get database port from JWT principal.
 */
fun ApplicationCall.getDbPort(): Int {
    val principal = principal<JWTPrincipal>()
        ?: throw IllegalStateException("No JWT principal found")
    return principal.payload.getClaim("dbPort").asInt()
        ?: throw IllegalStateException("No dbPort in token")
}

/**
 * Extension function to get database type from JWT principal.
 */
fun ApplicationCall.getDbType(): DatabaseType {
    val principal = principal<JWTPrincipal>()
        ?: throw IllegalStateException("No JWT principal found")
    val dbTypeName = principal.payload.getClaim("dbType").asString()
        ?: return DatabaseType.MYSQL  // Default for backward compatibility
    return DatabaseType.valueOf(dbTypeName)
}

/**
 * Extension function to check if the current user is admin.
 */
fun ApplicationCall.isAdmin(): Boolean {
    return getRole() == "admin"
}

/**
 * Extension function to get the dialect for the current session.
 */
fun ApplicationCall.getDialect(): DatabaseDialect {
    return SessionConnectionManager.getDialect(getSessionId())
}

/**
 * Extension function to get a connection using the session ID from JWT.
 */
fun ApplicationCall.getConnection(): Connection {
    val sessionId = getSessionId()
    return SessionConnectionManager.getConnection(sessionId)
}

/**
 * Extension function to execute a block with the session's connection.
 */
inline fun <T> ApplicationCall.useConnection(block: (Connection) -> T): T {
    return getConnection().use(block)
}

/**
 * Extension function to execute a block with the session's connection and dialect.
 */
inline fun <T> ApplicationCall.useConnectionWithDialect(block: (Connection, DatabaseDialect) -> T): T {
    val dialect = getDialect()
    return getConnection().use { conn -> block(conn, dialect) }
}