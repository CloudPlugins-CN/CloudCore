package com.cloudcore.api

import com.cloudcore.CloudCore
import java.io.File

/**
 * CloudCore API
 * 供云端加载的插件使用
 */
object CloudCoreAPI {

    /**
     * 检查插件是否已授权
     */
    fun isAuthorized(pluginName: String): Boolean {
        return CloudCore.authManager.isPluginAuthorized(pluginName)
    }

    /**
     * 获取CloudCore数据目录
     */
    fun getDataFolder(): File = CloudCore.dataFolder

    /**
     * 获取CloudCore临时目录
     */
    fun getTempFolder(): File = CloudCore.tempFolder

    /**
     * 信息日志
     */
    fun info(pluginName: String, message: String) {
        taboolib.common.platform.function.info("[$pluginName] $message")
    }

    /**
     * 警告日志
     */
    fun warning(pluginName: String, message: String) {
        taboolib.common.platform.function.warning("[$pluginName] $message")
    }

    /**
     * 错误日志
     */
    fun severe(pluginName: String, message: String) {
        taboolib.common.platform.function.severe("[$pluginName] $message")
    }
}
