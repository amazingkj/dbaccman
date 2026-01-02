package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseInfo(
    val name: String,
    val tableCount: Int,
    val totalRows: Long,
    val size: Long
)

@Serializable
data class TableInfo(
    val name: String,
    val engine: String?,
    val rows: Long,
    val size: Long,
    val createTime: String?
)

@Serializable
data class IndexInfo(
    val name: String,
    val columns: List<String>,
    val unique: Boolean,
    val type: String
)

@Serializable
data class CreateIndexRequest(
    val database: String,
    val table: String,
    val indexName: String,
    val columns: List<String>,
    val unique: Boolean = false
)

@Serializable
data class ColumnInfo(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val key: String?,
    val defaultValue: String?,
    val extra: String?
)
