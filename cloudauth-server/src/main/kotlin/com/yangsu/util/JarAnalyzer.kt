package com.yangsu.util

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.jar.JarFile

/**
 * JAR文件解析工具
 * 从Bukkit插件JAR中读取plugin.yml信息
 */
object JarAnalyzer {
    
    private val yaml = Yaml()
    
    /**
     * 从JAR文件中解析plugin.yml
     * @param jarFile JAR文件
     * @return 插件信息，如果解析失败返回null
     */
    fun parsePluginYml(jarFile: File): PluginYmlInfo? {
        if (!jarFile.exists()) return null
        
        return try {
            JarFile(jarFile).use { jar ->
                val entry = jar.getJarEntry("plugin.yml") 
                    ?: jar.getJarEntry("paper-plugin.yml")  // 支持Paper插件
                    ?: return null
                
                jar.getInputStream(entry).use { input ->
                    val data = yaml.load<Map<String, Any?>>(input)
                    
                    // 处理作者字段，支持单个author或authors列表
                    val authorValue = when {
                        data["author"] != null -> data["author"].toString()
                        data["authors"] != null -> {
                            when (val authors = data["authors"]) {
                                is List<*> -> authors.filterNotNull().joinToString("、")
                                is String -> authors
                                else -> null
                            }
                        }
                        else -> null
                    }
                    
                    PluginYmlInfo(
                        name = data["name"]?.toString() ?: return null,
                        version = data["version"]?.toString() ?: "1.0.0",
                        main = data["main"]?.toString() ?: "",
                        description = data["description"]?.toString(),
                        author = authorValue
                    )
                }
            }
        } catch (e: Exception) {
            println("解析plugin.yml失败: ${e.message}")
            null
        }
    }
    
    /**
     * 从字节数组解析plugin.yml (用于上传流)
     */
    fun parsePluginYmlFromBytes(bytes: ByteArray): PluginYmlInfo? {
        val tempFile = File.createTempFile("plugin_", ".jar")
        return try {
            tempFile.writeBytes(bytes)
            parsePluginYml(tempFile)
        } finally {
            tempFile.delete()
        }
    }
}

/**
 * plugin.yml 解析结果
 */
data class PluginYmlInfo(
    val name: String,
    val version: String,
    val main: String,
    val description: String?,
    val author: String?
)
