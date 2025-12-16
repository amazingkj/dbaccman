package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionLoginRequest(
    val host: String,
    val port: Int = 3306,
    val username: String,
    val password: String
)

@Serializable
data class ConnectionLoginResponse(
    val token: String,
    val username: String,
    val role: String,
    val host: String,
    val port: Int,
    val passwordExpiryDays: Int? = null
)

@Serializable
data class ConnectionInfo(
    val host: String,
    val port: Int,
    val username: String
)

@Serializable
data class PasswordExpiryInfo(
    val username: String,
    val host: String,
    val daysUntilExpiry: Int?,
    val passwordLastChanged: String?,
    val isExpired: Boolean
)
