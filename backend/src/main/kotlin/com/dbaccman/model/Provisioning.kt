package com.dbaccman.model

import kotlinx.serialization.Serializable

/**
 * Oracle-specific provisioning options.
 * Mirrors the standard operational procedure:
 *   data tablespace + (optional) index tablespace + temp tablespace
 *   -> user with default/temp tablespace -> role grants -> unlimited quotas
 */
@Serializable
data class OracleProvisionOptions(
    val dataTablespace: String,
    val dataFilePath: String,
    val dataSize: String = "8G",
    val indexTablespace: String? = null,
    val indexFilePath: String? = null,
    val indexSize: String = "1G",
    val tempTablespace: String,
    val tempFilePath: String,
    val tempSize: String = "256M",
    val autoExtend: Boolean = false,
    val roles: List<String> = listOf("CONNECT", "RESOURCE"),
    val quota: String = "UNLIMITED",
    val profile: String? = null
)

/**
 * PostgreSQL-specific provisioning options.
 * Mirrors: create user (LOGIN + options) -> create tablespace (owner, location)
 *   -> create database (owner, encoding, tablespace, connection limit)
 */
@Serializable
data class PostgresProvisionOptions(
    val tablespace: String,
    val location: String,
    val createDb: Boolean = true,
    val createRole: Boolean = false,
    val replication: Boolean = false,
    val databaseName: String? = null,
    val encoding: String = "UTF8",
    val connectionLimit: Int = -1
)

/**
 * MySQL-specific provisioning options.
 * Mirrors: create user -> create database -> grant all on database.
 */
@Serializable
data class MySqlProvisionOptions(
    val host: String = "%",
    val databaseName: String? = null,
    val charset: String = "utf8mb4",
    val grantAllOnDatabase: Boolean = true
)

@Serializable
data class ProvisionRequest(
    val username: String,
    val password: String,
    val oracle: OracleProvisionOptions? = null,
    val postgres: PostgresProvisionOptions? = null,
    val mysql: MySqlProvisionOptions? = null
)

/**
 * A single step of a provisioning plan. The SQL is masked (passwords hidden)
 * and safe to display to the user.
 */
@Serializable
data class ProvisionStep(
    val order: Int,
    val title: String,
    val description: String,
    val sql: String
)

@Serializable
data class ProvisionPlan(
    val dbType: String,
    val steps: List<ProvisionStep>
)

@Serializable
data class ProvisionStepResult(
    val order: Int,
    val title: String,
    val sql: String,
    val status: String, // SUCCESS, FAILED, SKIPPED
    val error: String? = null,
    val durationMs: Long = 0
)

@Serializable
data class ProvisionResult(
    val success: Boolean,
    val steps: List<ProvisionStepResult>
)