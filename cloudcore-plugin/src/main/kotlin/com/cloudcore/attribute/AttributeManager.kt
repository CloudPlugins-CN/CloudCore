package com.cloudcore.attribute

import com.cloudcore.attribute.impl.AttributePlusV2Hook
import com.cloudcore.attribute.impl.AttributePlusV3Hook
import com.cloudcore.attribute.impl.SXAttributeV2Hook
import com.cloudcore.attribute.impl.SXAttributeV3Hook
import org.bukkit.entity.Player
import taboolib.common.platform.function.console
import taboolib.common.platform.function.warning

/**
 * 属性管理器
 * 统一管理所有属性插件的适配器
 */
object AttributeManager {
    
    /** 已注册的所有属性钩子 */
    private val hooks = mutableListOf<AttributeHook>()
    
    /** 当前使用的有效钩子 */
    private var activeHook: AttributeHook? = null
    
    /**
     * 初始化属性系统
     * 自动检测并注册可用的属性插件
     */
    fun init() {
        hooks.clear()
        activeHook = null
        
        // 注册所有属性插件适配器（按优先级排序）
        val allHooks = listOf(
            AttributePlusV3Hook(),  // AttributePlus v3
            AttributePlusV2Hook(),  // AttributePlus v2
            SXAttributeV3Hook(),    // SX-Attribute v3
            SXAttributeV2Hook()     // SX-Attribute v2
        )
        
        for (hook in allHooks) {
            hooks.add(hook)
            if (hook.isAvailable() && activeHook == null) {
                activeHook = hook
                console().sendMessage("§a[CloudCore] 已接入属性插件: §e${hook.pluginName}")
            }
        }
        
        if (activeHook == null) {
            warning("§e[CloudCore] 未检测到可用的属性插件，属性功能将不可用")
            warning("§e[CloudCore] 支持的属性插件: AttributePlus(v2/v3), SX-Attribute(v2/v3), CraneAttribute")
        }
    }
    
    /**
     * 获取当前使用的属性插件名称
     */
    fun getActivePluginName(): String? = activeHook?.pluginName
    
    /**
     * 检查属性系统是否可用
     */
    fun isAvailable(): Boolean = activeHook?.isAvailable() ?: false
    
    /**
     * 添加属性
     * @param player 目标玩家
     * @param source 属性来源标识
     * @param attributes 属性列表
     */
    fun addAttribute(player: Player, source: String, attributes: List<String>) {
        if (attributes.isEmpty()) return
        activeHook?.addAttribute(player, source, attributes)
    }
    
    /**
     * 移除属性
     * @param player 目标玩家
     * @param source 属性来源标识
     */
    fun removeAttribute(player: Player, source: String) {
        activeHook?.removeAttribute(player, source)
    }
    
    /**
     * 更新属性
     * @param player 目标玩家
     * @param source 属性来源标识
     * @param attributes 属性列表
     */
    fun updateAttribute(player: Player, source: String, attributes: List<String>) {
        activeHook?.updateAttribute(player, source, attributes)
    }

    
    /**
     * 获取所有已注册的属性钩子（用于调试）
     */
    fun getAllHooks(): List<AttributeHook> = hooks.toList()
}
