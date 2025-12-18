package com.dbaccman.util

import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedDeque

data class AuditLogEntry(
    val timestamp: String,
    val action: String,
    val user: String?,
    val ipAddress: String?,
    val message: String,
    val target: String? = null,
    val details: String? = null
)

object AuditLogger {
    private val logger = LoggerFactory.getLogger("com.dbaccman.util.AuditLogger")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    // In-memory log storage (최근 1000개 유지)
    private val logEntries = ConcurrentLinkedDeque<AuditLogEntry>()
    private const val MAX_LOG_ENTRIES = 1000

    fun log(action: String, message: String, user: String? = null, ipAddress: String? = null) {
        val timestamp = LocalDateTime.now().format(formatter)
        val logMessage = buildString {
            append("ACTION=$action")
            if (user != null) {
                append(" USER=$user")
            }
            if (ipAddress != null) {
                append(" IP=$ipAddress")
            }
            append(" $message")
        }
        logger.info(logMessage)

        // Store in memory
        addLogEntry(AuditLogEntry(
            timestamp = timestamp,
            action = action,
            user = user,
            ipAddress = ipAddress,
            message = message
        ))
    }

    fun logWithTarget(action: String, user: String, target: String, details: String? = null, ipAddress: String? = null) {
        val timestamp = LocalDateTime.now().format(formatter)
        val logMessage = buildString {
            append("ACTION=$action USER=$user TARGET=$target")
            if (ipAddress != null) {
                append(" IP=$ipAddress")
            }
            if (details != null) {
                append(" DETAILS=$details")
            }
        }
        logger.info(logMessage)

        // Store in memory
        addLogEntry(AuditLogEntry(
            timestamp = timestamp,
            action = action,
            user = user,
            ipAddress = ipAddress,
            message = "",
            target = target,
            details = details
        ))
    }

    fun logQuery(user: String, query: String, database: String?, ipAddress: String?, success: Boolean, error: String? = null) {
        val timestamp = LocalDateTime.now().format(formatter)
        val status = if (success) "SUCCESS" else "FAILED"
        val logMessage = buildString {
            append("ACTION=QUERY_EXECUTE USER=$user STATUS=$status")
            if (ipAddress != null) {
                append(" IP=$ipAddress")
            }
            if (database != null) {
                append(" DB=$database")
            }
            append(" QUERY=${query.take(200)}")
            if (error != null) {
                append(" ERROR=$error")
            }
        }
        logger.info(logMessage)

        // Store in memory
        addLogEntry(AuditLogEntry(
            timestamp = timestamp,
            action = "QUERY_EXECUTE",
            user = user,
            ipAddress = ipAddress,
            message = query.take(200),
            target = database,
            details = if (success) "SUCCESS" else "FAILED: $error"
        ))
    }

    private fun addLogEntry(entry: AuditLogEntry) {
        logEntries.addFirst(entry)
        // Keep only the most recent entries
        while (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.removeLast()
        }
    }

    fun getRecentLogs(limit: Int = 100, action: String? = null, user: String? = null): List<AuditLogEntry> {
        return logEntries
            .filter { entry ->
                (action == null || entry.action == action) &&
                (user == null || entry.user?.contains(user, ignoreCase = true) == true)
            }
            .take(limit)
    }

    fun clearLogs() {
        logEntries.clear()
    }
}