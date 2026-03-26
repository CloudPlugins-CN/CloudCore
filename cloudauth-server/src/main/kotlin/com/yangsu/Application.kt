package com.yangsu

import com.yangsu.config.AppConfig
import com.yangsu.config.DatabaseFactory
import com.yangsu.config.configureJwt
import com.yangsu.model.ApiResponse
import com.yangsu.model.SimpleApiResponse
import com.yangsu.route.*
import com.yangsu.service.PluginService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.json.Json
import java.io.File

fun main() {
    // 加载配置文件
    AppConfig.load()
    
    // 使用配置文件中的端口和数据目录（环境变量优先）
    val port = System.getenv("PORT")?.toIntOrNull() ?: AppConfig.serverPort
    val dataDir = File(System.getenv("DATA_DIR") ?: AppConfig.dataDir)
    
    // 初始化数据库
    DatabaseFactory.init(dataDir)
    PluginService.init(dataDir)
    
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        configurePlugins()
        configureJwt()
        configureRouting()
    }.start(wait = true)
}

fun Application.configurePlugins() {
    // JSON序列化
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
    
    // CORS
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
    }
    
    // 日志
    install(CallLogging)
    
    // 异常处理
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                SimpleApiResponse(false, cause.message ?: "服务器内部错误")
            )
        }
    }
}

fun Application.configureRouting() {
    routing {
        // 静态文件 (WebUI)
        staticResources("/", "static") {
            default("index.html")
        }
        
        // API路由
        authRoutes()
        adminRoutes()
        userRoutes()
        clientRoutes()
        
        // 健康检查
        get("/health") {
            call.respond(ApiResponse(true, data = "ok"))
        }
    }
}
