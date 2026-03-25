package com.yangsu.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

object JwtConfig {
    private const val SECRET = "cloudauth-secret-key-2024"
    private const val ISSUER = "cloudauth-server"
    private const val AUDIENCE = "cloudauth-users"
    private const val VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L // 7天
    
    private val algorithm = Algorithm.HMAC256(SECRET)
    
    val verifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()
    
    fun generateToken(userId: Int, username: String, isAdmin: Boolean): String {
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withClaim("isAdmin", isAdmin)
            .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_MS))
            .sign(algorithm)
    }
}

fun Application.configureJwt() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
        
        jwt("admin-jwt") {
            verifier(JwtConfig.verifier)
            validate { credential ->
                val isAdmin = credential.payload.getClaim("isAdmin").asBoolean()
                if (isAdmin == true) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

// 扩展函数: 从JWT获取用户信息
fun JWTPrincipal.getUserId(): Int = payload.getClaim("userId").asInt()
fun JWTPrincipal.getUsername(): String = payload.getClaim("username").asString()
fun JWTPrincipal.isAdmin(): Boolean = payload.getClaim("isAdmin").asBoolean()
