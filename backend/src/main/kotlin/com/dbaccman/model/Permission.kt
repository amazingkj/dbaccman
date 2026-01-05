package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class Permission(
    val grantee: String,
    val database: String,
    val table: String = "*",
    val privilege: String,
    val isGrantable: Boolean = false
)

@Serializable
data class GrantPermissionRequest(
    val username: String,
    val host: String = "%",
    val database: String = "",  // Empty for system privileges
    val table: String = "*",
    val privileges: List<String>
)

@Serializable
data class RevokePermissionRequest(
    val username: String,
    val host: String = "%",
    val database: String = "",  // Empty for system privileges
    val table: String = "*",
    val privileges: List<String>
)

object MySQLPrivileges {
    val ALL = listOf(
        "SELECT", "INSERT", "UPDATE", "DELETE",
        "CREATE", "DROP", "INDEX", "ALTER",
        "CREATE VIEW", "SHOW VIEW",
        "CREATE ROUTINE", "ALTER ROUTINE", "EXECUTE",
        "TRIGGER", "REFERENCES"
    )

    val READ_ONLY = listOf("SELECT")

    val READ_WRITE = listOf("SELECT", "INSERT", "UPDATE", "DELETE")

    val DDL = listOf("CREATE", "DROP", "INDEX", "ALTER")
}
