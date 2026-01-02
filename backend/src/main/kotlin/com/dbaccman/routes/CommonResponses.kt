package com.dbaccman.routes

import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(
    val message: String
)

@Serializable
data class ApiErrorResponse(
    val error: String
)

@Serializable
data class UserInfoResponse(
    val username: String,
    val role: String,
    val host: String,
    val port: Int,
    val dbType: String
)

@Serializable
data class AvailablePrivilegesResponse(
    val all: List<String>,
    val readOnly: List<String>,
    val readWrite: List<String>,
    val ddl: List<String>
)

@Serializable
data class SwitchSchemaResponse(
    val success: Boolean,
    val schema: String
)
