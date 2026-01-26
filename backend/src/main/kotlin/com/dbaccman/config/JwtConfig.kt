package com.dbaccman.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("JwtConfig")

// Default secret that should NOT be used in production
private const val DEFAULT_JWT_SECRET = "dbaccman-jwt-secret-key-change-in-production"

object JwtConfig {
    lateinit var secret: String
    lateinit var issuer: String
    lateinit var audience: String
    lateinit var realm: String
    var expirationMs: Long = 86400000L // 24 hours

    fun init(
        secret: String,
        issuer: String,
        audience: String,
        realm: String,
        expirationMs: Long
    ) {
        this.secret = secret
        this.issuer = issuer
        this.audience = audience
        this.realm = realm
        this.expirationMs = expirationMs
    }

    val algorithm: Algorithm by lazy {
        Algorithm.HMAC256(secret)
    }

    val verifier by lazy {
        JWT.require(algorithm)
            .withAudience(audience)
            .withIssuer(issuer)
            .build()
    }
}

fun Application.configureJwt() {
    val config = environment.config
    val jwtSecret = config.property("jwt.secret").getString()

    // Validate JWT secret - warn if using default in production
    if (jwtSecret == DEFAULT_JWT_SECRET) {
        logger.warn("=" .repeat(80))
        logger.warn("SECURITY WARNING: Using default JWT secret!")
        logger.warn("This is insecure for production environments.")
        logger.warn("Set JWT_SECRET environment variable to a secure random string.")
        logger.warn("=" .repeat(80))
    } else if (jwtSecret.length < 32) {
        logger.warn("JWT secret is shorter than 32 characters. Consider using a longer secret.")
    }

    JwtConfig.init(
        secret = jwtSecret,
        issuer = config.property("jwt.issuer").getString(),
        audience = config.property("jwt.audience").getString(),
        realm = config.property("jwt.realm").getString(),
        expirationMs = config.property("jwt.expirationMs").getString().toLong()
    )

    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.realm
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            // Extract JWT from cookie or Authorization header
            authHeader { call ->
                // First try to get token from httpOnly cookie
                val cookieToken = call.request.cookies["auth_token"]
                // Fallback to Authorization header for backward compatibility
                val headerToken = call.request.headers["Authorization"]?.removePrefix("Bearer ")

                val token = cookieToken ?: headerToken
                token?.let { io.ktor.http.auth.HttpAuthHeader.Single("Bearer", it) }
            }
        }
    }
}
