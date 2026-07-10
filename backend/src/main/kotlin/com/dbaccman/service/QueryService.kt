package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.util.AuditLogger

data class ColumnMeta(
    val name: String,
    val type: String,
    val isAutoIncrement: Boolean = false,
    val isNullable: Boolean = true,
    val isPrimaryKey: Boolean = false
)

data class QueryResult(
    val columns: List<String>,
    val columnMetadata: List<ColumnMeta>? = null,
    val rows: List<List<Any?>>,
    val rowCount: Int,
    val executionTimeMs: Long,
    val affectedRows: Int? = null,
    val isSelectQuery: Boolean = true
)

class QueryService {

    companion object {
        // Default and maximum rows to return
        private const val DEFAULT_ROWS = 1000
        private const val MAX_ROWS = 50000

        // Query timeout in seconds (5 minutes default)
        private const val DEFAULT_QUERY_TIMEOUT_SECONDS = 300
        private const val MAX_QUERY_TIMEOUT_SECONDS = 600  // 10 minutes max

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
        account: String?,
        username: String,
        ipAddress: String?,
        limit: Int? = null,
        timeoutSeconds: Int? = null
    ): QueryResult {
        val rowLimit = (limit ?: DEFAULT_ROWS).coerceIn(1, MAX_ROWS)
        val queryTimeout = (timeoutSeconds ?: DEFAULT_QUERY_TIMEOUT_SECONDS).coerceIn(1, MAX_QUERY_TIMEOUT_SECONDS)
        // Validate the full query first (length, emptiness)
        validateQuery(query)

        // Split statements and validate each for dangerous patterns up front,
        // before borrowing a connection or switching schema - fail fast
        // without any database interaction
        val statements = splitStatements(query)

        if (statements.isEmpty()) {
            throw IllegalArgumentException("No valid SQL statements found")
        }

        statements.forEach { stmt -> validateStatementPatterns(stmt) }

        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val startTime = System.currentTimeMillis()

            // Switch to account's schema if provided
            if (!account.isNullOrBlank()) {
                val switchSql = dialect.getSwitchSchemaSql(account)
                if (switchSql != null) {
                    try {
                        conn.createStatement().use { stmt ->
                            stmt.execute(switchSql)
                        }
                    } catch (e: Exception) {
                        throw QueryExecutionException(
                            message = "Failed to switch to schema '$account': ${e.message}",
                            executionTimeMs = System.currentTimeMillis() - startTime
                        )
                    }
                }
            }

            try {
                // If single statement, execute normally
                if (statements.size == 1) {
                    val trimmedQuery = statements[0]
                    val isSelect = isSelectQuery(trimmedQuery)

                    if (isSelect) {
                        conn.createStatement().use { stmt ->
                            stmt.queryTimeout = queryTimeout
                            stmt.maxRows = rowLimit
                            stmt.executeQuery(trimmedQuery).use { rs ->
                                val metaData = rs.metaData
                                val columnCount = metaData.columnCount

                                val columns = (1..columnCount).map { metaData.getColumnLabel(it) }

                                // Extract column metadata
                                val columnMeta = (1..columnCount).map { i ->
                                    val isAutoIncrement = try { metaData.isAutoIncrement(i) } catch (_: Exception) { false }
                                    val isNullable = try {
                                        metaData.isNullable(i) != java.sql.ResultSetMetaData.columnNoNulls
                                    } catch (_: Exception) { true }
                                    val typeName = try { metaData.getColumnTypeName(i) } catch (_: Exception) { "UNKNOWN" }

                                    ColumnMeta(
                                        name = metaData.getColumnLabel(i),
                                        type = typeName,
                                        isAutoIncrement = isAutoIncrement,
                                        isNullable = isNullable,
                                        isPrimaryKey = isAutoIncrement // Auto-increment columns are typically PKs
                                    )
                                }

                                val rows = mutableListOf<List<Any?>>()

                                while (rs.next() && rows.size < rowLimit) {
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
                                    database = account,
                                    ipAddress = ipAddress,
                                    success = true
                                )

                                QueryResult(
                                    columns = columns,
                                    columnMetadata = columnMeta,
                                    rows = rows,
                                    rowCount = rows.size,
                                    executionTimeMs = executionTime,
                                    isSelectQuery = true
                                )
                            }
                        }
                    } else {
                        conn.createStatement().use { stmt ->
                            stmt.queryTimeout = queryTimeout
                            val affectedRows = stmt.executeUpdate(trimmedQuery)
                            val executionTime = System.currentTimeMillis() - startTime

                            AuditLogger.logQuery(
                                user = username,
                                query = trimmedQuery,
                                database = account,
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
                } else {
                    // Multiple statements - execute each and return summary
                    var totalAffectedRows = 0
                    val results = mutableListOf<String>()

                    conn.autoCommit = false
                    try {
                        for ((index, stmt) in statements.withIndex()) {
                            val isSelect = isSelectQuery(stmt)
                            if (isSelect) {
                                conn.createStatement().use { s ->
                                    s.queryTimeout = queryTimeout
                                    s.maxRows = rowLimit
                                    s.executeQuery(stmt).use { rs ->
                                        var rowCount = 0
                                        while (rs.next()) rowCount++
                                        results.add("Statement ${index + 1}: SELECT returned $rowCount rows")
                                    }
                                }
                            } else {
                                conn.createStatement().use { s ->
                                    s.queryTimeout = queryTimeout
                                    val affected = s.executeUpdate(stmt)
                                    totalAffectedRows += affected
                                    results.add("Statement ${index + 1}: $affected rows affected")
                                }
                            }
                        }
                        conn.commit()
                    } catch (e: Exception) {
                        conn.rollback()
                        throw e
                    } finally {
                        conn.autoCommit = true
                    }

                    val executionTime = System.currentTimeMillis() - startTime

                    AuditLogger.logQuery(
                        user = username,
                        query = "${statements.size} statements executed",
                        database = account,
                        ipAddress = ipAddress,
                        success = true
                    )

                    QueryResult(
                        columns = listOf("Result"),
                        rows = results.map { listOf(it) },
                        rowCount = results.size,
                        executionTimeMs = executionTime,
                        affectedRows = totalAffectedRows,
                        isSelectQuery = false
                    )
                }
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime

                AuditLogger.logQuery(
                    user = username,
                    query = query.take(500),
                    database = account,
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
    }

    private fun validateStatementPatterns(statement: String) {
        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(statement)) {
                throw IllegalArgumentException("This operation is not allowed for safety reasons")
            }
        }
    }

    private fun isSelectQuery(query: String): Boolean {
        val upperQuery = query.uppercase().trim()
        if (upperQuery.startsWith("SELECT") ||
            upperQuery.startsWith("SHOW") ||
            upperQuery.startsWith("DESCRIBE") ||
            upperQuery.startsWith("DESC") ||
            upperQuery.startsWith("EXPLAIN")) {
            return true
        }
        // CTE: WITH ... SELECT is read-only, but WITH ... INSERT/UPDATE/DELETE is not
        if (upperQuery.startsWith("WITH")) {
            // Find the main statement after the CTE(s) by matching the last closing paren
            val afterCte = upperQuery.replace(Regex("\\s+"), " ")
            // Check if it contains DML keywords after the CTE definition
            val dmlPattern = Regex("\\)\\s*(INSERT|UPDATE|DELETE|MERGE)\\s")
            return !dmlPattern.containsMatchIn(afterCte)
        }
        return false
    }

    /**
     * Split SQL statements by semicolon, respecting quoted strings.
     */
    private fun splitStatements(query: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0

        while (i < query.length) {
            val c = query[i]

            when {
                c == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                    current.append(c)
                }
                c == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                    current.append(c)
                }
                c == ';' && !inSingleQuote && !inDoubleQuote -> {
                    val stmt = current.toString().trim()
                    if (stmt.isNotBlank()) {
                        statements.add(stmt)
                    }
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }

        // Add last statement if any
        val lastStmt = current.toString().trim()
        if (lastStmt.isNotBlank()) {
            statements.add(lastStmt)
        }

        return statements
    }
}

class QueryExecutionException(
    message: String,
    val executionTimeMs: Long
) : RuntimeException(message)