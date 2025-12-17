package com.dbaccman.model

import com.dbaccman.dialect.DatabaseType
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionLoginRequest(
    val host: String,
    val port: Int? = null,  // null means use default port for dbType
    val username: String,
    val password: String,
    val dbType: DatabaseType = DatabaseType.MYSQL,
    val database: String? = null  // Optional initial database
) {
    fun getEffectivePort(): Int = port ?: dbType.defaultPort
}

@Serializable
data class ConnectionLoginResponse(
    val token: String,
    val username: String,
    val role: String,
    val host: String,
    val port: Int,
    val dbType: DatabaseType,
    val passwordExpiryDays: Int? = null
)

@Serializable
data class ConnectionInfo(
    val host: String,
    val port: Int,
    val username: String,
    val dbType: DatabaseType
)

@Serializable
data class PasswordExpiryInfo(
    val username: String,
    val host: String,
    val daysUntilExpiry: Int?,
    val passwordLastChanged: String?,
    val isExpired: Boolean
)