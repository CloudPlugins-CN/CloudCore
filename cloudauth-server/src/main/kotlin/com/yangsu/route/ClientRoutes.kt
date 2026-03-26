package com.yangsu.route

import com.yangsu.model.*
import com.yangsu.service.AuthService
import com.yangsu.service.PluginService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.clientRoutes() {
    route("/api/client") {
        
        // 客户端验证授权 (CloudCore插件调用)
        post("/verify") {
            val request = call.receive<VerifyRequest>()
            
            if (request.username.isBlank() || request.licenseCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest,
                    VerifyResponse(false, message = "用户名和授权码不能为空"))
                return@post
            }
            
            val response = AuthService.verify(request)
            call.respond(response)
        }
        
        // 下载插件JAR
        get("/download/{pluginName}/{version}") {
            val pluginName = call.parameters["pluginName"]
            val version = call.parameters["version"]
            
            if (pluginName.isNullOrBlank() || version.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "无效的参数")
                return@get
            }
            
            val jarFile = PluginService.getPluginJarPath(pluginName, version)
            
            if (!jarFile.exists()) {
                call.respond(HttpStatusCode.NotFound, "插件文件不存在")
                return@get
            }
            
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "$pluginName-$version.jar"
                ).toString()
            )
            call.respondFile(jarFile)
        }
        
        // 批量验证多个授权码 (一次性验证所有插件)
        post("/verify-batch") {
            @kotlinx.serialization.Serializable
            data class BatchVerifyRequest(
                val username: String,
                val licenses: List<String>,
                val ip: String?,
                val mac: String?,
                val machineCode: String?
            )
            
            @kotlinx.serialization.Serializable
            data class BatchVerifyResponse(
                val results: List<VerifyResponse>
            )
            
            val request = call.receive<BatchVerifyRequest>()
            
            val results = request.licenses.map { licenseCode ->
                AuthService.verify(VerifyRequest(
                    username = request.username,
                    licenseCode = licenseCode,
                    ip = request.ip,
                    mac = request.mac,
                    machineCode = request.machineCode
                ))
            }
            
            call.respond(BatchVerifyResponse(results))
        }
        
        // 获取插件信息 (无需验证)
        get("/plugin/{name}") {
            val name = call.parameters["name"]
            if (name.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest,
                    SimpleApiResponse(false, "无效的插件名"))
                return@get
            }
            
            val plugin = PluginService.getPluginByName(name)
            if (plugin == null) {
                call.respond(HttpStatusCode.NotFound,
                    SimpleApiResponse(false, "插件不存在"))
                return@get
            }
            
            // 只返回基本信息
            call.respond(ApiResponse(true, data = mapOf(
                "name" to plugin.name,
                "displayName" to plugin.displayName,
                "version" to plugin.version,
                "enabled" to plugin.enabled
            )))
        }
    }
}
