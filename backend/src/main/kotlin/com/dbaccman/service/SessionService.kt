package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.model.SessionInfo
import com.dbaccman.model.SessionStats
import com.dbaccman.util.AuditLogger

class SessionService {

    fun getActiveSessions(sessionId: String): List<SessionInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getActiveSessionsQuery()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val sessions = mutableListOf<SessionInfo>()
                    val metaData = rs.metaData
                    val hasSerialNum = (1..metaData.columnCount).any {
                        metaData.getColumnLabel(it).equals("serial_num", ignoreCase = true)
                    }
                    while (rs.next()) {
                        sessions.add(
                            SessionInfo(
                                pid = rs.getLong("pid"),
                                serialNum = if (hasSerialNum) rs.getLong("serial_num").takeIf { !rs.wasNull() } else null,
                                user = rs.getString("sess_user") ?: "",
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

    fun getSessionStats(sessionId: String): SessionStats {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getSessionStatsQuery()

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

    fun killSession(sessionId: String, pid: Long, serialNum: Long? = null) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getKillSessionSql(pid, serialNum)
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            AuditLogger.log("KILL_SESSION", "Killed session with PID: $pid" + (serialNum?.let { ", SERIAL#: $it" } ?: ""))
        }
    }

    fun killQuery(sessionId: String, pid: Long, serialNum: Long? = null) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getKillQuerySql(pid, serialNum)
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            AuditLogger.log("KILL_QUERY", "Killed query for session with PID: $pid" + (serialNum?.let { ", SERIAL#: $it" } ?: ""))
        }
    }

    fun getLongRunningQueries(sessionId: String, thresholdSeconds: Int = 60): List<SessionInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getLongRunningQueriesQuery()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, thresholdSeconds)
                stmt.executeQuery().use { rs ->
                    val sessions = mutableListOf<SessionInfo>()
                    val metaData = rs.metaData
                    val hasSerialNum = (1..metaData.columnCount).any {
                        metaData.getColumnLabel(it).equals("serial_num", ignoreCase = true)
                    }
                    while (rs.next()) {
                        sessions.add(
                            SessionInfo(
                                pid = rs.getLong("pid"),
                                serialNum = if (hasSerialNum) rs.getLong("serial_num").takeIf { !rs.wasNull() } else null,
                                user = rs.getString("sess_user") ?: "",
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