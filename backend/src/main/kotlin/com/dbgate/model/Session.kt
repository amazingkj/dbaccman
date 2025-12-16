package com.dbgate.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val pid: Long,
    val user: String,
    val host: String,
    val database: String?,
    val command: String,
    val time: Int,
    val state: String?,
    val query: String?
)

@Serializable
data class SessionStats(
    val totalSessions: Int,
    val activeSessions: Int,
    val sleepingSessions: Int,
    val longRunningSessions: Int
)
