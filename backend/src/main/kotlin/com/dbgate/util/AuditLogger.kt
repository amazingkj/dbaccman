package com.dbgate.util

import org.slf4j.LoggerFactory

object AuditLogger {
    private val logger = LoggerFactory.getLogger("com.dbgate.util.AuditLogger")

    fun log(action: String, message: String, user: String? = null) {
        val logMessage = buildString {
            append("ACTION=$action")
            if (user != null) {
                append(" USER=$user")
            }
            append(" $message")
        }
        logger.info(logMessage)
    }

    fun logWithTarget(action: String, user: String, target: String, details: String? = null) {
        val logMessage = buildString {
            append("ACTION=$action USER=$user TARGET=$target")
            if (details != null) {
                append(" DETAILS=$details")
            }
        }
        logger.info(logMessage)
    }
}
