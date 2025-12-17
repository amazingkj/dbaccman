package com.dbaccman.service

import com.dbaccman.config.useSessionConnectionWithDialect
import com.dbaccman.model.TablespaceInfo
import com.dbaccman.model.CreateTablespaceRequest
import com.dbaccman.model.TableLocationRequest
import com.dbaccman.model.TableInfo
import com.dbaccman.util.AuditLogger

class TablespaceService {

    fun getTablespaces(sessionId: String): List<TablespaceInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getTablespacesQuery()

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
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getCreateTablespaceSql(request.name, request.dataFile, request.engine)

            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            AuditLogger.log("CREATE_TABLESPACE", "Created tablespace ${request.name}")
        }
    }

    fun dropTablespace(sessionId: String, name: String) {
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getDropTablespaceSql(name)

            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }

            AuditLogger.log("DROP_TABLESPACE", "Dropped tablespace $name")
        }
    }

    fun getTablesInTablespace(sessionId: String, tablespaceName: String): List<TableInfo> {
        return useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getTablesInTablespaceQuery()

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
        useSessionConnectionWithDialect(sessionId) { conn, dialect ->
            val sql = dialect.getMoveTableToTablespaceSql(request.database, request.tableName, request.tablespaceName)

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