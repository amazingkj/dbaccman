package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val username: String,
    val host: String,
    val created: String? = null,
    val passwordLastChanged: String? = null,
    val passwordLifetime: Int? = null,
    val accountLocked: Boolean = false
)

@Serializable
data class CreateAccountRequest(
    val username: String,
    val host: String = "%",
    val password: String,
    val expireDays: Int = 90
)

@Serializable
data class ChangePasswordRequest(
    val password: String,
    val expireImmediately: Boolean = false
)

@Serializable
data class ExpiringAccount(
    val username: String,
    val host: String,
    val daysUntilExpiry: Int
)

@Serializable
data class SetTablespaceRequest(
    val username: String,
    val host: String = "%",
    val tablespace: String,
    val quota: String? = null  // e.g., "UNLIMITED", "100M", "1G"
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val username: String,
    val role: String
)

@Serializable
data class UserInfo(
    val username: String,
    val role: String
)
