package com.cloudcore.loader

import com.cloudcore.CloudCore
import com.cloudcore.config.CloudConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bukkit.Bukkit
import org.bukkit.plugin.InvalidDescriptionException
import org.bukkit.plugin.InvalidPluginException
import org.bukkit.plugin.java.JavaPlugin
import taboolib.common.platform.function.console
import taboolib.common.platform.function.info
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.warning
import taboolib.common.platform.function.submit
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import java.util.zip.ZipException

/**
 * 云端插件加载器
 * 负责下载、加载、卸载云端插件
 * 插件插件存放在临时目录，卸载时自动清理
 */
class CloudPluginLoader(
    private val config: CloudConfig,
    private val authManager: AuthManager
) {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeout.toLong(), TimeUnit.SECONDS)
        .readTimeout(config.readTimeout.toLong() * 2, TimeUnit.SECONDS) // 下载需要更长时间
        .build()
    
    /** 已加载的云端插件 */
    private val loadedPlugins = ConcurrentHashMap<String, LoadedCloudPlugin>()
    
    /**
     * 下载并加载插件
     */
    fun downloadAndLoadPlugin(plugin: VerifiedPlugin) {
        try {
            // 下载插件到临时目录
            val jarFile = downloadPlugin(plugin)
            if (jarFile == null) {
                severe("插件下载失败: ${plugin.pluginName}")
                return
            }

            // 在主线程加载插件
            submit {
                loadPlugin(plugin.pluginName, jarFile)
            }
            
        } catch (e: Exception) {
            severe("加载插件失败 [${plugin.pluginName}]: ${e.message}")
            if (config.debug) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 下载插件插件文件
     */
    private fun downloadPlugin(plugin: VerifiedPlugin): File? {
        val downloadUrl = "${config.serverUrl}${plugin.downloadUrl}"
        
        // 插件存放在系统临时目录 (客户看不到)
        val tempFolder = CloudCore.tempFolder
        if (!tempFolder.exists()) {
            tempFolder.mkdirs()
        }
        
        // 使用随机文件名
        val randomName = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32)
        val jarFile = File(tempFolder, "$randomName.jar")

        var lastException: Exception? = null
        val maxRetries = if (config.autoRetry) config.retryCount else 1
        
        repeat(maxRetries) { attempt ->
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        warning("下载失败 (HTTP ${response.code}): ${plugin.pluginName}")
                        warning("响应: ${response.message}")
                        return@use
                    }
                    
                    // 检查 Content-Type
                    val contentType = response.header("Content-Type") ?: ""
                    if (!contentType.contains("application/java-archive") && 
                        !contentType.contains("application/octet-stream") &&
                        !contentType.contains("application/zip")) {
                        warning("服务器返回的不是插件文件, Content-Type: $contentType")
                        // 尝试读取响应内容查看错误
                        val bodyText = response.body?.string()?.take(500) ?: ""
                        warning("响应内容: $bodyText")
                        return@use
                    }
                    
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(jarFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    if (jarFile.exists() && jarFile.length() > 0) {
                        return jarFile
                    } else {
                        warning("下载后文件为空或不存在")
                    }
                }
            } catch (e: Exception) {
                lastException = e
                warning("下载请求失败 (尝试 ${attempt + 1}/$maxRetries): ${e.javaClass.simpleName}: ${e.message}")
                if (attempt < maxRetries - 1) {
                    Thread.sleep(2000L * (attempt + 1))
                }
            }
        }
        
        severe("插件下载失败 [${plugin.pluginName}]: ${lastException?.message}")
        // 清理可能的不完整文件
        jarFile.delete()
        return null
    }
    
    /**
     * 验证插件文件完整性
     */
    private fun validateJarFile(jarFile: File): Boolean {
        try {
            JarFile(jarFile).use { jar ->
                // 检查是否能正常读取插件
                val entries = jar.entries()
                var hasPluginYml = false
                var entryCount = 0
                
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    if (entry.name == "plugin.yml") {
                        hasPluginYml = true
                        // 读取并验证 plugin.yml 内容
                        jar.getInputStream(entry).use { input ->
                            val content = input.bufferedReader().readText()
                            
                            // 检查必要字段
                            if (!content.contains("name:") || !content.contains("main:")) {
                                severe("plugin.yml 缺少必要字段 (name 或 main)")
                                return false
                            }
                            
                            // 检查 version 字段
                            if (!content.contains("version:")) {
                                severe("plugin.yml 缺少 version 字段")
                                return false
                            }
                        }
                    }
                }
                
                if (!hasPluginYml) {
                    severe("插件文件中未找到 plugin.yml")
                    return false
                }
                
                return true
            }
        } catch (e: ZipException) {
            severe("插件文件损坏或格式无效: ${e.message}")
            return false
        } catch (e: Exception) {
            severe("验证插件文件失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 加载插件
     */
    private fun loadPlugin(pluginName: String, jarFile: File) {
        try {
            // 首先验证插件文件完整性
            if (!validateJarFile(jarFile)) {
                severe("插件文件验证失败，尝试重新下载...")
                jarFile.delete()
                return
            }
            
            // 使用Bukkit的插件加载器加载
            val pluginManager = Bukkit.getPluginManager()
            
            val loadedPlugin = pluginManager.loadPlugin(jarFile)
            if (loadedPlugin == null) {
                severe("Bukkit无法加载插件: $pluginName")
                jarFile.delete()
                return
            }

            // 创建插件配置目录 (plugins/CloudCore/Config/插件名)
            val pluginConfigDir = File(CloudCore.configFolder, loadedPlugin.name)
            pluginConfigDir.mkdirs()
            
            // 通过反射修改 dataFolder 到配置目录
            val javaPlugin = loadedPlugin as? JavaPlugin
            if (javaPlugin != null) {
                try {
                    val field = JavaPlugin::class.java.getDeclaredField("dataFolder")
                    field.isAccessible = true
                    
                    // 移除 final 修饰符
                    val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                    modifiersField.isAccessible = true
                    modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())
                    
                    field.set(javaPlugin, pluginConfigDir)
                } catch (e: Exception) {
                    // Java 12+ 使用 VarHandle
                    try {
                        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
                        unsafeField.isAccessible = true
                        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
                        val field = JavaPlugin::class.java.getDeclaredField("dataFolder")
                        val offset = unsafe.objectFieldOffset(field)
                        unsafe.putObject(javaPlugin, offset, pluginConfigDir)
                    } catch (e2: Exception) {
                        warning("无法修改dataFolder: ${e2.message}")
                    }
                }
            }
            
            // 启用插件
            pluginManager.enablePlugin(loadedPlugin)
                        
            // 记录已加载的插件
            loadedPlugins[pluginName] = LoadedCloudPlugin(
                name = pluginName,
                jarFile = jarFile,
                plugin = loadedPlugin as? JavaPlugin,
                configDir = pluginConfigDir
            )
                        
            // 输出加载状态
            console().sendMessage("§a已成功注入：")
            val status = if (loadedPlugin.isEnabled) "§a成功" else "§c失败"
            console().sendMessage("§e${loadedPlugin.name}§7—§b${loadedPlugin.description.version} $status")

            // 清理Config目录中不属于云端插件的内容
            cleanConfigFolder()
            
        } catch (e: InvalidDescriptionException) {
            severe("插件描述文件无效 [$pluginName]: ${e.message}")
            // 输出更多调试信息
            severe("请检查插件的 plugin.yml 是否符合 Bukkit 规范")
            if (config.debug) {
                e.printStackTrace()
            }
        } catch (e: InvalidPluginException) {
            severe("插件格式无效 [$pluginName]: ${e.message}")
            e.cause?.let { cause ->
                severe("原因: ${cause.javaClass.simpleName}: ${cause.message}")
                // 如果是类加载问题，给出提示
                if (cause is ClassNotFoundException || cause is NoClassDefFoundError) {
                    severe("可能缺少依赖，请确保插件依赖已正确安装")
                }
            }
            if (config.debug) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            severe("加载插件失败 [$pluginName]: ${e.javaClass.simpleName}: ${e.message}")
            if (config.debug) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 卸载指定插件
     */
    fun unloadPlugin(pluginName: String) {
        val loadedPlugin = loadedPlugins.remove(pluginName) ?: return
        
        try {
            loadedPlugin.plugin?.let { plugin ->
                // 禁用插件
                if (plugin.isEnabled) {
                    Bukkit.getPluginManager().disablePlugin(plugin)
                }
                
                // 从Bukkit内部完全移除插件
                unloadFromBukkit(plugin)
            }
        } catch (e: Exception) {
            warning("卸载插件失败 [$pluginName]: ${e.message}")
            if (config.debug) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 从Bukkit内部完全移除插件
     */
    @Suppress("UNCHECKED_CAST")
    private fun unloadFromBukkit(plugin: org.bukkit.plugin.Plugin) {
        val pluginManager = Bukkit.getPluginManager()
        val pluginName = plugin.name
        
        try {
            // 1. 从PluginManager.plugins列表移除
            val pluginsField = pluginManager.javaClass.getDeclaredField("plugins")
            pluginsField.isAccessible = true
            val plugins = pluginsField.get(pluginManager) as MutableList<org.bukkit.plugin.Plugin>
            plugins.remove(plugin)
            
            // 2. 从PluginManager.lookupNames移除
            val lookupNamesField = pluginManager.javaClass.getDeclaredField("lookupNames")
            lookupNamesField.isAccessible = true
            val lookupNames = lookupNamesField.get(pluginManager) as MutableMap<String, org.bukkit.plugin.Plugin>
            lookupNames.remove(pluginName.lowercase())
        } catch (e: Exception) {
            warning("移除插件失败: ${e.message}")
        }
        
        try {
            // 3. 清除插件注册的命令
            val commandMapField = pluginManager.javaClass.getDeclaredField("commandMap")
            commandMapField.isAccessible = true
            val commandMap = commandMapField.get(pluginManager) as org.bukkit.command.SimpleCommandMap
            
            val knownCommandsField = org.bukkit.command.SimpleCommandMap::class.java.getDeclaredField("knownCommands")
            knownCommandsField.isAccessible = true
            val knownCommands = knownCommandsField.get(commandMap) as MutableMap<String, org.bukkit.command.Command>
            
            // 移除该插件的所有命令
            val toRemove = knownCommands.entries.filter { (_, cmd) ->
                cmd is org.bukkit.command.PluginCommand && 
                cmd.plugin == plugin
            }.map { it.key }
            
            toRemove.forEach { knownCommands.remove(it) }
            
            if (toRemove.isNotEmpty()) {
            }
        } catch (e: Exception) {
            warning("清除失败: ${e.message}")
        }
        
        try {
            // 4. 关闭PluginClassLoader
            val classLoader = plugin.javaClass.classLoader
            
            // 尝试关闭 URLClassLoader/PluginClassLoader
            if (classLoader is java.net.URLClassLoader) {
                classLoader.close()
            } else if (classLoader is java.io.Closeable) {
                classLoader.close()
            }
            
            // 尝试从 PluginClassLoader 中清除 plugin 引用
            try {
                val pluginField = classLoader.javaClass.getDeclaredField("plugin")
                pluginField.isAccessible = true
                pluginField.set(classLoader, null)
            } catch (_: Exception) {}
            
            try {
                val pluginInitField = classLoader.javaClass.getDeclaredField("pluginInit")
                pluginInitField.isAccessible = true
                pluginInitField.set(classLoader, null)
            } catch (_: Exception) {}
            
        } catch (e: Exception) {
            warning("关闭ClassLoader失败: ${e.message}")
        }
    }
    
    /**
     * 卸载所有云端插件
     */
    fun unloadAllPlugins() {
        val pluginNames = loadedPlugins.keys.toList()
        for (name in pluginNames) {
            unloadPlugin(name)
        }
    }
    
    /**
     * 获取已加载的插件列表
     */
    fun getLoadedPlugins(): List<LoadedCloudPlugin> = loadedPlugins.values.toList()
    
    /**
     * 检查插件是否已加载
     */
    fun isPluginLoaded(pluginName: String): Boolean = loadedPlugins.containsKey(pluginName)
    
    /**
     * 获取插件的配置目录
     */
    fun getPluginConfigDir(pluginName: String): File {
        return File(CloudCore.configFolder, pluginName).also { it.mkdirs() }
    }
    
    /**
     * 清理Config目录中不属于云端插件的内容
     */
    private fun cleanConfigFolder() {
        val configFolder = CloudCore.configFolder
        if (!configFolder.exists()) return
        
        val loadedPluginNames = loadedPlugins.keys.toSet()
        
        configFolder.listFiles()?.forEach { file ->
            if (file.name !in loadedPluginNames) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
        }
    }
}

/**
 * 已加载的云端插件信息
 */
data class LoadedCloudPlugin(
    val name: String,
    val jarFile: File,
    val plugin: JavaPlugin?,
    val configDir: File
)
