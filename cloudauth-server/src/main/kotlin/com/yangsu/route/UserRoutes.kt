package com.yangsu.route

import com.yangsu.model.ApiResponse
import com.yangsu.model.ClaimPluginRequest
import com.yangsu.model.SimpleApiResponse
import com.yangsu.model.UserUnbindRequest
import com.yangsu.model.VerifyUserDTO
import com.yangsu.service.AuthService
import com.yangsu.service.EmailService
import com.yangsu.service.LicenseService
import com.yangsu.service.PluginClaimService
import com.yangsu.service.PluginExchangeService
import com.yangsu.service.PluginService
import com.yangsu.service.UserService
import com.yangsu.config.getUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun Route.userRoutes() {
    authenticate("auth-jwt") {
        route("/api/user") {
            
            // 验证token有效性
            get("/verify") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                val user = UserService.getUserById(userId)
                if (user != null) {
                    call.respond(ApiResponse(true, data = VerifyUserDTO(
                        username = user.username,
                        isAdmin = user.isAdmin
                    )))
                } else {
                    call.respond(SimpleApiResponse(false, "用户不存在"))
                }
            }
            
            // 获取当前用户的授权信息
            get("/auth") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                val authInfo = LicenseService.getUserAuthInfo(userId)
                call.respond(ApiResponse(true, data = authInfo))
            }
            
            // 获取用户的所有授权插件列表
            get("/plugins") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                val authInfo = LicenseService.getUserAuthInfo(userId)
                val plugins = authInfo.plugins.map { 
                    mapOf(
                        "pluginId" to it.pluginId,
                        "pluginName" to it.pluginName,
                        "displayName" to it.displayName,
                        "version" to it.version
                    )
                }
                call.respond(ApiResponse(true, data = plugins))
            }
            
            // 用户解绑设备
            post("/unbind") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                val request = call.receive<UserUnbindRequest>()
                
                val result = AuthService.userUnbind(userId, request.bindingId)
                result.fold(
                    onSuccess = { success ->
                        if (success) {
                            call.respond(SimpleApiResponse(true, "解绑成功"))
                        } else {
                            call.respond(SimpleApiResponse(false, "解绑失败"))
                        }
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 用户解绑自己的所有设备
            post("/unbind-all") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                val result = AuthService.userUnbindAll(userId)
                result.fold(
                    onSuccess = { count ->
                        call.respond(SimpleApiResponse(true, "已解绑 $count 个设备"))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 获取用户仪表盘统计信息
            get("/dashboard") {
                val stats = UserService.getUserDashboardStats()
                call.respond(ApiResponse(true, data = stats))
            }
            
            // 获取可领取的插件列表
            get("/claimable-plugins") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                try {
                    val configs = PluginClaimService.getEnabledClaimConfigs(userId)
                    call.respond(ApiResponse(true, data = configs))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError,
                        SimpleApiResponse(false, "获取可领取插件失败: ${e.message}"))
                }
            }
            
            // 领取插件授权码
            post("/claim") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                @kotlinx.serialization.Serializable
                data class UserClaimRequest(val configId: Int)
                
                val request = call.receive<UserClaimRequest>()
                
                val result = PluginClaimService.claimPlugin(userId, request.configId)
                result.fold(
                    onSuccess = { claimResult ->
                        // 异步发送领取邮件通知（不阻塞响应）
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val user = UserService.getUserById(userId)
                                if (user?.email != null) {
                                    EmailService.sendClaimLicenseEmail(
                                        email = user.email,
                                        username = user.username,
                                        license = EmailService.LicenseEmailInfo(
                                            pluginName = claimResult.pluginName,
                                            displayName = claimResult.pluginDisplayName,
                                            licenseCode = claimResult.licenseCode,
                                            maxBindings = 1
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                println("发送领取邮件失败: ${e.message}")
                            }
                        }
                        call.respond(SimpleApiResponse(true, claimResult.message))
                    },
                    onFailure = { e ->
                        call.respond(SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 获取可用的置换配置列表
            get("/exchange-configs") {
                try {
                    val configs = PluginExchangeService.getEnabledExchangeConfigs()
                    call.respond(ApiResponse(true, data = configs))
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError,
                        SimpleApiResponse(false, "获取置换配置失败: ${e.message}"))
                }
            }
            
            // 用户执行插件置换
            post("/exchange") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                @kotlinx.serialization.Serializable
                data class UserExchangeRequest(val exchangeConfigId: Int)
                
                val request = call.receive<UserExchangeRequest>()
                
                val result = PluginExchangeService.userExchange(userId, request.exchangeConfigId)
                result.fold(
                    onSuccess = { message ->
                        call.respond(SimpleApiResponse(true, message))
                    },
                    onFailure = { e ->
                        // 使用 200 OK 返回错误信息，避免 400 控制台报错
                        call.respond(SimpleApiResponse(false, e.message))
                    }
                )
            }
        }
    }
    
    // 公开 API：获取所有可用插件（无需登录）
    route("/api/public") {
        get("/plugins") {
            val plugins = PluginService.getAllAvailablePlugins()
            call.respond(ApiResponse(true, data = plugins))
        }
    }
}
