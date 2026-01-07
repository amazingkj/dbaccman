package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class UserTable(
    val schemaName: String,
    val tableName: String,
    val rowCount: Long?,
    val lastAnalyzed: String?,
    val tablespaceName: String?
)

@Serializable
data class UserTablespace(
    val name: String,
    val maxBytes: String,
    val usedBytes: Long
)

@Serializable
data class UserTablespaceInfo(
    val quotas: List<UserTablespace>,
    val defaultTablespace: String?,
    val temporaryTablespace: String?
)

@Serializable
data class UserQueryResult(
    val columns: List<String>,
    val rows: List<List<String?>>,
    val rowCount: Int,
    val executionTimeMs: Long
)
