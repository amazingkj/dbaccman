package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val pid: Long,
    val serialNum: Long? = null,  // Oracle SERIAL# for kill session
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
