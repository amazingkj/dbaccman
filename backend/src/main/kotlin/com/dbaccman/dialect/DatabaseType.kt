package com.dbaccman.dialect

import kotlinx.serialization.Serializable

@Serializable
enum class DatabaseType {
    MYSQL,
    ORACLE,
    POSTGRESQL;

    companion object {
        fun fromString(value: String): DatabaseType {
            return when (value.uppercase()) {
                "MYSQL", "MARIADB" -> MYSQL
                "ORACLE" -> ORACLE
                "POSTGRESQL", "POSTGRES", "PG" -> POSTGRESQL
                else -> throw IllegalArgumentException("Unsupported database type: $value")
            }
        }
    }

    val displayName: String
        get() = when (this) {
            MYSQL -> "MySQL"
            ORACLE -> "Oracle"
            POSTGRESQL -> "PostgreSQL"
        }

    val defaultPort: Int
        get() = when (this) {
            MYSQL -> 3306
            ORACLE -> 1521
            POSTGRESQL -> 5432
        }
}