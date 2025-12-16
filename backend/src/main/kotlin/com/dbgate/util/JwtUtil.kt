package com.dbgate.util

import com.auth0.jwt.JWT
import com.dbgate.config.JwtConfig
import java.util.*

object JwtUtil {
    fun generateToken(username: String, role: String = "user"): String {
        return JWT.create()
            .withAudience(JwtConfig.audience)
            .withIssuer(JwtConfig.issuer)
            .withClaim("username", username)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + JwtConfig.expirationMs))
            .sign(JwtConfig.algorithm)
    }

    fun validateToken(token: String): Boolean {
        return try {
            JwtConfig.verifier.verify(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getUsernameFromToken(token: String): String? {
        return try {
            val jwt = JwtConfig.verifier.verify(token)
            jwt.getClaim("username").asString()
        } catch (e: Exception) {
            null
        }
    }

    fun getRoleFromToken(token: String): String? {
        return try {
            val jwt = JwtConfig.verifier.verify(token)
            jwt.getClaim("role").asString()
        } catch (e: Exception) {
            null
        }
    }
}
