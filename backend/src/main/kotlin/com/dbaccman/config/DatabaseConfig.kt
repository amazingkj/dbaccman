package com.dbaccman.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import java.sql.Connection

object DatabaseConfig {
    private lateinit var dataSource: HikariDataSource

    fun init(
        url: String,
        user: String,
        password: String,
        driver: String,
        maxPoolSize: Int
    ) {
        val config = HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            driverClassName = driver
            maximumPoolSize = maxPoolSize
            isAutoCommit = true
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }
        dataSource = HikariDataSource(config)
    }

    fun getConnection(): Connection = dataSource.connection

    fun close() {
        if (::dataSource.isInitialized) {
            dataSource.close()
        }
    }
}

fun Application.configureDatabase() {
    val config = environment.config

    DatabaseConfig.init(
        url = config.property("database.url").getString(),
        user = config.property("database.user").getString(),
        password = config.property("database.password").getString(),
        driver = config.property("database.driver").getString(),
        maxPoolSize = config.property("database.maxPoolSize").getString().toInt()
    )

    environment.monitor.subscribe(ApplicationStopped) {
        DatabaseConfig.close()
    }
}

inline fun <T> useConnection(block: (Connection) -> T): T {
    return DatabaseConfig.getConnection().use { conn ->
        block(conn)
    }
}
