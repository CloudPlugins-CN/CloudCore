package com.cloudcore.attribute.impl

import com.cloudcore.attribute.AttributeHook
import github.saukiya.sxattribute.SXAttribute
import github.saukiya.sxattribute.data.condition.SXConditionType
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.warning

/**
 * SX-Attribute 属性插件适配器 (v2)
 */
class SXAttributeV2Hook : AttributeHook {
    
    override val pluginName: String = "SX-Attribute"
    
    override fun isAvailable(): Boolean {
        if (Bukkit.getPluginManager().getPlugin(pluginName)?.isEnabled != true) {
            return false
        }
        // 检测是否为 v2 版本 (通过检测 v2 特有的 SXAttributeAPI 类)
        return try {
            Class.forName("github.saukiya.sxattribute.api.SXAttributeAPI")
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
            val api = SXAttribute.getApi()
            val data = api.getLoreData(player, SXConditionType.ALL, attributes)
            api.setEntityAPIData(SXAttributeV2Hook::class.java, player.uniqueId, data)
        } catch (e: Exception) {
            warning("添加属性时发生错误: ${e.message}")
        }
    }
    
    override fun removeAttribute(player: Player, source: String) {
        if (!isAvailable()) {
            return
        }
        try {
            val api = SXAttribute.getApi()
            api.removeEntityAPIData(SXAttributeV2Hook::class.java, player.uniqueId)
        } catch (e: Exception) {
            warning("移除属性时发生错误: ${e.message}")
        }
    }
}
