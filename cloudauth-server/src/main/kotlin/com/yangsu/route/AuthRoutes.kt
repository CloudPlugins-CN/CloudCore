package com.yangsu.route

import com.yangsu.model.*
import com.yangsu.service.UserService
import com.yangsu.service.EmailService
import com.yangsu.config.JwtConfig
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes() {
    route("/api/auth") {
        
        // 发送验证码
        post("/send-code") {
            val request = call.receive<SendCodeRequest>()
            
            // 验证邮箱格式
            if (!request.email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
                call.respond(HttpStatusCode.BadRequest,
                    SimpleApiResponse(false, "邮箱格式不正确"))
                return@post
            }
            
            // 验证类型
            if (request.type !in listOf("REGISTER", "FORGOT_PASSWORD")) {
                call.respond(HttpStatusCode.BadRequest,
                    SimpleApiResponse(false, "无效的验证码类型"))
                return@post
            }
            
            // 注册时检查邮箱是否已被使用
            if (request.type == "REGISTER") {
                if (UserService.emailExists(request.email)) {
                    call.respond(HttpStatusCode.Conflict,
                        SimpleApiResponse(false, "邮箱已被注册"))
                    return@post
                }
            }
            
            // 找回密码时检查邮箱是否已注册
            if (request.type == "FORGOT_PASSWORD") {
                if (!UserService.emailExists(request.email)) {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "邮箱未注册"))
                    return@post
                }
            }
            
            val result = EmailService.sendVerificationCode(request.email, request.type)
            result.fold(
                onSuccess = { msg ->
                    call.respond(SimpleApiResponse(true, msg))
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.InternalServerError,
                        SimpleApiResponse(false, e.message))
                }
            )
        }
        
        // 用户注册
        post("/register") {
            val request = call.receive<RegisterRequest>()
            
            if (request.username.length < 3 || request.username.length > 20) {
                call.respond(HttpStatusCode.BadRequest, 
                    SimpleApiResponse(false, "用户名长度需要在3-20个字符之间"))
                return@post
            }
            
            if (request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest,
                    SimpleApiResponse(false, "密码长度至少6个字符"))
                return@post
            }
            
            // 验证邮箱格式
            if (!request.email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
                call.respond(HttpStatusCode.BadRequest,
                    SimpleApiResponse(false, "邮箱格式不正确"))
                return@post
            }
            
            // 验证验证码
            val codeValid = EmailService.verifyCode(request.email, request.verificationCode, "REGISTER")
            if (!codeValid) {
                call.respond(HttpStatusCode.BadRequest,
                    SimpleApiResponse(false, "验证码无效或已过期"))
                return@post
            }
            
            val result = UserService.register(request)
            result.fold(
                onSuccess = { user ->
                    call.respond(ApiResponse(true, "注册成功", user))
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.Conflict,
                        SimpleApiResponse(false, e.message))
                }
            )
        }
        
        // 用户登录
        post("/login") {
            val request = call.receive<LoginRequest>()
            
            val result = UserService.login(request)
            result.fold(
                onSuccess = { (user, isAdmin) ->
                    val token = JwtConfig.generateToken(user.id, user.username, isAdmin)
                    call.respond(ApiResponse(true, "登录成功", LoginResponse(token, user.username, isAdmin, user.isSuperAdmin)))
                },
                onFailure = { e ->
                    // 使用 200 OK 返回错误信息，避免 401 日志
                    call.respond(SimpleApiResponse(false, e.message ?: "用户名或密码错误"))
                }
            )
        }
        
        // 找回密码
        post("/forgot-password") {
            val request = call.receive<ForgotPasswordRequest>()
            
            // 验证邮箱格式
            if (!request.email.matches(Regex("^[A-Za-z0-9+_.-]+@(.+)$"))) {
                call.respond(HttpStatusCode.BadRequest,
                    SimpleApiResponse(false, "邮箱格式不正确"))
                return@post
            }
            
            if (request.newPassword.length < 6) {
                call.respond(HttpStatusCode.BadRequest,
                    SimpleApiResponse(false, "密码长度至少6个字符"))
                return@post
            }
            
            // 验证验证码
            val codeValid = EmailService.verifyCode(request.email, request.verificationCode, "FORGOT_PASSWORD")
            if (!codeValid) {
                call.respond(HttpStatusCode.BadRequest,
                    SimpleApiResponse(false, "验证码无效或已过期"))
                return@post
            }
            
            val result = UserService.resetPasswordByEmail(request.email, request.newPassword)
            result.fold(
                onSuccess = {
                    call.respond(SimpleApiResponse(true, "密码重置成功"))
                },
                onFailure = { e ->
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, e.message))
                }
            )
        }
    }
}
