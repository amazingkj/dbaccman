package com.dbaccman.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Serializable
data class AuditLogEntry(
    val timestamp: String,
    val action: String,
    val user: String?,
    val ipAddress: String?,
    val message: String,
    val target: String? = null,
    val details: String? = null,
    val success: Boolean = true
)

object AuditLogger {
    private val logger = LoggerFactory.getLogger("com.dbaccman.util.AuditLogger")
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val json = Json { prettyPrint = false }

    // In-memory log storage (최근 1000개 유지)
    private val logEntries = ConcurrentLinkedDeque<AuditLogEntry>()
    private const val MAX_LOG_ENTRIES = 1000

    // File persistence
    private val logDir = File("logs/audit")
    private var currentLogFile: File? = null
    private var currentLogDate: LocalDate? = null
    private var fileWriter: PrintWriter? = null
    private val fileLock = ReentrantLock()
    private const val MAX_FILE_SIZE = 10 * 1024 * 1024L  // 10MB per file
    private const val MAX_LOG_FILES = 30  // Keep 30 days of logs

    init {
        initLogDirectory()
    }

    private fun initLogDirectory() {
        try {
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            rotateLogFileIfNeeded()
        } catch (e: Exception) {
            logger.error("Failed to initialize audit log directory", e)
        }
    }

    private fun rotateLogFileIfNeeded() {
        fileLock.withLock {
            val today = LocalDate.now()

            // Check if we need a new file (new day or file too large)
            val needsRotation = currentLogDate != today ||
                (currentLogFile?.length() ?: 0) > MAX_FILE_SIZE

            if (needsRotation) {
                // Close existing writer
                fileWriter?.close()
                fileWriter = null

                // Create new log file
                currentLogDate = today
                val dateStr = today.format(dateFormatter)
                var suffix = 0
                var logFile: File

                do {
                    val suffixStr = if (suffix == 0) "" else "-$suffix"
                    logFile = File(logDir, "audit-$dateStr$suffixStr.jsonl")
                    suffix++
                } while (logFile.exists() && logFile.length() > MAX_FILE_SIZE)

                currentLogFile = logFile
                fileWriter = PrintWriter(FileWriter(logFile, true), true)

                // Cleanup old log files
                cleanupOldLogFiles()
            }
        }
    }

    private fun cleanupOldLogFiles() {
        try {
            val cutoffDate = LocalDate.now().minusDays(MAX_LOG_FILES.toLong())
            logDir.listFiles()?.filter { file ->
                file.name.startsWith("audit-") && file.name.endsWith(".jsonl")
            }?.forEach { file ->
                try {
                    val dateStr = file.name.removePrefix("audit-").take(10)
                    val fileDate = LocalDate.parse(dateStr, dateFormatter)
                    if (fileDate.isBefore(cutoffDate)) {
                        file.delete()
                        logger.info("Deleted old audit log: ${file.name}")
                    }
                } catch (_: Exception) {
                    // Skip files with invalid names
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to cleanup old audit logs", e)
        }
    }

    private fun writeToFile(entry: AuditLogEntry) {
        try {
            rotateLogFileIfNeeded()
            fileLock.withLock {
                fileWriter?.println(json.encodeToString(entry))
            }
        } catch (e: Exception) {
            logger.error("Failed to write audit log to file", e)
        }
    }

    fun log(action: String, message: String, user: String? = null, ipAddress: String? = null, success: Boolean = true) {
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

        val entry = AuditLogEntry(
            timestamp = timestamp,
            action = action,
            user = user,
            ipAddress = ipAddress,
            message = message,
            success = success
        )

        // Store in memory and file
        addLogEntry(entry)
        writeToFile(entry)
    }

    fun logWithTarget(action: String, user: String, target: String, details: String? = null, ipAddress: String? = null, success: Boolean = true) {
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

        val entry = AuditLogEntry(
            timestamp = timestamp,
            action = action,
            user = user,
            ipAddress = ipAddress,
            message = "",
            target = target,
            details = details,
            success = success
        )

        // Store in memory and file
        addLogEntry(entry)
        writeToFile(entry)
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

        val entry = AuditLogEntry(
            timestamp = timestamp,
            action = "QUERY_EXECUTE",
            user = user,
            ipAddress = ipAddress,
            message = query.take(200),
            target = database,
            details = if (success) "SUCCESS" else "FAILED: $error",
            success = success
        )

        // Store in memory and file
        addLogEntry(entry)
        writeToFile(entry)
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

    /**
     * Shutdown the audit logger, closing file handles.
     */
    fun shutdown() {
        fileLock.withLock {
            fileWriter?.close()
            fileWriter = null
        }
        logger.info("AuditLogger shutdown complete")
    }
}