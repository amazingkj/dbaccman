package com.dbaccman.util

import com.auth0.jwt.JWT
import com.dbaccman.config.JwtConfig
import com.dbaccman.dialect.DatabaseType
import java.util.*

object JwtUtil {
    fun generateToken(
        username: String,
        role: String = "user",
        sessionId: String,
        host: String,
        port: Int,
        dbType: DatabaseType = DatabaseType.MYSQL
    ): String {
        return JWT.create()
            .withAudience(JwtConfig.audience)
            .withIssuer(JwtConfig.issuer)
            .withClaim("username", username)
            .withClaim("role", role)
            .withClaim("sessionId", sessionId)
            .withClaim("dbHost", host)
            .withClaim("dbPort", port)
            .withClaim("dbType", dbType.name)
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

    fun getSessionIdFromToken(token: String): String? {
        return try {
            val jwt = JwtConfig.verifier.verify(token)
            jwt.getClaim("sessionId").asString()
        } catch (e: Exception) {
            null
        }
    }

    fun getHostFromToken(token: String): String? {
        return try {
            val jwt = JwtConfig.verifier.verify(token)
            jwt.getClaim("dbHost").asString()
        } catch (e: Exception) {
            null
        }
    }

    fun getPortFromToken(token: String): Int? {
        return try {
            val jwt = JwtConfig.verifier.verify(token)
            jwt.getClaim("dbPort").asInt()
        } catch (e: Exception) {
            null
        }
    }

    fun getDbTypeFromToken(token: String): DatabaseType? {
        return try {
            val jwt = JwtConfig.verifier.verify(token)
            val dbTypeName = jwt.getClaim("dbType").asString()
            dbTypeName?.let { DatabaseType.valueOf(it) }
        } catch (e: Exception) {
            null
        }
    }
}