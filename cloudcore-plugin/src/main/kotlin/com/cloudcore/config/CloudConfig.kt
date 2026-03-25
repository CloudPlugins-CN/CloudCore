package com.cloudcore.config

import com.cloudcore.CloudCore
import taboolib.module.configuration.Configuration
import java.io.File

/**
 * CloudCore 配置管理
 */
class CloudConfig(private val config: Configuration) {
    
    /**
     * 授权服务器地址
     */
    val serverUrl: String
        get() = config.getString("server-url", "http://localhost:8080")!!.trimEnd('/')
    
    /**
     * 用户名
     */
    val username: String
        get() = config.getString("username", "")!!
    
    /**
     * 授权码列表
     */
    val licenseCodes: List<String>
        get() = config.getStringList("license-codes")
    
    /**
     * 是否启用调试模式
     */
    val debug: Boolean
        get() = config.getBoolean("debug", false)
    
    /**
     * 连接超时时间 (秒)
     */
    val connectTimeout: Int
        get() = config.getInt("connect-timeout", 30)
    
    /**
     * 读取超时时间 (秒)
     */
    val readTimeout: Int
        get() = config.getInt("read-timeout", 60)
    
    /**
     * 是否自动重试
     */
    val autoRetry: Boolean
        get() = config.getBoolean("auto-retry", true)
    
    /**
     * 重试次数
     */
    val retryCount: Int
        get() = config.getInt("retry-count", 3)
    
    /**
     * 检查配置是否完整
     */
    fun isConfigured(): Boolean {
        return serverUrl.isNotBlank() && 
               username.isNotBlank() && 
               licenseCodes.isNotEmpty()
    }
    
    /**
     * 获取指定插件的配置文件
     */
    fun getPluginConfig(pluginName: String): Configuration? {
        val configFile = File(CloudCore.configFolder, "$pluginName/config.yml")
        return if (configFile.exists()) {
            Configuration.loadFromFile(configFile)
        } else {
            null
        }
    }
}
