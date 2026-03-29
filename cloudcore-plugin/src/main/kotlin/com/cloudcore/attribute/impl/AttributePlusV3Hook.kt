package com.cloudcore.attribute.impl

import com.cloudcore.attribute.AttributeHook
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.serverct.ersha.api.AttributeAPI
import taboolib.common.platform.function.warning

/**
 * AttributePlus 属性插件适配器 (v3)
 */
class AttributePlusV3Hook : AttributeHook {

    override val pluginName: String = "AttributePlus"

    override fun isAvailable(): Boolean {
        if (Bukkit.getPluginManager().getPlugin(pluginName)?.isEnabled != true) {
            return false
        }
        // 检测是否为 v3 版本
        return try {
            Class.forName("org.serverct.ersha.api.AttributeAPI")
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun addAttribute(player: Player, source: String, attributes: List<String>) {
        if (!isAvailable()) {
            warning("$pluginName 插件未加载，无法添加属性")
            return
        }
        try {
            val attrData = AttributeAPI.getAttrData(player)
            AttributeAPI.addSourceAttribute(attrData, source, attributes)
        } catch (e: Exception) {
            warning("添加属性时发生错误: ${e.message}")
        }
    }

    override fun removeAttribute(player: Player, source: String) {
        if (!isAvailable()) {
            return
        }
        try {
            val attrData = AttributeAPI.getAttrData(player)
            AttributeAPI.takeSourceAttribute(attrData, source)
        } catch (e: Exception) {
            warning("移除属性时发生错误: ${e.message}")
        }
    }
}
