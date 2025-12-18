package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.util.AuditLogger

data class QueryResult(
    val columns: List<String>,
    val rows: List<List<Any?>>,
    val rowCount: Int,
    val executionTimeMs: Long,
    val affectedRows: Int? = null,
    val isSelectQuery: Boolean = true
)

class QueryService {

    companion object {
        // Maximum rows to return to prevent memory issues
        private const val MAX_ROWS = 1000

        // Dangerous operations that are not allowed
        private val DANGEROUS_PATTERNS = listOf(
            Regex("^\\s*DROP\\s+DATABASE", RegexOption.IGNORE_CASE),
            Regex("^\\s*DROP\\s+SCHEMA", RegexOption.IGNORE_CASE),
            Regex("^\\s*TRUNCATE", RegexOption.IGNORE_CASE),
            Regex("^\\s*DELETE\\s+FROM\\s+mysql\\.", RegexOption.IGNORE_CASE),
            Regex("^\\s*DELETE\\s+FROM\\s+pg_", RegexOption.IGNORE_CASE),
            Regex("^\\s*DELETE\\s+FROM\\s+sys\\.", RegexOption.IGNORE_CASE),
            Regex("^\\s*ALTER\\s+SYSTEM", RegexOption.IGNORE_CASE),
            Regex("^\\s*SHUTDOWN", RegexOption.IGNORE_CASE)
        )
    }

    fun executeQuery(
        sessionId: String,
        query: String,
        database: String?,
        username: String,
        ipAddress: String?
    ): QueryResult {
        // Validate query
        validateQuery(query)

        return useSessionConnectionWithDialect(sessionId) { conn, _ ->
            val startTime = System.currentTimeMillis()

            // Set database/schema if provided
            if (!database.isNullOrBlank()) {
                try {
                    conn.schema = database
                } catch (e: Exception) {
                    // Some databases don't support schema setting this way
                    try {
                        conn.createStatement().use { stmt ->
                            stmt.execute("USE $database")
                        }
                    } catch (e2: Exception) {
                        // Ignore if USE command also fails
                    }
                }
            }

            val trimmedQuery = query.trim()
            val isSelect = isSelectQuery(trimmedQuery)

            try {
                if (isSelect) {
                    conn.createStatement().use { stmt ->
                        stmt.maxRows = MAX_ROWS
                        stmt.executeQuery(trimmedQuery).use { rs ->
                            val metaData = rs.metaData
                            val columnCount = metaData.columnCount

                            val columns = (1..columnCount).map { metaData.getColumnLabel(it) }
                            val rows = mutableListOf<List<Any?>>()

                            while (rs.next() && rows.size < MAX_ROWS) {
                                val row = (1..columnCount).map { i ->
                                    try {
                                        rs.getObject(i)?.toString()
                                    } catch (e: Exception) {
                                        "[Error reading column]"
                                    }
                                }
                                rows.add(row)
                            }

                            val executionTime = System.currentTimeMillis() - startTime

                            AuditLogger.logQuery(
                                user = username,
                                query = trimmedQuery,
                                database = database,
                                ipAddress = ipAddress,
                                success = true
                            )

                            QueryResult(
                                columns = columns,
                                rows = rows,
                                rowCount = rows.size,
                                executionTimeMs = executionTime,
                                isSelectQuery = true
                            )
                        }
                    }
                } else {
                    // Non-SELECT query (INSERT, UPDATE, DELETE, etc.)
                    conn.createStatement().use { stmt ->
                        val affectedRows = stmt.executeUpdate(trimmedQuery)
                        val executionTime = System.currentTimeMillis() - startTime

                        AuditLogger.logQuery(
                            user = username,
                            query = trimmedQuery,
                            database = database,
                            ipAddress = ipAddress,
                            success = true
                        )

                        QueryResult(
                            columns = listOf("Affected Rows"),
                            rows = listOf(listOf(affectedRows)),
                            rowCount = 1,
                            executionTimeMs = executionTime,
                            affectedRows = affectedRows,
                            isSelectQuery = false
                        )
                    }
                }
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime

                AuditLogger.logQuery(
                    user = username,
                    query = trimmedQuery,
                    database = database,
                    ipAddress = ipAddress,
                    success = false,
                    error = e.message?.take(200)
                )

                throw QueryExecutionException(
                    message = e.message ?: "Query execution failed",
                    executionTimeMs = executionTime
                )
            }
        }
    }

    private fun validateQuery(query: String) {
        val trimmedQuery = query.trim()

        if (trimmedQuery.isBlank()) {
            throw IllegalArgumentException("Query cannot be empty")
        }

        if (trimmedQuery.length > 10000) {
            throw IllegalArgumentException("Query too long (max 10000 characters)")
        }

        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(trimmedQuery)) {
                throw IllegalArgumentException("This operation is not allowed for safety reasons")
            }
        }
    }

    private fun isSelectQuery(query: String): Boolean {
        val upperQuery = query.uppercase().trim()
        return upperQuery.startsWith("SELECT") ||
               upperQuery.startsWith("SHOW") ||
               upperQuery.startsWith("DESCRIBE") ||
               upperQuery.startsWith("DESC") ||
               upperQuery.startsWith("EXPLAIN") ||
               upperQuery.startsWith("WITH")  // CTE that typically ends with SELECT
    }
}

class QueryExecutionException(
    message: String,
    val executionTimeMs: Long
) : RuntimeException(message)