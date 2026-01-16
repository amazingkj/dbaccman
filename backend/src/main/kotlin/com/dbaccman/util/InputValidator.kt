package com.dbaccman.util

import com.dbaccman.exception.ValidationException

/**
 * Input validation utility for database operations.
 * Prevents SQL injection by validating identifiers and privileges.
 */
object InputValidator {

    // Valid identifier pattern: alphanumeric, underscore, dollar sign
    // Also allows C## prefix for Oracle CDB users
    private val VALID_IDENTIFIER_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_\$#]*$")

    // Host pattern: IP, hostname, or %
    private val VALID_HOST_PATTERN = Regex("^[a-zA-Z0-9._%-]+$")

    // Oracle C## prefix for common users
    private val ORACLE_COMMON_USER_PATTERN = Regex("^C##[a-zA-Z0-9_\$#]+$", RegexOption.IGNORE_CASE)

    // Standard SQL privileges
    private val VALID_PRIVILEGES = setOf(
        // DML privileges
        "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE",
        // DDL privileges
        "CREATE", "DROP", "ALTER", "INDEX", "REFERENCES",
        "CREATE VIEW", "SHOW VIEW", "CREATE ROUTINE", "ALTER ROUTINE",
        "CREATE TEMPORARY TABLES", "LOCK TABLES",
        // Execute privileges
        "EXECUTE", "TRIGGER", "EVENT",
        // Admin privileges
        "GRANT OPTION", "ALL", "ALL PRIVILEGES",
        "SUPER", "PROCESS", "FILE", "SHOW DATABASES",
        "RELOAD", "SHUTDOWN",
        "REPLICATION SLAVE", "REPLICATION CLIENT",
        "CREATE USER", "CREATE TABLESPACE",
        // Oracle specific
        "CONNECT", "RESOURCE", "DBA",
        "CREATE SESSION", "CREATE TABLE", "CREATE SEQUENCE",
        "CREATE PROCEDURE", "CREATE TRIGGER", "CREATE TYPE",
        "CREATE ANY TABLE", "DROP ANY TABLE",
        "SELECT ANY TABLE", "INSERT ANY TABLE", "UPDATE ANY TABLE", "DELETE ANY TABLE",
        "CREATE ANY INDEX", "DROP ANY INDEX",
        "CREATE ANY VIEW", "DROP ANY VIEW",
        "CREATE ANY PROCEDURE", "DROP ANY PROCEDURE", "EXECUTE ANY PROCEDURE",
        "CREATE ANY SEQUENCE", "DROP ANY SEQUENCE", "SELECT ANY SEQUENCE",
        "CREATE ANY TRIGGER", "DROP ANY TRIGGER",
        "CREATE PUBLIC SYNONYM", "DROP PUBLIC SYNONYM",
        "UNLIMITED TABLESPACE", "ALTER SESSION",
        "CREATE SYNONYM", "CREATE DATABASE LINK",
        "ALTER USER", "DROP USER",
        // PostgreSQL specific
        "USAGE", "CONNECT", "TEMPORARY", "TEMP",
        "CREATE SCHEMA", "TRUNCATE"
    )

    /**
     * Validates a database identifier (username, database name, table name, etc.)
     * @throws ValidationException if invalid
     */
    fun validateIdentifier(identifier: String, fieldName: String = "identifier") {
        if (identifier.isBlank()) {
            throw ValidationException("$fieldName cannot be empty", fieldName)
        }

        if (identifier.length > 128) {
            throw ValidationException("$fieldName exceeds maximum length of 128 characters", fieldName)
        }

        // Allow Oracle C## prefix
        if (ORACLE_COMMON_USER_PATTERN.matches(identifier)) {
            return
        }

        if (!VALID_IDENTIFIER_PATTERN.matches(identifier)) {
            throw ValidationException(
                "$fieldName contains invalid characters. Only alphanumeric characters, underscores, and dollar signs are allowed.",
                fieldName
            )
        }
    }

    /**
     * Validates a host specification.
     * @throws ValidationException if invalid
     */
    fun validateHost(host: String) {
        if (host.isBlank()) {
            throw ValidationException("Host cannot be empty", "host")
        }

        if (host.length > 255) {
            throw ValidationException("Host exceeds maximum length of 255 characters", "host")
        }

        if (!VALID_HOST_PATTERN.matches(host)) {
            throw ValidationException(
                "Host contains invalid characters. Only alphanumeric characters, dots, underscores, hyphens, and % wildcard are allowed.",
                "host"
            )
        }
    }

    /**
     * Validates a list of SQL privileges.
     * @throws ValidationException if any privilege is invalid
     */
    fun validatePrivileges(privileges: List<String>) {
        if (privileges.isEmpty()) {
            throw ValidationException("At least one privilege must be specified", "privileges")
        }

        privileges.forEach { privilege ->
            val normalizedPrivilege = privilege.uppercase().trim()
            if (normalizedPrivilege !in VALID_PRIVILEGES) {
                throw ValidationException(
                    "Invalid privilege: $privilege. Only standard SQL privileges are allowed.",
                    "privileges"
                )
            }
        }
    }

    /**
     * Validates a database name, allowing wildcards.
     * @throws ValidationException if invalid
     */
    fun validateDatabaseName(database: String, allowWildcard: Boolean = true) {
        if (database.isBlank()) {
            return // Empty database is allowed for global privileges
        }

        if (allowWildcard && database == "*") {
            return
        }

        validateIdentifier(database, "database")
    }

    /**
     * Validates a table name, allowing wildcards.
     * @throws ValidationException if invalid
     */
    fun validateTableName(table: String, allowWildcard: Boolean = true) {
        if (table.isBlank()) {
            return
        }

        if (allowWildcard && table == "*") {
            return
        }

        validateIdentifier(table, "table")
    }

    /**
     * Validates password strength.
     * @throws ValidationException if password is weak
     */
    fun validatePassword(password: String) {
        if (password.length < 8) {
            throw ValidationException(
                "Password must be at least 8 characters long",
                "password"
            )
        }

        if (password.length > 128) {
            throw ValidationException(
                "Password exceeds maximum length of 128 characters",
                "password"
            )
        }

        // Check for at least one letter and one digit
        if (!password.any { it.isLetter() }) {
            throw ValidationException(
                "Password must contain at least one letter",
                "password"
            )
        }

        if (!password.any { it.isDigit() }) {
            throw ValidationException(
                "Password must contain at least one digit",
                "password"
            )
        }
    }

    /**
     * Sanitizes a string for logging (removes sensitive data patterns).
     */
    fun sanitizeForLogging(input: String): String {
        return input
            .replace(Regex("password\\s*=\\s*'[^']*'", RegexOption.IGNORE_CASE), "password='***'")
            .replace(Regex("IDENTIFIED\\s+BY\\s+'[^']*'", RegexOption.IGNORE_CASE), "IDENTIFIED BY '***'")
            .replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "[IP]")
    }

    /**
     * Validates a quota string (e.g., "100M", "1G", "UNLIMITED").
     * @throws ValidationException if invalid
     */
    fun validateQuota(quota: String) {
        if (quota.isBlank()) {
            throw ValidationException("Quota cannot be empty", "quota")
        }

        val normalizedQuota = quota.uppercase().trim()

        if (normalizedQuota == "UNLIMITED") {
            return
        }

        // Pattern: number followed by optional K/M/G/T suffix
        val quotaPattern = Regex("^\\d+[KMGT]?$")
        if (!quotaPattern.matches(normalizedQuota)) {
            throw ValidationException(
                "Invalid quota format. Use a number followed by K, M, G, or T (e.g., '100M', '1G') or 'UNLIMITED'.",
                "quota"
            )
        }
    }

    /**
     * Validates expire days.
     * @throws ValidationException if invalid
     */
    fun validateExpireDays(days: Int) {
        if (days < 0) {
            throw ValidationException("Expire days cannot be negative", "expireDays")
        }

        if (days > 3650) {
            throw ValidationException("Expire days cannot exceed 3650 (10 years)", "expireDays")
        }
    }
}
