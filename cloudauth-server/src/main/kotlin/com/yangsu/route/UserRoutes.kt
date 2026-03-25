package com.yangsu.route

import com.yangsu.model.ApiResponse
import com.yangsu.model.UserUnbindRequest
import com.yangsu.model.VerifyUserDTO
import com.yangsu.service.AuthService
import com.yangsu.service.LicenseService
import com.yangsu.service.UserService
import com.yangsu.config.getUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
                    call.respond(ApiResponse<Nothing>(false, "用户不存在"))
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
                            call.respond(ApiResponse<Nothing>(true, "解绑成功"))
                        } else {
                            call.respond(ApiResponse<Nothing>(false, "解绑失败"))
                        }
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            ApiResponse<Nothing>(false, e.message))
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
                        call.respond(ApiResponse<Nothing>(true, "已解绑 $count 个设备"))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            ApiResponse<Nothing>(false, e.message))
                    }
                )
            }
        }
    }
}
