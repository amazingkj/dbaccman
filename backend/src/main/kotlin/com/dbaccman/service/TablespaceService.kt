package com.dbaccman.service

import com.dbaccman.config.useSessionConnection
import com.dbaccman.model.TablespaceInfo
import com.dbaccman.model.CreateTablespaceRequest
import com.dbaccman.model.TableLocationRequest
import com.dbaccman.model.TableInfo
import com.dbaccman.util.AuditLogger

class TablespaceService {

    fun getTablespaces(sessionId: String): List<TablespaceInfo> {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    NAME as name,
                    SPACE_TYPE as space_type,
                    FILE_SIZE as file_size,
                    ALLOCATED_SIZE as allocated_size,
                    STATE as state
                FROM information_schema.INNODB_TABLESPACES
                ORDER BY NAME
            """.trimIndent()

            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    val tablespaces = mutableListOf<TablespaceInfo>()
                    while (rs.next()) {
                        tablespaces.add(
                            TablespaceInfo(
                                name = rs.getString("name"),
                                spaceType = rs.getString("space_type"),
                                fileSize = rs.getLong("file_size"),
                                allocatedSize = rs.getLong("allocated_size"),
                                state = rs.getString("state")
                            )
                        )
                    }
                    tablespaces
                }
            }
        }
    }

    fun createTablespace(sessionId: String, request: CreateTablespaceRequest) {
        useSessionConnection(sessionId) { conn ->
            val dataFile = request.dataFile ?: "${request.name}.ibd"
            val sql = "CREATE TABLESPACE `${request.name}` ADD DATAFILE '$dataFile' ENGINE=${request.engine}"

            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            AuditLogger.log("CREATE_TABLESPACE", "Created tablespace ${request.name}")
        }
    }

    fun dropTablespace(sessionId: String, name: String) {
        useSessionConnection(sessionId) { conn ->
            val sql = "DROP TABLESPACE `$name`"

            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            AuditLogger.log("DROP_TABLESPACE", "Dropped tablespace $name")
        }
    }

    fun getTablesInTablespace(sessionId: String, tablespaceName: String): List<TableInfo> {
        return useSessionConnection(sessionId) { conn ->
            val sql = """
                SELECT
                    t.TABLE_SCHEMA as db_name,
                    t.TABLE_NAME as table_name,
                    t.ENGINE as engine,
                    IFNULL(t.TABLE_ROWS, 0) as `rows`,
                    IFNULL(t.DATA_LENGTH + t.INDEX_LENGTH, 0) as size,
                    DATE_FORMAT(t.CREATE_TIME, '%Y-%m-%d %H:%i:%s') as create_time
                FROM information_schema.TABLES t
                JOIN information_schema.INNODB_TABLES it
                    ON CONCAT(t.TABLE_SCHEMA, '/', t.TABLE_NAME) = it.NAME
                JOIN information_schema.INNODB_TABLESPACES ts
                    ON it.SPACE = ts.SPACE
                WHERE ts.NAME = ?
                AND t.TABLE_TYPE = 'BASE TABLE'
                ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, tablespaceName)
                stmt.executeQuery().use { rs ->
                    val tables = mutableListOf<TableInfo>()
                    while (rs.next()) {
                        tables.add(
                            TableInfo(
                                name = "${rs.getString("db_name")}.${rs.getString("table_name")}",
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

    fun moveTableToTablespace(sessionId: String, request: TableLocationRequest) {
        useSessionConnection(sessionId) { conn ->
            val sql = "ALTER TABLE `${request.database}`.`${request.tableName}` TABLESPACE = `${request.tablespaceName}`"

            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            AuditLogger.log(
                "MOVE_TABLE",
                "Moved table ${request.database}.${request.tableName} to tablespace ${request.tablespaceName}"
            )
        }
    }
}
