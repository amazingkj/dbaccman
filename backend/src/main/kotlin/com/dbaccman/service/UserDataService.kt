package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.model.*
import com.dbaccman.util.AuditLogger
import org.slf4j.LoggerFactory

class UserDataService {
    private val logger = LoggerFactory.getLogger(UserDataService::class.java)

    /**
     * Get tables owned by the current user (USER_TABLES).
     */
    fun getMyTables(sessionId: String): List<UserTable> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            if (dialect !is OracleDialect) {
                throw UnsupportedOperationException("This feature is only available for Oracle databases")
            }

            val sql = dialect.getMyTablesQuery()
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val tables = mutableListOf<UserTable>()
                    while (rs.next()) {
                        tables.add(
                            UserTable(
                                schemaName = rs.getString("schema_name") ?: "",
                                tableName = rs.getString("table_name") ?: "",
                                rowCount = rs.getObject("row_count")?.let { (it as Number).toLong() },
                                lastAnalyzed = rs.getString("last_analyzed"),
                                tablespaceName = rs.getString("tablespace_name")
                            )
                        )
                    }
                    tables
                }
            }
        }
    }

    /**
     * Get columns for a table owned by the current user.
     */
    fun getMyTableColumns(sessionId: String, table: String): List<ColumnInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            if (dialect !is OracleDialect) {
                throw UnsupportedOperationException("This feature is only available for Oracle databases")
            }

            val sql = dialect.getMyTableColumnsQuery()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, table)
                stmt.executeQuery().use { rs ->
                    val columns = mutableListOf<ColumnInfo>()
                    while (rs.next()) {
                        columns.add(
                            ColumnInfo(
                                name = rs.getString("column_name") ?: "",
                                type = rs.getString("data_type") ?: "",
                                nullable = rs.getString("is_nullable") == "Y",
                                key = null,
                                defaultValue = rs.getString("column_default"),
                                extra = null
                            )
                        )
                    }
                    columns
                }
            }
        }
    }

    /**
     * Get indexes for a table owned by the current user.
     */
    fun getMyTableIndexes(sessionId: String, table: String): List<IndexInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            if (dialect !is OracleDialect) {
                throw UnsupportedOperationException("This feature is only available for Oracle databases")
            }

            val sql = dialect.getMyTableIndexesQuery()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, table)
                stmt.executeQuery().use { rs ->
                    val indexes = mutableListOf<IndexInfo>()
                    while (rs.next()) {
                        indexes.add(
                            IndexInfo(
                                name = rs.getString("index_name") ?: "",
                                type = rs.getString("index_type") ?: "",
                                unique = rs.getInt("is_unique") == 1,
                                columns = (rs.getString("columns") ?: "").split(", ")
                            )
                        )
                    }
                    indexes
                }
            }
        }
    }

    /**
     * Get table data for a table owned by the current user.
     * Only allows SELECT on USER_TABLES to prevent accessing other schemas.
     */
    fun getMyTableData(sessionId: String, table: String, limit: Int): Map<String, Any> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            // Validate table name to prevent SQL injection
            if (!table.matches(Regex("^[A-Za-z_][A-Za-z0-9_\$#]*$"))) {
                throw IllegalArgumentException("Invalid table name")
            }

            // Use USER_TABLES to verify the table exists in user's schema
            val checkSql = "SELECT 1 FROM USER_TABLES WHERE TABLE_NAME = UPPER(?)"
            val tableExists = conn.prepareStatement(checkSql).use { stmt ->
                stmt.setString(1, table)
                stmt.executeQuery().use { rs -> rs.next() }
            }

            if (!tableExists) {
                throw IllegalArgumentException("Table '$table' not found in your schema")
            }

            val sql = dialect.getSelectWithLimitSql(dialect.quoteIdentifier(table.uppercase()), limit.coerceIn(1, 1000))
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val metaData = rs.metaData
                    val columnCount = metaData.columnCount
                    val columns = (1..columnCount).map { metaData.getColumnLabel(it) }

                    val rows = mutableListOf<List<Any?>>()
                    while (rs.next()) {
                        val row = (1..columnCount).map { i ->
                            try {
                                rs.getObject(i)?.toString()
                            } catch (e: Exception) {
                                "[Error]"
                            }
                        }
                        rows.add(row)
                    }

                    mapOf(
                        "columns" to columns,
                        "rows" to rows,
                        "rowCount" to rows.size
                    )
                }
            }
        }
    }

    /**
     * Get tablespace quotas for the current user.
     */
    fun getMyTablespaces(sessionId: String): UserTablespaceInfo {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            if (dialect !is OracleDialect) {
                throw UnsupportedOperationException("This feature is only available for Oracle databases")
            }

            // Get tablespace quotas
            val quotaSql = dialect.getMyTablespacesQuery()
            val quotas = conn.createStatement().use { stmt ->
                stmt.executeQuery(quotaSql).use { rs ->
                    val list = mutableListOf<UserTablespace>()
                    while (rs.next()) {
                        list.add(
                            UserTablespace(
                                name = rs.getString("name") ?: "",
                                maxBytes = rs.getString("max_bytes") ?: "0",
                                usedBytes = rs.getLong("used_bytes")
                            )
                        )
                    }
                    list
                }
            }

            // Get default tablespace
            val defaultSql = dialect.getMyDefaultTablespaceQuery()
            val (defaultTs, tempTs) = conn.createStatement().use { stmt ->
                stmt.executeQuery(defaultSql).use { rs ->
                    if (rs.next()) {
                        Pair(rs.getString("DEFAULT_TABLESPACE"), rs.getString("TEMPORARY_TABLESPACE"))
                    } else {
                        Pair(null, null)
                    }
                }
            }

            UserTablespaceInfo(
                quotas = quotas,
                defaultTablespace = defaultTs,
                temporaryTablespace = tempTs
            )
        }
    }

    /**
     * Execute a query restricted to the user's own schema.
     * Users can execute any query they have permission for within their own schema.
     */
    fun executeUserQuery(sessionId: String, query: String, limit: Int): UserQueryResult {
        val startTime = System.currentTimeMillis()

        return useSessionConnectionWithDialect(sessionId) { conn, _ ->
            val trimmedQuery = query.trim()

            // Block queries that might access other schemas
            val upperQuery = trimmedQuery.uppercase()
            val dangerousPatterns = listOf(
                Regex("\\bDBA_\\w+"),           // DBA_* views
                Regex("\\bALL_\\w+"),           // ALL_* views (can see other schemas)
                Regex("\\bV\\$\\w+"),           // V$ views
                Regex("\\bGV\\$\\w+"),          // GV$ views
                Regex("\\b[A-Z_][A-Z0-9_]*\\.[A-Z_][A-Z0-9_]*"),  // schema.table patterns (excluding USER.column)
            )

            for (pattern in dangerousPatterns) {
                if (pattern.containsMatchIn(upperQuery)) {
                    throw IllegalArgumentException("Query contains restricted patterns. Use USER_* views to access your own schema data.")
                }
            }

            // Determine if this is a SELECT query
            val isSelect = upperQuery.startsWith("SELECT")

            if (isSelect) {
                // Execute SELECT query
                val rowLimit = limit.coerceIn(1, 5000)
                conn.createStatement().use { stmt ->
                    stmt.maxRows = rowLimit
                    stmt.executeQuery(trimmedQuery).use { rs ->
                        val metaData = rs.metaData
                        val columnCount = metaData.columnCount
                        val columns = (1..columnCount).map { metaData.getColumnLabel(it) }

                        val rows = mutableListOf<List<String?>>()
                        while (rs.next() && rows.size < rowLimit) {
                            val row = (1..columnCount).map { i ->
                                try {
                                    rs.getObject(i)?.toString()
                                } catch (e: Exception) {
                                    "[Error]"
                                }
                            }
                            rows.add(row)
                        }

                        val executionTime = System.currentTimeMillis() - startTime

                        AuditLogger.logQuery(
                            user = "user",  // TODO: Get actual username from session
                            query = trimmedQuery.take(500),
                            database = null,
                            ipAddress = null,
                            success = true
                        )

                        UserQueryResult(
                            columns = columns,
                            rows = rows,
                            rowCount = rows.size,
                            executionTimeMs = executionTime
                        )
                    }
                }
            } else {
                // Execute DML (INSERT, UPDATE, DELETE) or DDL
                conn.createStatement().use { stmt ->
                    val affectedRows = stmt.executeUpdate(trimmedQuery)
                    val executionTime = System.currentTimeMillis() - startTime

                    AuditLogger.logQuery(
                        user = "user",  // TODO: Get actual username from session
                        query = trimmedQuery.take(500),
                        database = null,
                        ipAddress = null,
                        success = true
                    )

                    UserQueryResult(
                        columns = listOf("Affected Rows"),
                        rows = listOf(listOf(affectedRows.toString())),
                        rowCount = affectedRows,
                        executionTimeMs = executionTime
                    )
                }
            }
        }
    }
}
