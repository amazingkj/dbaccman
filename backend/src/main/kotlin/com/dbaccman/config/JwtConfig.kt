package com.dbaccman.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

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

    JwtConfig.init(
        secret = config.property("jwt.secret").getString(),
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
        }
    }
}
