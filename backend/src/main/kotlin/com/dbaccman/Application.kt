package com.dbaccman

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.config.configureJwt
import com.dbaccman.routes.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.io.File

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Unknown error"))
            )
        }
    }

    configureJwt()

    // Shutdown hook to cleanup all session connections
    environment.monitor.subscribe(ApplicationStopped) {
        SessionConnectionManager.shutdown()
    }

    routing {
        route("/api") {
            authRoutes()
            accountRoutes()
            permissionRoutes()
            sessionRoutes()
            tableRoutes()
            tablespaceRoutes()
            queryRoutes()
            dashboardRoutes()
        }

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        // Serve static files (React frontend) from 'static' directory
        staticFiles("/", File("static")) {
            default("index.html")
        }
    }
}
