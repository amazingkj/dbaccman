package com.dbaccman

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.config.configureJwt
import com.dbaccman.exception.configureErrorHandling
import com.dbaccman.routes.*
import com.dbaccman.util.AuditLogger
import com.dbaccman.util.CsrfUtil
import com.dbaccman.util.configureRateLimiting
import com.dbaccman.websocket.EventBroadcaster
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.websocket.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // gzip 압축 활성화 (응답 크기 60-80% 감소)
    install(Compression) {
        gzip {
            priority = 1.0
            minimumSize(1024) // 1KB 이상만 압축
        }
        deflate {
            priority = 0.9
            minimumSize(1024)
        }
    }

    // WebSocket support for real-time updates
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(30)
        timeout = Duration.ofSeconds(60)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // Prometheus metrics
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics(),
            ClassLoaderMetrics(),
            UptimeMetrics()
        )
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-CSRF-Token")  // For CSRF protection
        allowCredentials = true  // Required for cookies

        // Allow specific origins (configure for production)
        val allowedOrigins = listOf(
            "localhost:5173",      // Vite dev server
            "localhost:3000",      // Alternative dev
            "localhost:12081",     // Production port
            "127.0.0.1:5173",
            "127.0.0.1:3000",
            "127.0.0.1:12081"
        )
        allowedOrigins.forEach { host ->
            allowHost(host, schemes = listOf("http", "https"))
        }
    }

    // Centralized error handling
    configureErrorHandling()

    // Rate limiting
    configureRateLimiting()

    // CSRF Protection middleware
    install(createApplicationPlugin("CsrfProtection") {
        onCall { call ->
            val method = call.request.httpMethod
            val path = call.request.path()

            // Only validate on state-changing requests
            if (method == HttpMethod.Post || method == HttpMethod.Put || method == HttpMethod.Delete) {
                // Skip CSRF for login endpoint (before authentication)
                if (path.endsWith("/auth/login")) {
                    return@onCall
                }

                // Skip CSRF for logout (needs to work even with expired token)
                if (path.endsWith("/auth/logout")) {
                    return@onCall
                }

                val csrfToken = call.request.header("X-CSRF-Token")
                if (!CsrfUtil.validateToken(csrfToken)) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Invalid or missing CSRF token"))
                    return@onCall
                }
            }
        }
    })

    configureJwt()

    // Start WebSocket heartbeat
    EventBroadcaster.startHeartbeat(this)

    // Shutdown hook to cleanup all session connections, audit logger, and WebSocket
    environment.monitor.subscribe(ApplicationStopped) {
        runBlocking {
            EventBroadcaster.shutdown()
        }
        SessionConnectionManager.shutdown()
        AuditLogger.shutdown()
    }

    routing {
        route("/api") {
            authRoutes()
            accountRoutes()
            permissionRoutes()
            sessionRoutes()
            tableRoutes()
            tablespaceRoutes()
            provisioningRoutes()
            queryRoutes()
            dashboardRoutes()
            roleRoutes()
            userRoutes()  // User-specific routes (own schema only)
            webSocketRoutes()  // Real-time WebSocket routes
            monitoringRoutes()  // System monitoring routes
        }

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        // Prometheus metrics endpoint
        get("/metrics") {
            call.respond(appMicrometerRegistry.scrape())
        }

        // Swagger UI for API documentation
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")

        // Serve static files (React frontend) from 'static' directory
        staticFiles("/", File("static")) {
            default("index.html")
        }
    }
}
