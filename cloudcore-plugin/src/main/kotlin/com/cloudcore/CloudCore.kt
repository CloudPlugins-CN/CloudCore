package com.cloudcore

import com.cloudcore.config.CloudConfig
import com.cloudcore.loader.AuthManager
import com.cloudcore.loader.CloudPluginLoader
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.console
import taboolib.common.platform.function.info
import taboolib.common.platform.function.pluginId
import taboolib.common.platform.function.pluginVersion
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.io.File

/**
 * CloudCore - 云系列插件核心
 * 云端授权与插件管理系统
 */
object CloudCore : Plugin() {
    
    @Config("config.yml")
    lateinit var config: Configuration
        private set
    
    /** 配置管理器 */
    lateinit var cloudConfig: CloudConfig
        private set
    
    /** 授权管理器 */
    lateinit var authManager: AuthManager
        private set
    
    /** 云端插件加载器 */
    lateinit var pluginLoader: CloudPluginLoader
        private set
    
    /** 插件数据目录 */
    val dataFolder: File
        get() = File("plugins/CloudCore")
    
    /** 云端插件配置目录 (也是JAR存放目录，保证dataFolder正确) */
    val configFolder: File
        get() = File(dataFolder, "Config")
    
    /** 临时文件目录 (系统临时目录，用于下载缓存等) */
    val tempFolder: File
        get() = File(System.getProperty("java.io.tmpdir"), "CloudCore")

    /**
     * CloudCore 是否已经完成运行期初始化
     */
    fun isInitialized(): Boolean {
        return ::cloudConfig.isInitialized && ::authManager.isInitialized && ::pluginLoader.isInitialized
    }
    
    override fun onLoad() {
        // 只创建数据目录，Config目录在加载云端插件时按需创建
        dataFolder.mkdirs()
    }
    
    override fun onEnable() {
        // 启动时清理上次遗留的临时文件
        cleanTempFiles()
        
        // 初始化配置
        cloudConfig = CloudConfig(config)
        
        // 初始化授权管理器
        authManager = AuthManager(cloudConfig)
        
        // 初始化云端插件加载器
        pluginLoader = CloudPluginLoader(cloudConfig, authManager)

        console().sendMessage("§cLoading §b$pluginId §6$pluginVersion")

        // 异步验证授权并加载插件
        if (cloudConfig.isConfigured()) {
            authManager.verifyAndLoadPlugins()
        } else {
            warning("请在配置文件填写授权信息")
        }
    }
    
    override fun onDisable() {
        pluginLoader.unloadAllPlugins()
        console().sendMessage("§cUnloading §b$pluginId §6$pluginVersion")
    }
    
    /**
     * 清理临时目录 (仅启动时调用)
     */
    private fun cleanTempFiles() {
        // 清理系统临时目录
        if (tempFolder.exists()) {
            tempFolder.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
        }
    }
    
    /**
     * 只重新加载配置，不重新加载插件
     */
    fun reloadConfigOnly() {
        // 重新加载配置
        config.reload()
        cloudConfig = CloudConfig(config)
        
        // 更新授权管理器和加载器的配置引用
        authManager.updateConfig(cloudConfig)
        pluginLoader.updateConfig(cloudConfig)
    }
}
