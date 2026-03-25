package com.cloudcore.api

import com.cloudcore.CloudCore
import org.bukkit.plugin.java.JavaPlugin
import taboolib.module.configuration.Configuration
import java.io.File
import java.io.InputStream

/**
 * CloudCore 对外 API。
 *
 * 该对象为其他插件提供授权状态查询、配置目录访问和配置管理入口。
 */
object CloudCoreAPI {

    /**
     * CloudCore 是否已经完成初始化。
     */
    @JvmStatic
    fun isReady(): Boolean = CloudCore.isInitialized()

    /**
     * CloudCore 配置是否完整。
     */
    @JvmStatic
    fun isConfigured(): Boolean = isReady() && CloudCore.cloudConfig.isConfigured()

    /**
     * 获取授权服务端地址。
     */
    @JvmStatic
    fun getServerUrl(): String? = if (isReady()) CloudCore.cloudConfig.serverUrl else null

    /**
     * 检查插件是否已授权。
     */
    @JvmStatic
    fun isAuthorized(pluginName: String): Boolean {
        return isReady() && CloudCore.authManager.isPluginAuthorized(pluginName)
    }

    /**
     * 获取单个插件的授权信息。
     */
    @JvmStatic
    fun getAuthorization(pluginName: String): PluginAuthorization? {
        if (!isReady()) {
            return null
        }
        return CloudCore.authManager.getVerifiedPlugin(pluginName)?.toAuthorization()
    }

    /**
     * 获取当前全部已授权插件。
     */
    @JvmStatic
    fun getAuthorizedPlugins(): List<PluginAuthorization> {
        if (!isReady()) {
            return emptyList()
        }
        return CloudCore.authManager.getVerifiedPlugins().map { it.toAuthorization() }
    }

    /**
     * 获取当前全部已加载插件。
     */
    @JvmStatic
    fun getLoadedPlugins(): List<LoadedPluginInfo> {
        if (!isReady()) {
            return emptyList()
        }
        return CloudCore.pluginLoader.getLoadedPlugins().map { plugin ->
            LoadedPluginInfo(
                name = plugin.name,
                version = plugin.plugin?.description?.version,
                jarFile = plugin.jarFile,
                configDir = plugin.configDir,
                enabled = plugin.plugin?.isEnabled == true
            )
        }
    }

    /**
     * 检查插件是否已被 CloudCore 加载。
     */
    @JvmStatic
    fun isLoaded(pluginName: String): Boolean {
        return isReady() && CloudCore.pluginLoader.isPluginLoaded(pluginName)
    }

    /**
     * 获取 CloudCore 数据目录。
     */
    @JvmStatic
    fun getDataFolder(): File = CloudCore.dataFolder

    /**
     * 获取 CloudCore 配置根目录。
     */
    @JvmStatic
    fun getConfigRootFolder(): File = CloudCore.configFolder

    /**
     * 获取 CloudCore 临时目录。
     */
    @JvmStatic
    fun getTempFolder(): File = CloudCore.tempFolder

    /**
     * 获取指定插件在 CloudCore 下的配置目录。
     */
    @JvmStatic
    fun getPluginConfigFolder(pluginName: String): File {
        return CloudConfigManager.getPluginConfigFolder(pluginName)
    }

    /**
     * 获取指定插件在 CloudCore 下的配置文件。
     */
    @JvmStatic
    fun getPluginConfigFile(pluginName: String, fileName: String = "config.yml"): File {
        return CloudConfigManager.getPluginConfigFile(pluginName, fileName)
    }

    /**
     * 获取插件的配置管理器实例。
     */
    @JvmStatic
    fun getConfigManager(plugin: JavaPlugin): CloudConfigManager = CloudConfigManager.of(plugin)

    /**
     * 加载指定插件的配置文件。
     */
    @JvmStatic
    fun loadConfig(pluginName: String, fileName: String = "config.yml"): Configuration {
        return CloudConfigManager.getPluginConfigFile(pluginName, fileName).let { file ->
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                file.createNewFile()
            }
            Configuration.loadFromFile(file)
        }
    }

    /**
     * 保存指定插件的配置文件。
     */
    @JvmStatic
    fun saveConfig(pluginName: String, fileName: String, configuration: Configuration) {
        val file = getPluginConfigFile(pluginName, fileName)
        file.parentFile?.mkdirs()
        configuration.saveToFile(file)
    }

    /**
     * 从插件资源中保存默认文件。
     */
    @JvmStatic
    fun saveDefaultResource(plugin: JavaPlugin, resourcePath: String, targetName: String = resourcePath): Boolean {
        return getConfigManager(plugin).saveResource(resourcePath, targetName)
    }

    /**
     * 强制覆盖保存插件资源。
     */
    @JvmStatic
    fun saveResourceForce(plugin: JavaPlugin, resourcePath: String, targetName: String = resourcePath): Boolean {
        return getConfigManager(plugin).saveResourceForce(resourcePath, targetName)
    }

    /**
     * 从输入流保存配置文件。
     */
    @JvmStatic
    fun saveConfig(plugin: JavaPlugin, fileName: String, inputStream: InputStream): Boolean {
        return getConfigManager(plugin).saveConfig(fileName, inputStream)
    }

    /**
     * 使用字符串内容保存默认配置文件。
     */
    @JvmStatic
    fun saveDefaultConfig(plugin: JavaPlugin, fileName: String, defaultContent: String): Boolean {
        return getConfigManager(plugin).saveDefaultConfig(fileName, defaultContent)
    }

    /**
     * 重新加载 CloudCore。
     */
    @JvmStatic
    fun reload() {
        CloudCore.reload()
    }

    /**
     * 信息日志。
     */
    @JvmStatic
    fun info(pluginName: String, message: String) {
        taboolib.common.platform.function.info("[$pluginName] $message")
    }

    /**
     * 警告日志。
     */
    @JvmStatic
    fun warning(pluginName: String, message: String) {
        taboolib.common.platform.function.warning("[$pluginName] $message")
    }

    /**
     * 错误日志。
     */
    @JvmStatic
    fun severe(pluginName: String, message: String) {
        taboolib.common.platform.function.severe("[$pluginName] $message")
    }
}

data class PluginAuthorization(
    val licenseCode: String,
    val pluginName: String,
    val pluginVersion: String,
    val downloadUrl: String
)

data class LoadedPluginInfo(
    val name: String,
    val version: String?,
    val jarFile: File,
    val configDir: File,
    val enabled: Boolean
)

private fun com.cloudcore.loader.VerifiedPlugin.toAuthorization(): PluginAuthorization {
    return PluginAuthorization(
        licenseCode = licenseCode,
        pluginName = pluginName,
        pluginVersion = pluginVersion,
        downloadUrl = downloadUrl
    )
}
