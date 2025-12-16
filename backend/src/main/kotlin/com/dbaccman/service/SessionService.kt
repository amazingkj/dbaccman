package com.dbaccman.service

import com.dbaccman.config.useSessionConnection
import com.dbaccman.model.SessionInfo
import com.dbaccman.model.SessionStats
import com.dbaccman.util.AuditLogger

class SessionService {

    fun getActiveSessions(sessionId: String): List<SessionInfo> {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    id as pid,
                    user,
                    host,
                    db as database_name,
                    command,
                    time,
                    state,
                    info as query
                FROM information_schema.processlist
                ORDER BY time DESC
            """.trimIndent()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val sessions = mutableListOf<SessionInfo>()
                    while (rs.next()) {
                        sessions.add(
                            SessionInfo(
                                pid = rs.getLong("pid"),
                                user = rs.getString("user") ?: "",
                                host = rs.getString("host") ?: "",
                                database = rs.getString("database_name"),
                                command = rs.getString("command") ?: "",
                                time = rs.getInt("time"),
                                state = rs.getString("state"),
                                query = rs.getString("query")?.take(500) // Truncate long queries
                            )
                        )
                    }
                    sessions
                }
            }
        }
    }

    fun getSessionStats(sessionId: String): SessionStats {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    COUNT(*) as total,
                    SUM(CASE WHEN command != 'Sleep' THEN 1 ELSE 0 END) as active,
                    SUM(CASE WHEN command = 'Sleep' THEN 1 ELSE 0 END) as sleeping,
                    SUM(CASE WHEN time > 60 AND command != 'Sleep' THEN 1 ELSE 0 END) as long_running
                FROM information_schema.processlist
            """.trimIndent()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    if (rs.next()) {
                        SessionStats(
                            totalSessions = rs.getInt("total"),
                            activeSessions = rs.getInt("active"),
                            sleepingSessions = rs.getInt("sleeping"),
                            longRunningSessions = rs.getInt("long_running")
                        )
                    } else {
                        SessionStats(0, 0, 0, 0)
                    }
                }
            }
        }
    }

    fun killSession(sessionId: String, pid: Long) {
        useSessionConnection(sessionId) { conn ->
            val sql = "KILL ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, pid)
                stmt.execute()
            }

            AuditLogger.log("KILL_SESSION", "Killed session with PID: $pid")
        }
    }

    fun killQuery(sessionId: String, pid: Long) {
        useSessionConnection(sessionId) { conn ->
            val sql = "KILL QUERY ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, pid)
                stmt.execute()
            }

            AuditLogger.log("KILL_QUERY", "Killed query for session with PID: $pid")
        }
    }

    fun getLongRunningQueries(sessionId: String, thresholdSeconds: Int = 60): List<SessionInfo> {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    id as pid,
                    user,
                    host,
                    db as database_name,
                    command,
                    time,
                    state,
                    info as query
                FROM information_schema.processlist
                WHERE command != 'Sleep'
                AND time > ?
                ORDER BY time DESC
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, thresholdSeconds)
                stmt.executeQuery().use { rs ->
                    val sessions = mutableListOf<SessionInfo>()
                    while (rs.next()) {
                        sessions.add(
                            SessionInfo(
                                pid = rs.getLong("pid"),
                                user = rs.getString("user") ?: "",
                                host = rs.getString("host") ?: "",
                                database = rs.getString("database_name"),
                                command = rs.getString("command") ?: "",
                                time = rs.getInt("time"),
                                state = rs.getString("state"),
                                query = rs.getString("query")?.take(500)
                            )
                        )
                    }
                    sessions
                }
            }
        }
    }
}
