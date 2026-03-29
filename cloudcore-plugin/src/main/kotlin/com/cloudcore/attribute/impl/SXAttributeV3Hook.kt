package com.cloudcore.attribute.impl

import com.cloudcore.attribute.AttributeHook
import github.saukiya.sxattribute.SXAttribute
import github.saukiya.sxattribute.data.attribute.SXAttributeData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.warning

/**
 * SX-Attribute 属性插件适配器 (v3)
 * API类: github.saukiya.sxattribute.api.SXAPI
 * 使用 loadListData (V3特有方法)
 */
class SXAttributeV3Hook : AttributeHook {
    
    override val pluginName: String = "SX-Attribute"
    
    // 通过反射获取 V3 的 SXAPI 特有方法
    private val sxapiClass by lazy {
        try {
            Class.forName("github.saukiya.sxattribute.api.SXAPI")
        } catch (e: Exception) {
            null
        }
    }
    
    private val loadListDataMethod by lazy {
        try {
            sxapiClass?.getMethod("loadListData", List::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    override fun isAvailable(): Boolean {
        if (Bukkit.getPluginManager().getPlugin(pluginName)?.isEnabled != true) {
            return false
        }
        // 检测是否为 v3 版本 (通过检测 v3 特有的 SXAPI 类和 loadListData 方法)
        return sxapiClass != null && loadListDataMethod != null
    }
    
    override fun addAttribute(player: Player, source: String, attributes: List<String>) {
        if (!isAvailable()) {
            warning("$pluginName 插件未加载，无法添加属性")
            return
        }
        try {
            val api = SXAttribute.getApi()
            val data = loadListDataMethod!!.invoke(api, attributes)
            api.setEntityAPIData(SXAttributeV3Hook::class.java, player.uniqueId, data as SXAttributeData)
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
            api.removeEntityAPIData(SXAttributeV3Hook::class.java, player.uniqueId)
        } catch (e: Exception) {
            warning("移除属性时发生错误: ${e.message}")
        }
    }
}
