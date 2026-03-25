package com.cloudcore.api

import com.cloudcore.CloudCore
import org.bukkit.plugin.java.JavaPlugin
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Type
import java.io.File
import java.io.InputStream

/**
 * CloudCore 配置文件管理器
 * 专为云端插件提供配置文件管理功能
 * 
 * 配置文件存放路径: plugins/CloudCore/Config/{插件名}/
 */
class CloudConfigManager(private val plugin: JavaPlugin) {
    
    /** 插件名称 */
    val pluginName: String = plugin.name
    
    /** 插件配置目录 */
    val configFolder: File
        get() = File(CloudCore.configFolder, pluginName).also { it.mkdirs() }
    
    /** 主配置文件 */
    private var mainConfig: Configuration? = null
    
    /** 缓存的配置文件 */
    private val configCache = mutableMapOf<String, Configuration>()
    
    // ==================== 主配置文件操作 ====================
    
    /**
     * 获取主配置 (config.yml)
     */
    fun getConfig(): Configuration {
        if (mainConfig == null) {
            mainConfig = loadConfig("config.yml")
        }
        return mainConfig!!
    }
    
    /**
     * 保存主配置
     */
    fun saveConfig() {
        mainConfig?.saveToFile(getConfigFile("config.yml"))
    }
    
    /**
     * 重新加载主配置
     */
    fun reloadConfig(): Configuration {
        mainConfig = loadConfig("config.yml")
        return mainConfig!!
    }
    
    /**
     * 保存默认主配置 (从JAR资源)
     * @return true 如果创建了新文件
     */
    fun saveDefaultConfig(): Boolean {
        return saveResource("config.yml")
    }
    
    // ==================== 通用配置文件操作 ====================
    
    /**
     * 获取配置文件对象
     */
    fun getConfigFile(fileName: String): File {
        return File(configFolder, fileName)
    }
    
    /**
     * 加载配置文件
     * @param fileName 文件名 (如 "config.yml", "data/players.yml")
     */
    fun loadConfig(fileName: String): Configuration {
        val file = getConfigFile(fileName)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        val config = Configuration.loadFromFile(file)
        configCache[fileName] = config
        return config
    }
    
    /**
     * 加载配置文件 (指定类型)
     */
    fun loadConfig(fileName: String, type: Type): Configuration {
        val file = getConfigFile(fileName)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        val config = Configuration.loadFromFile(file, type)
        configCache[fileName] = config
        return config
    }
    
    /**
     * 获取缓存的配置 (如果没有则加载)
     */
    fun getCachedConfig(fileName: String): Configuration {
        return configCache.getOrPut(fileName) { loadConfig(fileName) }
    }
    
    /**
     * 保存配置到文件
     */
    fun saveConfig(fileName: String, config: Configuration) {
        val file = getConfigFile(fileName)
        file.parentFile?.mkdirs()
        config.saveToFile(file)
    }
    
    /**
     * 保存配置到文件 (使用缓存的配置)
     */
    fun saveConfig(fileName: String) {
        configCache[fileName]?.let { config ->
            saveConfig(fileName, config)
        }
    }
    
    /**
     * 保存所有缓存的配置
     */
    fun saveAllConfigs() {
        configCache.forEach { (fileName, config) ->
            saveConfig(fileName, config)
        }
    }
    
    /**
     * 重新加载指定配置
     */
    fun reloadConfig(fileName: String): Configuration {
        configCache.remove(fileName)
        return loadConfig(fileName)
    }
    
    /**
     * 重新加载所有缓存的配置
     */
    fun reloadAllConfigs() {
        val fileNames = configCache.keys.toList()
        configCache.clear()
        mainConfig = null
        fileNames.forEach { loadConfig(it) }
    }
    
    // ==================== 资源文件操作 ====================
    
    /**
     * 从JAR资源保存文件 (不覆盖已有)
     * @param resourcePath JAR中的资源路径
     * @param targetName 目标文件名 (默认与资源路径相同)
     * @return true 如果创建了新文件
     */
    fun saveResource(resourcePath: String, targetName: String = resourcePath): Boolean {
        val file = getConfigFile(targetName)
        if (file.exists()) return false
        
        plugin.getResource(resourcePath)?.use { input ->
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                input.copyTo(output)
            }
            return true
        }
        return false
    }
    
    /**
     * 从JAR资源保存文件 (强制覆盖)
     */
    fun saveResourceForce(resourcePath: String, targetName: String = resourcePath): Boolean {
        plugin.getResource(resourcePath)?.use { input ->
            val file = getConfigFile(targetName)
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                input.copyTo(output)
            }
            return true
        }
        return false
    }
    
    /**
     * 从输入流保存配置
     */
    fun saveConfig(fileName: String, inputStream: InputStream): Boolean {
        val file = getConfigFile(fileName)
        if (file.exists()) return false
        
        file.parentFile?.mkdirs()
        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return true
    }
    
    /**
     * 保存默认配置 (从字符串内容)
     */
    fun saveDefaultConfig(fileName: String, defaultContent: String): Boolean {
        val file = getConfigFile(fileName)
        if (file.exists()) return false
        
        file.parentFile?.mkdirs()
        file.writeText(defaultContent, Charsets.UTF_8)
        return true
    }
    
    // ==================== 文件管理 ====================
    
    /**
     * 检查配置文件是否存在
     */
    fun exists(fileName: String): Boolean {
        return getConfigFile(fileName).exists()
    }
    
    /**
     * 删除配置文件
     */
    fun delete(fileName: String): Boolean {
        configCache.remove(fileName)
        return getConfigFile(fileName).delete()
    }
    
    /**
     * 创建子目录
     */
    fun createFolder(folderName: String): File {
        return File(configFolder, folderName).also { it.mkdirs() }
    }
    
    /**
     * 列出所有配置文件
     * @param extension 文件扩展名过滤 (如 "yml", null表示不过滤)
     */
    fun listFiles(extension: String? = null): List<File> {
        return configFolder.listFiles()?.filter { file ->
            file.isFile && (extension == null || file.extension == extension)
        } ?: emptyList()
    }
    
    /**
     * 列出指定目录下的文件
     */
    fun listFiles(folder: String, extension: String? = null): List<File> {
        val dir = File(configFolder, folder)
        return dir.listFiles()?.filter { file ->
            file.isFile && (extension == null || file.extension == extension)
        } ?: emptyList()
    }
    
    /**
     * 递归列出所有配置文件
     */
    fun listAllFiles(extension: String? = null): List<File> {
        return configFolder.walkTopDown().filter { file ->
            file.isFile && (extension == null || file.extension == extension)
        }.toList()
    }
    
    // ==================== 便捷方法 ====================
    
    /**
     * 读取字符串配置
     */
    fun getString(fileName: String, path: String, default: String = ""): String {
        return getCachedConfig(fileName).getString(path) ?: default
    }
    
    /**
     * 读取整数配置
     */
    fun getInt(fileName: String, path: String, default: Int = 0): Int {
        return getCachedConfig(fileName).getInt(path, default)
    }
    
    /**
     * 读取布尔配置
     */
    fun getBoolean(fileName: String, path: String, default: Boolean = false): Boolean {
        return getCachedConfig(fileName).getBoolean(path, default)
    }
    
    /**
     * 读取双精度配置
     */
    fun getDouble(fileName: String, path: String, default: Double = 0.0): Double {
        return getCachedConfig(fileName).getDouble(path, default)
    }
    
    /**
     * 读取字符串列表
     */
    fun getStringList(fileName: String, path: String): List<String> {
        return getCachedConfig(fileName).getStringList(path)
    }
    
    /**
     * 设置配置值
     */
    fun set(fileName: String, path: String, value: Any?) {
        getCachedConfig(fileName).set(path, value)
    }
    
    // ==================== 静态方法 ====================
    
    companion object {
        
        /** 管理器实例缓存 */
        private val instances = mutableMapOf<String, CloudConfigManager>()
        
        /**
         * 获取插件的配置管理器实例
         */
        @JvmStatic
        fun of(plugin: JavaPlugin): CloudConfigManager {
            return instances.getOrPut(plugin.name) { CloudConfigManager(plugin) }
        }
        
        /**
         * 获取插件配置目录 (静态方法)
         */
        @JvmStatic
        fun getPluginConfigFolder(pluginName: String): File {
            return File(CloudCore.configFolder, pluginName).also { it.mkdirs() }
        }
        
        /**
         * 获取插件配置文件 (静态方法)
         */
        @JvmStatic
        fun getPluginConfigFile(pluginName: String, fileName: String): File {
            return File(getPluginConfigFolder(pluginName), fileName)
        }
        
        /**
         * 清理指定插件的配置管理器缓存
         */
        @JvmStatic
        fun clearCache(pluginName: String) {
            instances.remove(pluginName)?.configCache?.clear()
        }
        
        /**
         * 清理所有缓存
         */
        @JvmStatic
        fun clearAllCache() {
            instances.values.forEach { it.configCache.clear() }
            instances.clear()
        }
    }
}
