package com.cloudcore.attribute.impl

import cn.org.bukkit.craneattribute.api.AttributeAPI
import com.cloudcore.attribute.AttributeHook
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.warning

/**
 * CraneAttribute 属性插件适配器
 */
class CraneAttributeHook : AttributeHook {

    override val pluginName: String = "CraneAttribute"

    override fun isAvailable(): Boolean {
        return Bukkit.getPluginManager().getPlugin(pluginName)?.isEnabled == true
    }

    override fun addAttribute(player: Player, source: String, attributes: List<String>) {
        if (!isAvailable()) {
            warning("$pluginName 插件未加载，无法添加属性")
            return
        }
        try {
            val attrData = AttributeAPI.getAttrData(player)
            AttributeAPI.addAttributeSource(attrData, source, attributes)
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
            AttributeAPI.removeAttributeSource(attrData, source)
        } catch (e: Exception) {
            warning("移除属性时发生错误: ${e.message}")
        }
    }
}
