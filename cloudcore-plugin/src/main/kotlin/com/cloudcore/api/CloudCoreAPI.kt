package com.cloudcore.api

import com.cloudcore.CloudCore
import com.cloudcore.attribute.AttributeManager
import org.bukkit.entity.Player
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

    // ==================== 属性系统 API ====================

    /**
     * 检查属性系统是否可用
     * @return 是否有可用的属性插件
     */
    fun isAttributeAvailable(): Boolean = AttributeManager.isAvailable()

    /**
     * 获取当前使用的属性插件名称
     * @return 属性插件名称，如 "AttributePlus", "SX-Attribute" 等
     */
    fun getAttributePluginName(): String? = AttributeManager.getActivePluginName()

    /**
     * 为玩家添加属性
     * @param player 目标玩家
     * @param source 属性来源标识（用于区分不同来源的属性）
     * @param attributes 属性列表，如 ["攻击力: +10", "防御力: +5"]
     */
    fun addAttribute(player: Player, source: String, attributes: List<String>) {
        AttributeManager.addAttribute(player, source, attributes)
    }

    /**
     * 为玩家添加属性（可变参数版本）
     * @param player 目标玩家
     * @param source 属性来源标识
     * @param attributes 属性列表
     */
    fun addAttribute(player: Player, source: String, vararg attributes: String) {
        AttributeManager.addAttribute(player, source, attributes.toList())
    }

    /**
     * 移除玩家的指定来源属性
     * @param player 目标玩家
     * @param source 属性来源标识
     */
    fun removeAttribute(player: Player, source: String) {
        AttributeManager.removeAttribute(player, source)
    }

    /**
     * 更新玩家的属性（先移除再添加）
     * @param player 目标玩家
     * @param source 属性来源标识
     * @param attributes 新的属性列表
     */
    fun updateAttribute(player: Player, source: String, attributes: List<String>) {
        AttributeManager.updateAttribute(player, source, attributes)
    }

    /**
     * 更新玩家的属性（可变参数版本）
     * @param player 目标玩家
     * @param source 属性来源标识
     * @param attributes 新的属性列表
     */
    fun updateAttribute(player: Player, source: String, vararg attributes: String) {
        AttributeManager.updateAttribute(player, source, attributes.toList())
    }
}
