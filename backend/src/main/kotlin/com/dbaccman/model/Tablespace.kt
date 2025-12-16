package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class TablespaceInfo(
    val name: String,
    val spaceType: String,
    val fileSize: Long,
    val allocatedSize: Long,
    val state: String,
    val filePath: String? = null
)

@Serializable
data class CreateTablespaceRequest(
    val name: String,
    val dataFile: String? = null,
    val engine: String = "InnoDB"
)

@Serializable
data class TableLocationRequest(
    val database: String,
    val tableName: String,
    val tablespaceName: String
)
