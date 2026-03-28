package com.yangsu.route

import com.yangsu.model.*
import com.yangsu.service.*
import com.yangsu.config.getUserId
import com.yangsu.util.JarAnalyzer
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream

/** 上传结果 */
private data class PluginUploadResult(
    val success: Boolean,
    val message: String,
    val version: String? = null
)

fun Route.adminRoutes() {
    authenticate("admin-jwt") {
        route("/api/admin") {
            
            // ==================== 用户管理 ====================
            
            // 获取所有用户
            get("/users") {
                val users = UserService.getAllUsers()
                call.respond(ApiResponse(true, data = users))
            }
            
            // 修改用户密码
            post("/users/password") {
                val request = call.receive<ChangePasswordRequest>()
                
                if (request.newPassword.length < 6) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "密码长度不能少于6位"))
                    return@post
                }
                
                val success = UserService.changePassword(request.userId, request.newPassword)
                if (success) {
                    call.respond(SimpleApiResponse(true, "密码修改成功"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "用户不存在"))
                }
            }
            
            // 超级管理员创建管理员账户
            post("/users/create-admin") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                // 检查是否为超级管理员
                if (!UserService.isSuperAdmin(userId)) {
                    call.respond(HttpStatusCode.Forbidden,
                        SimpleApiResponse(false, "只有超级管理员才能创建管理员账户"))
                    return@post
                }
                
                val request = call.receive<CreateAdminRequest>()
                
                if (request.username.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "用户名不能为空"))
                    return@post
                }
                
                if (request.password.length < 6) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "密码长度不能少于6位"))
                    return@post
                }
                
                val result = UserService.createAdmin(request.username, request.password)
                result.fold(
                    onSuccess = { user ->
                        call.respond(ApiResponse(true, "管理员账户创建成功", user))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 超级管理员封禁/解封用户
            post("/users/{id}/ban") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                if (!UserService.isSuperAdmin(userId)) {
                    call.respond(HttpStatusCode.Forbidden,
                        SimpleApiResponse(false, "只有超级管理员才能封禁/解封用户"))
                    return@post
                }
                
                val targetUserId = call.parameters["id"]?.toIntOrNull()
                if (targetUserId == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的用户ID"))
                    return@post
                }
                
                // 不能封禁自己
                if (targetUserId == userId) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "不能封禁自己"))
                    return@post
                }
                
                // 检查目标用户是否为超级管理员
                if (UserService.isSuperAdmin(targetUserId)) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "不能封禁超级管理员"))
                    return@post
                }
                
                val success = UserService.toggleBan(targetUserId, true)
                if (success) {
                    call.respond(SimpleApiResponse(true, "用户已封禁"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "用户不存在"))
                }
            }
            
            // 超级管理员解封用户
            post("/users/{id}/unban") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                
                if (!UserService.isSuperAdmin(userId)) {
                    call.respond(HttpStatusCode.Forbidden,
                        SimpleApiResponse(false, "只有超级管理员才能封禁/解封用户"))
                    return@post
                }
                
                val targetUserId = call.parameters["id"]?.toIntOrNull()
                if (targetUserId == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的用户ID"))
                    return@post
                }
                
                val success = UserService.toggleBan(targetUserId, false)
                if (success) {
                    call.respond(SimpleApiResponse(true, "用户已解封"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "用户不存在"))
                }
            }
            
            // 超级管理员设置/取消用户管理员权限
            post("/users/{id}/set-admin") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                            
                if (!UserService.isSuperAdmin(userId)) {
                    call.respond(HttpStatusCode.Forbidden,
                        SimpleApiResponse(false, "只有超级管理员才能设置管理员"))
                    return@post
                }
                            
                val targetUserId = call.parameters["id"]?.toIntOrNull()
                if (targetUserId == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的用户 ID"))
                    return@post
                }
                            
                // 不能修改自己的权限
                if (targetUserId == userId) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "不能修改自己的权限"))
                    return@post
                }
                            
                // 不能修改超级管理员的权限
                if (UserService.isSuperAdmin(targetUserId)) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "不能修改超级管理员的权限"))
                    return@post
                }
                            
                val isAdmin = call.request.queryParameters["isAdmin"]?.toBoolean() ?: false
                val success = UserService.setAdmin(targetUserId, isAdmin)
                if (success) {
                    call.respond(SimpleApiResponse(true, if (isAdmin) "已设置为管理员" else "已取消管理员"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "用户不存在"))
                }
            }
                        
            // 超级管理员删除用户
            delete("/users/{id}") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getUserId()
                            
                if (!UserService.isSuperAdmin(userId)) {
                    call.respond(HttpStatusCode.Forbidden,
                        SimpleApiResponse(false, "只有超级管理员才能删除用户"))
                    return@delete
                }
                            
                val targetUserId = call.parameters["id"]?.toIntOrNull()
                if (targetUserId == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的用户 ID"))
                    return@delete
                }
                            
                // 不能删除自己
                if (targetUserId == userId) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "不能删除自己"))
                    return@delete
                }
                            
                // 不能删除超级管理员
                if (UserService.isSuperAdmin(targetUserId)) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "不能删除超级管理员"))
                    return@delete
                }
                            
                val success = UserService.deleteUser(targetUserId)
                if (success) {
                    call.respond(SimpleApiResponse(true, "用户已删除"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "用户不存在"))
                }
            }
            
            // ==================== 插件管理 ====================
            
            // 获取所有插件
            get("/plugins") {
                val plugins = PluginService.getAllPlugins()
                call.respond(ApiResponse(true, data = plugins))
            }
            
            // 创建插件
            post("/plugins") {
                val request = call.receive<CreatePluginRequest>()
                val result = PluginService.createPlugin(request)
                result.fold(
                    onSuccess = { plugin ->
                        call.respond(ApiResponse(true, "插件创建成功", plugin))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 更新插件
            put("/plugins/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的插件ID"))
                    return@put
                }
                
                val request = call.receive<UpdatePluginRequest>()
                val result = PluginService.updatePlugin(id, request)
                result.fold(
                    onSuccess = { plugin ->
                        call.respond(ApiResponse(true, "插件更新成功", plugin))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 删除插件
            delete("/plugins/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的插件ID"))
                    return@delete
                }
                
                val success = PluginService.deletePlugin(id)
                if (success) {
                    call.respond(SimpleApiResponse(true, "插件删除成功"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "插件不存在"))
                }
            }
            
            // 禁用/启用插件
            put("/plugins/{id}/toggle") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的插件ID"))
                    return@put
                }
                
                val enabled = call.request.queryParameters["enabled"]?.toBoolean() ?: true
                val success = PluginService.togglePlugin(id, enabled)
                
                if (success) {
                    call.respond(SimpleApiResponse(true, if (enabled) "插件已启用" else "插件已禁用"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "插件不存在"))
                }
            }
            
            // 上传插件JAR (从 plugin.yml 获取版本，新版本覆盖旧版本)
            post("/plugins/{id}/upload") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的插件ID"))
                    return@post
                }
                
                val plugin = PluginService.getPluginById(id)
                if (plugin == null) {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "插件不存在"))
                    return@post
                }
                
                val multipart = call.receiveMultipart()
                var uploadResult: PluginUploadResult? = null
                
                while (true) {
                    val part = multipart.readPart() ?: break
                    if (part is PartData.FileItem) {
                        // 读取上传的文件内容
                        val providerFunc = part.provider
                        val channel: ByteReadChannel = providerFunc()
                        val bytes = channel.toInputStream().readBytes()
                        
                        // 解析 plugin.yml 获取版本信息
                        val pluginInfo = JarAnalyzer.parsePluginYmlFromBytes(bytes)
                        
                        if (pluginInfo == null) {
                            uploadResult = PluginUploadResult(false, "无法解析plugin.yml，请确保是有效的Bukkit插件")
                        } else {
                            val newName = pluginInfo.name
                            val newVersion = pluginInfo.version
                            
                            try {
                                // 更新数据库中的插件信息（包括名称、版本、主类、描述、作者）
                                PluginService.updatePluginFromJar(id, newName, newVersion, pluginInfo.main, pluginInfo.description, pluginInfo.author)
                                
                                // 保存新版本JAR
                                val newJarFile = PluginService.getPluginJarPath(newName, newVersion)
                                newJarFile.parentFile.mkdirs()
                                newJarFile.writeBytes(bytes)
                                
                                val message = "插件上传成功! 插件名: $newName, 版本: $newVersion"
                                uploadResult = PluginUploadResult(true, message, newVersion)
                            } catch (e: Exception) {
                                uploadResult = PluginUploadResult(false, e.message ?: "上传失败")
                            }
                        }
                    }
                    part.dispose()
                }
                
                if (uploadResult != null) {
                    if (uploadResult!!.success) {
                        call.respond(ApiResponse(true, uploadResult!!.message, mapOf("version" to uploadResult!!.version)))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, SimpleApiResponse(false, uploadResult!!.message))
                    }
                } else {
                    call.respond(HttpStatusCode.BadRequest, SimpleApiResponse(false, "未收到文件"))
                }
            }
            
            // 插件置换：将插件 A 的授权码转换为插件 B
            post("/plugins/exchange") {
                val request = call.receive<ExchangePluginRequest>()
                val result = PluginService.exchangePlugin(request.fromPluginId, request.toPluginId)
                result.fold(
                    onSuccess = { message ->
                        call.respond(SimpleApiResponse(true, message))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // ==================== 插件置换配置管理 ====================
            
            // 获取所有置换配置
            get("/exchange-configs") {
                val configs = PluginExchangeService.getAllExchangeConfigs()
                call.respond(ApiResponse(true, data = configs))
            }
            
            // 创建置换配置
            post("/exchange-configs") {
                val request = call.receive<CreateExchangeConfigRequest>()
                val result = PluginExchangeService.createExchangeConfig(request)
                result.fold(
                    onSuccess = { config ->
                        call.respond(ApiResponse(true, "创建成功", config))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 禁用/启用置换配置
            put("/exchange-configs/{id}/toggle") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的配置ID"))
                    return@put
                }
                
                val enabled = call.request.queryParameters["enabled"]?.toBoolean() ?: true
                val success = PluginExchangeService.toggleExchangeConfig(id, enabled)
                
                if (success) {
                    call.respond(SimpleApiResponse(true, if (enabled) "已启用" else "已禁用"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "配置不存在"))
                }
            }
            
            // 编辑置换配置
            put("/exchange-configs/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的配置ID"))
                    return@put
                }
                
                val request = call.receive<UpdateExchangeConfigRequest>()
                val result = PluginExchangeService.updateExchangeConfig(id, request)
                result.fold(
                    onSuccess = { config ->
                        call.respond(ApiResponse(true, "更新成功", config))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 删除置换配置
            delete("/exchange-configs/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的配置ID"))
                    return@delete
                }
                
                val success = PluginExchangeService.deleteExchangeConfig(id)
                if (success) {
                    call.respond(SimpleApiResponse(true, "删除成功"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "配置不存在"))
                }
            }
            
            // ==================== 插件领取配置管理 ====================
            
            // 获取所有领取配置
            get("/claim-configs") {
                val configs = PluginClaimService.getAllClaimConfigs()
                call.respond(ApiResponse(true, data = configs))
            }
            
            // 创建领取配置
            post("/claim-configs") {
                val request = call.receive<CreateClaimConfigRequest>()
                val result = PluginClaimService.createClaimConfig(request)
                result.fold(
                    onSuccess = { config ->
                        call.respond(ApiResponse(true, "创建成功", config))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 切换领取配置启用状态
            put("/claim-configs/{id}/toggle") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的配置ID"))
                    return@put
                }
                
                val enabled = call.request.queryParameters["enabled"]?.toBoolean() ?: true
                val success = PluginClaimService.toggleClaimConfig(id, enabled)
                
                if (success) {
                    call.respond(SimpleApiResponse(true, if (enabled) "已启用" else "已禁用"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "配置不存在"))
                }
            }
            
            // 编辑领取配置
            put("/claim-configs/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的配置ID"))
                    return@put
                }
                
                val request = call.receive<UpdateClaimConfigRequest>()
                val result = PluginClaimService.updateClaimConfig(id, request)
                result.fold(
                    onSuccess = { config ->
                        call.respond(ApiResponse(true, "更新成功", config))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 删除领取配置
            delete("/claim-configs/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的配置ID"))
                    return@delete
                }
                
                val success = PluginClaimService.deleteClaimConfig(id)
                if (success) {
                    call.respond(SimpleApiResponse(true, "删除成功"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "配置不存在"))
                }
            }
            
            // ==================== 授权码管理 ====================
            
            // 获取所有授权码
            get("/licenses") {
                val licenses = LicenseService.getAllLicenses()
                call.respond(ApiResponse(true, data = licenses))
            }
            
            // 生成授权码
            post("/licenses/generate") {
                val request = call.receive<GenerateLicenseRequest>()
                val result = LicenseService.generateLicenses(request)
                result.fold(
                    onSuccess = { licenses ->
                        // 如果指定了用户名，尝试发送邮件通知
                        if (!request.username.isNullOrBlank()) {
                            val user = UserService.getUserByUsername(request.username)
                            if (user != null && !user.email.isNullOrBlank()) {
                                // 转换为邮件信息列表
                                val emailInfos = licenses.map { license ->
                                    EmailService.LicenseEmailInfo(
                                        pluginName = license.pluginName,
                                        displayName = license.pluginDisplayName,
                                        licenseCode = license.code,
                                        maxBindings = license.maxBindings
                                    )
                                }
                                // 异步发送邮件，不阻塞响应
                                EmailService.sendLicenseEmail(user.email, user.username, emailInfos)
                            }
                        }
                        call.respond(ApiResponse(true, "授权码生成成功", licenses))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 为用户授权
            post("/licenses/grant") {
                val principal = call.principal<JWTPrincipal>()!!
                val adminId = principal.getUserId()
                val request = call.receive<GrantLicenseRequest>()
                
                val result = LicenseService.grantLicense(request, adminId)
                result.fold(
                    onSuccess = {
                        call.respond(SimpleApiResponse(true, "授权成功"))
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 禁用/启用授权码
            put("/licenses/{id}/toggle") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的授权码ID"))
                    return@put
                }
                
                val enabled = call.request.queryParameters["enabled"]?.toBoolean() ?: true
                val success = LicenseService.toggleLicense(id, enabled)
                
                if (success) {
                    call.respond(SimpleApiResponse(true, if (enabled) "已启用" else "已禁用"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "授权码不存在"))
                }
            }
            
            // 修改授权码绑定数量
            put("/licenses/{id}/bindings") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的授权码ID"))
                    return@put
                }
                
                val maxBindings = call.request.queryParameters["maxBindings"]?.toIntOrNull()
                if (maxBindings == null || maxBindings < 0) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的绑定数量"))
                    return@put
                }
                
                val success = LicenseService.updateMaxBindings(id, maxBindings)
                if (success) {
                    call.respond(SimpleApiResponse(true, "绑定数量已修改为 $maxBindings"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "授权码不存在"))
                }
            }
            
            // 删除授权码
            delete("/licenses/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的授权码ID"))
                    return@delete
                }
                
                val success = LicenseService.deleteLicense(id)
                if (success) {
                    call.respond(SimpleApiResponse(true, "授权码删除成功"))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        SimpleApiResponse(false, "授权码不存在"))
                }
            }
            
            // 解绑设备
            post("/licenses/unbind") {
                val request = call.receive<UnbindRequest>()
                val result = AuthService.unbind(request.licenseCode)
                
                result.fold(
                    onSuccess = { unbinded ->
                        if (unbinded) {
                            call.respond(SimpleApiResponse(true, "解绑成功"))
                        } else {
                            call.respond(SimpleApiResponse(true, "没有绑定的设备"))
                        }
                    },
                    onFailure = { e ->
                        call.respond(HttpStatusCode.BadRequest,
                            SimpleApiResponse(false, e.message))
                    }
                )
            }
            
            // 解绑指定用户的所有设备
            post("/users/{id}/unbind") {
                val userId = call.parameters["id"]?.toIntOrNull()
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的用户ID"))
                    return@post
                }
                
                val count = AuthService.adminUnbindUser(userId)
                call.respond(SimpleApiResponse(true, "已解绑 $count 个设备"))
            }
            
            // 删除指定用户的所有授权码
            delete("/users/{id}/licenses") {
                val userId = call.parameters["id"]?.toIntOrNull()
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的用户ID"))
                    return@delete
                }
                
                val count = LicenseService.deleteUserLicenses(userId)
                call.respond(SimpleApiResponse(true, "已删除 $count 个授权码"))
            }
            
            // 解绑所有用户的所有设备
            post("/unbind-all") {
                val count = AuthService.adminUnbindAll()
                call.respond(SimpleApiResponse(true, "已解绑全部 $count 个设备"))
            }
            
            // ==================== 统计信息 ====================
            
            get("/stats") {
                val stats = AuthService.getStats()
                call.respond(ApiResponse(true, data = stats))
            }
            
            // ==================== 系统配置 ====================
            
            // 设置解绑冷却时间
            post("/config/unbind-cooldown") {
                val hours = call.request.queryParameters["hours"]?.toIntOrNull()
                if (hours == null || hours < 0) {
                    call.respond(HttpStatusCode.BadRequest,
                        SimpleApiResponse(false, "无效的冷却时间"))
                    return@post
                }
                
                val success = AuthService.setUnbindCooldownHours(hours)
                if (success) {
                    call.respond(SimpleApiResponse(true, "解绑冷却时间已设置为 ${hours} 小时"))
                } else {
                    call.respond(HttpStatusCode.InternalServerError,
                        SimpleApiResponse(false, "设置失败"))
                }
            }
        }
    }
}
