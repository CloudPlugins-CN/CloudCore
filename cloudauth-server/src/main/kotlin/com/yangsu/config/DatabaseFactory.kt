package com.yangsu.config

import at.favre.lib.crypto.bcrypt.BCrypt
import com.yangsu.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.LocalDateTime
import java.util.Properties

object DatabaseFactory {
    
    private lateinit var database: Database
    
    fun init(dataDir: File) {
        val dbFile = File(dataDir, "cloudauth.db")
        dataDir.mkdirs()
        
        database = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )
        
        transaction {
            // 创建所有表
            SchemaUtils.create(
                Users,
                Plugins,
                LicenseCodes,
                DeviceBindings,
                UserPluginAuth,
                AuditLogs,
                SystemConfig,
                VerificationCodes
            )
            
            // 创建默认管理员账户 (如果不存在)
            createDefaultAdmin()
            // 创建默认配置
            createDefaultConfig()
            // 从配置文件加载SMTP配置
            loadSmtpFromProperties()
        }
        
        println("Database initialized at: ${dbFile.absolutePath}")
    }
    
    private fun createDefaultAdmin() {
        val adminExists = Users.select(Users.username)
            .where { Users.username eq "SunShine" }
            .count() > 0
            
        if (!adminExists) {
            val hashedPassword = BCrypt.withDefaults().hashToString(12, "SunShine123".toCharArray())
            Users.insert {
                it[username] = "SunShine"
                it[password] = hashedPassword
                it[email] = "1758983508@qq.com"  // 超级管理员邮箱
                it[isAdmin] = true
                it[isSuperAdmin] = true  // 超级管理员
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
            println("Default super admin account created: SunShine / SunShine123")
        } else {
            // 确保已存在的 SunShine 账户是超级管理员并设置邮箱
            Users.update({ Users.username eq "SunShine" }) {
                it[isSuperAdmin] = true
                it[email] = "1758983508@qq.com"
            }
        }
    }
    
    private fun createDefaultConfig() {
        val configExists = SystemConfig.selectAll()
            .where { SystemConfig.key eq "unbind_cooldown_hours" }
            .count() > 0
            
        if (!configExists) {
            SystemConfig.insert {
                it[key] = "unbind_cooldown_hours"
                it[value] = "24"
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    /**
     * 从配置文件加载SMTP配置到数据库（如果数据库中还没有配置）
     */
    private fun loadSmtpFromProperties() {
        // 检查数据库是否已有SMTP配置
        val smtpExists = SystemConfig.selectAll()
            .where { SystemConfig.key eq "smtp_host" }
            .count() > 0
        
        if (smtpExists) return  // 已有配置，不覆盖
        
        // 从配置文件读取
        val props = Properties()
        try {
            // 1. 先尝试读取外部配置文件
            val externalConfig = File("application.properties")
            if (externalConfig.exists()) {
                externalConfig.inputStream().use { props.load(it) }
                println("Loading SMTP config from external file: ${externalConfig.absolutePath}")
            } else {
                // 2. 否则读取classpath内置配置
                Thread.currentThread().contextClassLoader.getResourceAsStream("application.properties")?.use {
                    props.load(it)
                    println("Loading SMTP config from classpath")
                }
            }
        } catch (e: Exception) {
            println("Failed to load config: ${e.message}")
            return
        }
        
        val host = props.getProperty("smtp.host")?.takeIf { it.isNotBlank() && it != "your_email@163.com" }
        val port = props.getProperty("smtp.port")
        val username = props.getProperty("smtp.username")?.takeIf { it.isNotBlank() && !it.startsWith("your_") }
        val password = props.getProperty("smtp.password")?.takeIf { it.isNotBlank() && !it.startsWith("your_") }
        val fromName = props.getProperty("smtp.from-name") ?: "CloudAuth"
        
        if (host != null && username != null && password != null) {
            val now = LocalDateTime.now()
            val configs = mapOf(
                "smtp_host" to host,
                "smtp_port" to (port ?: "465"),
                "smtp_username" to username,
                "smtp_password" to password,
                "smtp_from_name" to fromName
            )
            
            configs.forEach { (key, value) ->
                SystemConfig.insert {
                    it[SystemConfig.key] = key
                    it[SystemConfig.value] = value
                    it[updatedAt] = now
                }
            }
            println("SMTP config loaded from properties file: $host")
        }
    }
    
    fun <T> dbQuery(block: () -> T): T = transaction { block() }
}
