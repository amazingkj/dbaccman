package com.dbaccman.service

import com.dbaccman.config.useSessionConnection
import com.dbaccman.model.*
import com.dbaccman.util.AuditLogger

class TableService {

    fun getDatabases(sessionId: String): List<DatabaseInfo> {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    s.schema_name as name,
                    COUNT(t.table_name) as table_count,
                    IFNULL(SUM(t.data_length + t.index_length), 0) as size
                FROM information_schema.schemata s
                LEFT JOIN information_schema.tables t
                    ON s.schema_name = t.table_schema
                WHERE s.schema_name NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys')
                GROUP BY s.schema_name
                ORDER BY s.schema_name
            """.trimIndent()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val databases = mutableListOf<DatabaseInfo>()
                    while (rs.next()) {
                        databases.add(
                            DatabaseInfo(
                                name = rs.getString("name"),
                                tableCount = rs.getInt("table_count"),
                                size = rs.getLong("size")
                            )
                        )
                    }
                    databases
                }
            }
        }
    }

    fun getTables(sessionId: String, database: String): List<TableInfo> {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    table_name as name,
                    engine,
                    IFNULL(table_rows, 0) as `rows`,
                    IFNULL(data_length + index_length, 0) as size,
                    DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') as create_time
                FROM information_schema.tables
                WHERE table_schema = ?
                AND table_type = 'BASE TABLE'
                ORDER BY table_name
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, database)
                stmt.executeQuery().use { rs ->
                    val tables = mutableListOf<TableInfo>()
                    while (rs.next()) {
                        tables.add(
                            TableInfo(
                                name = rs.getString("name"),
                                engine = rs.getString("engine"),
                                rows = rs.getLong("rows"),
                                size = rs.getLong("size"),
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
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    column_name as name,
                    column_type as type,
                    is_nullable = 'YES' as nullable,
                    column_key as col_key,
                    column_default as default_value,
                    extra
                FROM information_schema.columns
                WHERE table_schema = ?
                AND table_name = ?
                ORDER BY ordinal_position
            """.trimIndent()

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
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    index_name as name,
                    GROUP_CONCAT(column_name ORDER BY seq_in_index) as columns,
                    NOT non_unique as is_unique,
                    index_type as type
                FROM information_schema.statistics
                WHERE table_schema = ?
                AND table_name = ?
                GROUP BY index_name, non_unique, index_type
                ORDER BY index_name
            """.trimIndent()

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
        useSessionConnection(sessionId) { conn ->
            val columns = request.columns.joinToString(", ") { quoteIdentifier(it) }
            val uniqueKeyword = if (request.unique) "UNIQUE" else ""

            val sql = """
                CREATE $uniqueKeyword INDEX ${quoteIdentifier(request.indexName)}
                ON ${quoteIdentifier(request.database)}.${quoteIdentifier(request.table)} ($columns)
            """.trimIndent()

            conn.createStatement().execute(sql)

            AuditLogger.log(
                "CREATE_INDEX",
                "Created index ${request.indexName} on ${request.database}.${request.table}"
            )
        }
    }

    fun dropIndex(sessionId: String, database: String, table: String, indexName: String) {
        useSessionConnection(sessionId) { conn ->
            val sql = "DROP INDEX ${quoteIdentifier(indexName)} ON ${quoteIdentifier(database)}.${quoteIdentifier(table)}"
            conn.createStatement().execute(sql)

            AuditLogger.log("DROP_INDEX", "Dropped index $indexName from $database.$table")
        }
    }

    private fun quoteIdentifier(identifier: String): String {
        return "`${identifier.replace("`", "``")}`"
    }
}
