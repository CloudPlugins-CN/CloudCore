package com.cloudcore.attribute

import org.bukkit.entity.Player

/**
 * 属性钩子接口
 * 适配不同属性插件
 */
interface AttributeHook {
    
    /**
     * 插件名称
     */
    val pluginName: String
    
    /**
     * 检查属性插件是否可用
     */
    fun isAvailable(): Boolean
    
    /**
     * 添加属性
     * @param player 目标玩家
     * @param source 属性来源标识
     * @param attributes 属性列表
     */
    fun addAttribute(player: Player, source: String, attributes: List<String>)
    
    /**
     * 移除属性
     * @param player 目标玩家
     * @param source 属性来源标识
     */
    fun removeAttribute(player: Player, source: String)
    
    /**
     * 更新属性（先移除再添加）
     * @param player 目标玩家
     * @param source 属性来源标识
     * @param attributes 属性列表
     */
    fun updateAttribute(player: Player, source: String, attributes: List<String>) {
        removeAttribute(player, source)
        if (attributes.isNotEmpty()) {
            addAttribute(player, source, attributes)
        }
    }
}
