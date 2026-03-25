package com.cloudcore.loader

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.cloudcore.CloudCore
import com.cloudcore.config.CloudConfig
import com.cloudcore.util.DeviceInfo
import com.cloudcore.util.DeviceInfoData
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import taboolib.common.platform.function.info
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.warning
import taboolib.common.platform.function.submitAsync
import java.util.concurrent.TimeUnit

/**
 * 授权管理器
 * 负责与授权服务器通信，验证授权码
 */
class AuthManager(private val config: CloudConfig) {
    
    private val gson = Gson()
    private val httpClient: OkHttpClient
    
    /** 已验证的插件信息 */
    private val verifiedPlugins = mutableMapOf<String, VerifiedPlugin>()
    
    init {
        httpClient = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.readTimeout.toLong(), TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * 更新配置（用于 reload）
     */
    fun updateConfig(newConfig: CloudConfig) {
        // 只需要更新配置引用，HTTP Client 会使用新配置的超时设置
    }
    
    /**
     * 验证授权并加载插件
     */
    fun verifyAndLoadPlugins() {
        submitAsync {
            val deviceInfo = DeviceInfo.getAllInfo()
            if (config.debug) {
                DeviceInfo.printInfo()
            }
            
            var successCount = 0
            var failCount = 0
            
            for (licenseCode in config.licenseCodes) {
                val result = verifySingleLicense(licenseCode, deviceInfo)
                if (result != null) {
                    verifiedPlugins[licenseCode] = result

                    // 下载并加载插件
                    CloudCore.pluginLoader.downloadAndLoadPlugin(result)
                    successCount++
                } else {
                    failCount++
                }
            }
        }
    }
    
    /**
     * 只验证授权，不重新下载插件
     * 如果授权失效会卸载插件
     */
    fun verifyAuthOnly() {
        submitAsync {
            val deviceInfo = DeviceInfo.getAllInfo()
            
            // 检查已验证的插件，移除授权失效的
            val invalidLicenses = mutableListOf<String>()
            
            for ((licenseCode, verifiedPlugin) in verifiedPlugins) {
                val result = verifySingleLicense(licenseCode, deviceInfo)
                if (result == null) {
                    invalidLicenses.add(licenseCode)
                    warning("插件 ${verifiedPlugin.pluginName} 授权已失效，正在卸载...")
                }
            }
            
            // 卸载授权失效的插件
            invalidLicenses.forEach { licenseCode ->
                verifiedPlugins.remove(licenseCode)
                val plugin = CloudCore.pluginLoader.getLoadedPlugins().find { 
                    it.name == licenseCode 
                }
                if (plugin != null) {
                    CloudCore.pluginLoader.unloadPlugin(plugin.name)
                }
            }
            
            if (invalidLicenses.isEmpty()) {
                info("所有插件授权验证通过")
            } else {
                warning("共有 ${invalidLicenses.size} 个插件因授权失效被卸载")
            }
        }
    }
    
    /**
     * 验证单个授权码
     */
    private fun verifySingleLicense(licenseCode: String, deviceInfo: DeviceInfoData): VerifiedPlugin? {
        val url = "${config.serverUrl}/api/client/verify"
        
        val requestBody = mapOf(
            "username" to config.username,
            "licenseCode" to licenseCode,
            "ip" to deviceInfo.ip,
            "mac" to deviceInfo.mac,
            "machineCode" to deviceInfo.machineCode
        )
        
        var lastException: Exception? = null
        val maxRetries = if (config.autoRetry) config.retryCount else 1
        
        repeat(maxRetries) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use
                    val json = gson.fromJson(body, JsonObject::class.java)
                    
                    if (json.get("valid")?.asBoolean == true) {
                        return VerifiedPlugin(
                            licenseCode = licenseCode,
                            pluginName = json.get("pluginName")?.asString ?: "",
                            pluginVersion = json.get("pluginVersion")?.asString ?: "",
                            downloadUrl = json.get("downloadUrl")?.asString ?: ""
                        )
                    } else {
                        val message = json.get("message")?.asString ?: "未知错误"
                        // 检查是否是封禁错误
                        if (message.contains("封禁")) {
                            severe("§c当前账号已被封禁,如有异议请联系管理员")
                        } else {
                            warning("授权验证失败 [$licenseCode]: $message")
                        }
                        return null
                    }
                }
            } catch (e: Exception) {
                lastException = e
                if (config.debug) {
                    warning("验证请求失败 (尝试 ${attempt + 1}/$maxRetries): ${e.message}")
                }
                if (attempt < maxRetries - 1) {
                    Thread.sleep(1000L * (attempt + 1)) // 递增延迟
                }
            }
        }
        
        severe("授权验证请求失败 [$licenseCode]: ${lastException?.message}")
        return null
    }
    
    /**
     * 获取已验证的插件列表
     */
    fun getVerifiedPlugins(): List<VerifiedPlugin> = verifiedPlugins.values.toList()

    /**
     * 根据插件名称获取已验证的插件信息
     */
    fun getVerifiedPlugin(pluginName: String): VerifiedPlugin? {
        return verifiedPlugins.values.find { it.pluginName == pluginName }
    }
    
    /**
     * 检查插件是否已授权
     */
    fun isPluginAuthorized(pluginName: String): Boolean {
        return verifiedPlugins.values.any { it.pluginName == pluginName }
    }
    
    /**
     * 获取插件的下载URL
     */
    fun getPluginDownloadUrl(pluginName: String): String? {
        return verifiedPlugins.values.find { it.pluginName == pluginName }?.downloadUrl
    }
}

/**
 * 已验证的插件信息
 */
data class VerifiedPlugin(
    val licenseCode: String,
    val pluginName: String,
    val pluginVersion: String,
    val downloadUrl: String
)
