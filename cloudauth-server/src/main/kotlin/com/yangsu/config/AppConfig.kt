package com.yangsu.config

import java.io.File
import java.util.Properties

object AppConfig {
    
    private val properties = Properties()
    private var loaded = false
    
    // 服务器配置
    var serverPort: Int = 8080
        private set
    var dataDir: String = "./data"
        private set
    
    // SMTP 配置
    var smtpHost: String? = null
        private set
    var smtpPort: Int = 25
        private set
    var smtpUsername: String? = null
        private set
    var smtpPassword: String? = null
        private set
    var smtpFromName: String = "CloudPlugins"
        private set
    
    // JWT 配置
    var jwtSecret: String = "cloudauth-default-secret-key"
        private set
    var jwtExpirationHours: Int = 72
        private set
    
    /**
     * 加载配置文件
     * 优先级: 外部 application.properties > classpath 内置配置
     */
    fun load() {
        if (loaded) return
        
        // 1. 先加载 classpath 中的默认配置
        try {
            val classLoader = Thread.currentThread().contextClassLoader
            classLoader.getResourceAsStream("application.properties")?.use {
                properties.load(it)
                println("Loaded default config from classpath")
            }
        } catch (e: Exception) {
            println("No default config in classpath")
        }
        
        // 2. 尝试加载外部配置文件（覆盖默认值）
        val externalConfig = File("application.properties")
        if (externalConfig.exists()) {
            try {
                externalConfig.inputStream().use {
                    properties.load(it)
                    println("Loaded external config: ${externalConfig.absolutePath}")
                }
            } catch (e: Exception) {
                println("Failed to load external config: ${e.message}")
            }
        }
        
        // 3. 解析配置值
        parseConfig()
        loaded = true
        
        printConfig()
    }
    
    private fun parseConfig() {
        // 服务器配置
        serverPort = properties.getProperty("server.port")?.toIntOrNull() ?: 8080
        dataDir = properties.getProperty("data.dir") ?: "./data"
        
        // SMTP 配置
        smtpHost = properties.getProperty("smtp.host")?.takeIf { it.isNotBlank() }
        smtpPort = properties.getProperty("smtp.port")?.toIntOrNull() ?: 25
        smtpUsername = properties.getProperty("smtp.username")?.takeIf { it.isNotBlank() }
        smtpPassword = properties.getProperty("smtp.password")?.takeIf { it.isNotBlank() }
        smtpFromName = properties.getProperty("smtp.from-name") ?: "CloudPlugins"
        
        // JWT 配置
        jwtSecret = properties.getProperty("jwt.secret") ?: "cloudauth-default-secret-key"
        jwtExpirationHours = properties.getProperty("jwt.expiration-hours")?.toIntOrNull() ?: 72
    }
    
    private fun printConfig() {
        println("========== CloudAuth Configuration ==========")
        println("Server Port: $serverPort")
        println("Data Dir: $dataDir")
        println("SMTP Host: ${smtpHost ?: "(not configured)"}")
        println("SMTP Port: $smtpPort")
        println("SMTP Username: ${smtpUsername?.let { "${it.take(3)}***" } ?: "(not configured)"}")
        println("SMTP Password: ${if (smtpPassword != null) "****" else "(not configured)"}")
        println("JWT Expiration: ${jwtExpirationHours}h")
        println("=============================================")
    }
    
    /**
     * 检查 SMTP 是否已配置
     */
    fun isSmtpConfigured(): Boolean {
        return !smtpHost.isNullOrBlank() && !smtpUsername.isNullOrBlank() && !smtpPassword.isNullOrBlank()
    }
}
