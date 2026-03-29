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
            // 创建所有表（如果不存在）
            SchemaUtils.create(
                Users,
                Plugins,
                LicenseCodes,
                DeviceBindings,
                UserPluginAuth,
                AuditLogs,
                SystemConfig,
                VerificationCodes,
                PluginExchangeConfigs,
                PluginClaimConfigs
            )
            
            // 数据库迁移：添加缺失的列
            migrateDatabase()

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
    
    /**
     * 数据库迁移：添加缺失的表和列
     * 程序更新时会自动检查并创建缺失的数据库结构，防止数据丢失
     */
    private fun Transaction.migrateDatabase() {
        try {
            println("Checking database migrations...")
            
            // 1. 检查并创建缺失的表（SchemaUtils.create 已经处理了 IF NOT EXISTS）
            // 这里额外确保所有表都存在
            val requiredTables = listOf(
                "users", "plugins", "license_codes", "device_bindings",
                "user_plugin_auth", "audit_logs", "system_config",
                "verification_codes", "plugin_exchange_configs", "plugin_claim_configs"
            )
            
            requiredTables.forEach { tableName ->
                if (!checkTableExists(tableName)) {
                    println("Warning: Table '$tableName' does not exist. It will be created by SchemaUtils.")
                }
            }
            
            // 2. 检查 plugin_claim_configs 表是否有 exclude_plugin_ids 列
            if (checkTableExists("plugin_claim_configs")) {
                val hasExcludeColumn = checkColumnExists("plugin_claim_configs", "exclude_plugin_ids")
                if (!hasExcludeColumn) {
                    exec("ALTER TABLE plugin_claim_configs ADD COLUMN exclude_plugin_ids TEXT DEFAULT ''")
                    println("Database migration: Added exclude_plugin_ids column to plugin_claim_configs table")
                }
                
                // 2.1 检查 plugin_claim_configs 表是否有 free_plugin_not_count 列
                val hasFreePluginNotCount = checkColumnExists("plugin_claim_configs", "free_plugin_not_count")
                if (!hasFreePluginNotCount) {
                    exec("ALTER TABLE plugin_claim_configs ADD COLUMN free_plugin_not_count INTEGER DEFAULT 0")
                    println("Database migration: Added free_plugin_not_count column to plugin_claim_configs table")
                }
            }
            
            // 3. 检查 user_plugin_auth 表是否有 granted_at 和 granted_by 列
            if (checkTableExists("user_plugin_auth")) {
                val hasGrantedAt = checkColumnExists("user_plugin_auth", "granted_at")
                if (!hasGrantedAt) {
                    exec("ALTER TABLE user_plugin_auth ADD COLUMN granted_at DATETIME DEFAULT CURRENT_TIMESTAMP")
                    println("Database migration: Added granted_at column to user_plugin_auth table")
                }
                
                val hasGrantedBy = checkColumnExists("user_plugin_auth", "granted_by")
                if (!hasGrantedBy) {
                    exec("ALTER TABLE user_plugin_auth ADD COLUMN granted_by INTEGER REFERENCES users(id)")
                    println("Database migration: Added granted_by column to user_plugin_auth table")
                }
            }
            
            // 4. 检查 plugins 表是否有 author 和 price 列
            if (checkTableExists("plugins")) {
                val hasAuthor = checkColumnExists("plugins", "author")
                if (!hasAuthor) {
                    exec("ALTER TABLE plugins ADD COLUMN author VARCHAR(100)")
                    println("Database migration: Added author column to plugins table")
                }
                
                val hasPrice = checkColumnExists("plugins", "price")
                if (!hasPrice) {
                    exec("ALTER TABLE plugins ADD COLUMN price DECIMAL(10, 2) DEFAULT 0.00")
                    println("Database migration: Added price column to plugins table")
                }
            }
            
            println("Database migration check completed.")
        } catch (e: Exception) {
            println("Database migration failed: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 检查表是否存在
     */
    private fun Transaction.checkTableExists(tableName: String): Boolean {
        return try {
            val result = exec("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'") { rs ->
                rs.next()
            }
            result != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检查表中是否存在指定列
     */
    private fun Transaction.checkColumnExists(tableName: String, columnName: String): Boolean {
        return try {
            exec("SELECT $columnName FROM $tableName LIMIT 1") {}
            true
        } catch (e: Exception) {
            false
        }
    }
}
