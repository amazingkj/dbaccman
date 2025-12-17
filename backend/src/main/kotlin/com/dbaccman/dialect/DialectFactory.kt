package com.dbaccman.dialect

/**
 * Factory for creating database dialect instances based on database type.
 */
object DialectFactory {

    private val dialects = mutableMapOf<DatabaseType, DatabaseDialect>()

    init {
        register(MySQLDialect())
        register(OracleDialect())
        register(PostgreSQLDialect())
    }

    private fun register(dialect: DatabaseDialect) {
        dialects[dialect.type] = dialect
    }

    /**
     * Gets the dialect for the specified database type.
     * @throws IllegalArgumentException if the database type is not supported
     */
    fun getDialect(type: DatabaseType): DatabaseDialect {
        return dialects[type]
            ?: throw IllegalArgumentException("Unsupported database type: ${type.displayName}")
    }

    /**
     * Checks if a database type is supported.
     */
    fun isSupported(type: DatabaseType): Boolean {
        return dialects.containsKey(type)
    }

    /**
     * Gets all supported database types.
     */
    fun getSupportedTypes(): List<DatabaseType> {
        return dialects.keys.toList()
    }
}