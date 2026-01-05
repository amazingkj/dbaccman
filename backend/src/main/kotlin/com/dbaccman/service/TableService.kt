package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.model.*
import com.dbaccman.util.AuditLogger
import kotlinx.serialization.Serializable

class TableService {

    fun getDatabases(sessionId: String): List<DatabaseInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getDatabasesQuery()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val databases = mutableListOf<DatabaseInfo>()
                    while (rs.next()) {
                        databases.add(
                            DatabaseInfo(
                                name = rs.getString("name"),
                                tableCount = rs.getInt("table_count"),
                                totalRows = rs.getLong("total_rows"),
                                size = rs.getLong("total_size")
                            )
                        )
                    }
                    databases
                }
            }
        }
    }

    fun getTables(sessionId: String, database: String): List<TableInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getTablesQuery()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, database)
                stmt.executeQuery().use { rs ->
                    val tables = mutableListOf<TableInfo>()
                    while (rs.next()) {
                        tables.add(
                            TableInfo(
                                name = rs.getString("name"),
                                engine = rs.getString("engine"),
                                rows = rs.getLong("row_count"),
                                size = rs.getLong("table_size"),
                                createTime = rs.getString("create_time")
                            )
                        )
                    }
                    tables
                }
            }
        }
    }

    fun getTableColumns(sessionId: String, database: String, table: String): List<ColumnInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getTableColumnsQuery()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, database)
                stmt.setString(2, table)
                stmt.executeQuery().use { rs ->
                    val columns = mutableListOf<ColumnInfo>()
                    while (rs.next()) {
                        columns.add(
                            ColumnInfo(
                                name = rs.getString("name"),
                                type = rs.getString("type"),
                                nullable = rs.getBoolean("nullable"),
                                key = rs.getString("col_key")?.ifEmpty { null },
                                defaultValue = rs.getString("default_value"),
                                extra = rs.getString("extra")?.ifEmpty { null }
                            )
                        )
                    }
                    columns
                }
            }
        }
    }

    fun getIndexes(sessionId: String, database: String, table: String): List<IndexInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getIndexesQuery()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, database)
                stmt.setString(2, table)
                stmt.executeQuery().use { rs ->
                    val indexes = mutableListOf<IndexInfo>()
                    while (rs.next()) {
                        indexes.add(
                            IndexInfo(
                                name = rs.getString("name"),
                                columns = rs.getString("columns").split(","),
                                unique = rs.getBoolean("is_unique"),
                                type = rs.getString("type")
                            )
                        )
                    }
                    indexes
                }
            }
        }
    }

    fun createIndex(sessionId: String, request: CreateIndexRequest) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getCreateIndexSql(
                database = request.database,
                table = request.table,
                indexName = request.indexName,
                columns = request.columns,
                unique = request.unique
            )

            conn.createStatement().execute(sql)

            AuditLogger.log(
                "CREATE_INDEX",
                "Created index ${request.indexName} on ${request.database}.${request.table}"
            )
        }
    }

    fun dropIndex(sessionId: String, database: String, table: String, indexName: String) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getDropIndexSql(database, table, indexName)
            conn.createStatement().execute(sql)

            AuditLogger.log("DROP_INDEX", "Dropped index $indexName from $database.$table")
        }
    }

    fun getTableData(sessionId: String, database: String, table: String, limit: Int): TableDataResult {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            // Switch to schema if needed
            val switchSql = dialect.getSwitchSchemaSql(database)
            if (switchSql != null) {
                conn.createStatement().use { stmt ->
                    stmt.execute(switchSql)
                }
            }

            // Build SELECT query with proper identifier quoting
            val quotedTable = dialect.quoteIdentifier(table)
            val sql = dialect.getSelectWithLimitSql(quotedTable, limit)

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val metaData = rs.metaData
                    val columnCount = metaData.columnCount

                    val columns = (1..columnCount).map { metaData.getColumnLabel(it) }
                    val rows = mutableListOf<List<String?>>()

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

                    TableDataResult(
                        columns = columns,
                        rows = rows,
                        rowCount = rows.size
                    )
                }
            }
        }
    }

    fun gatherStats(sessionId: String, schema: String, table: String? = null): Boolean {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getGatherStatsSql(schema, table)
            if (sql != null) {
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }
                AuditLogger.log(
                    "GATHER_STATS",
                    if (table != null) "Gathered statistics for table $schema.$table"
                    else "Gathered statistics for schema $schema"
                )
                true
            } else {
                false
            }
        }
    }
}

@Serializable
data class TableDataResult(
    val columns: List<String>,
    val rows: List<List<String?>>,
    val rowCount: Int
)