package com.cloudcore.attribute.impl

import com.cloudcore.attribute.AttributeHook
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.serverct.ersha.AttributePlus
import org.serverct.ersha.jd.AttributeAPI
import taboolib.common.platform.function.warning

/**
 * AttributePlus 属性插件适配器 (v2)
 */
class AttributePlusV2Hook : AttributeHook {

    override val pluginName: String = "AttributePlus"

    override fun isAvailable(): Boolean {
        if (Bukkit.getPluginManager().getPlugin(pluginName)?.isEnabled != true) {
            return false
        }
        // 检测是否为 v2 版本
        return try {
            Class.forName("org.serverct.ersha.jd.AttributeAPI")
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
            AttributeAPI.addAttribute(player, source, attributes, false)
        } catch (e: Exception) {
            warning("添加属性时发生错误: ${e.message}")
        }
    }

    override fun removeAttribute(player: Player, source: String) {
        if (!isAvailable()) {
            return
        }
        try {
            AttributeAPI.deleteAttribute(player, source)
        } catch (e: Exception) {
            warning("移除属性时发生错误: ${e.message}")
        }
    }
}
