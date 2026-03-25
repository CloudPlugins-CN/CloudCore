package com.cloudcore.command

import com.cloudcore.CloudCore
import com.cloudcore.util.DeviceInfo
import org.bukkit.command.CommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.expansion.createHelper

/**
 * CloudCore 命令
 */
@CommandHeader(name = "cloudcore", aliases = ["cc"], permission = "cloudcore.admin")
object CloudCoreCommand {
    
    @CommandBody
    val main = mainCommand {
        createHelper()
    }
    
    /**
     * 重载配置
     * 只重新检查授权，不会重新下载插件
     * 如果授权失效会卸载插件
     */
    @CommandBody
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendMessage("§a[CloudCore] §f正在重新加载配置...")
            
            // 重新加载配置文件
            CloudCore.reload()
            
            // 只验证授权，不重新下载插件
            CloudCore.authManager.verifyAuthOnly()
            
            sender.sendMessage("§a[CloudCore] §f配置重载完成！")
        }
    }
    
    /**
     * 查看状态
     */
    @CommandBody
    val status = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendMessage("§a========== CloudCore ==========")
            sender.sendMessage("§7服务器地址: §f${CloudCore.cloudConfig.serverUrl}")
            sender.sendMessage("§7用户名: §f${CloudCore.cloudConfig.username}")
            sender.sendMessage("§7授权码数量: §f${CloudCore.cloudConfig.licenseCodes.size}")
            sender.sendMessage("")
            
            val verifiedPlugins = CloudCore.authManager.getVerifiedPlugins()
            sender.sendMessage("§7已验证插件: §f${verifiedPlugins.size}")
            verifiedPlugins.forEach { plugin ->
                sender.sendMessage("  §a- §f${plugin.pluginName} §7v${plugin.pluginVersion}")
            }
            
            val loadedPlugins = CloudCore.pluginLoader.getLoadedPlugins()
            sender.sendMessage("")
            sender.sendMessage("§7已加载插件: §f${loadedPlugins.size}")
            loadedPlugins.forEach { plugin ->
                val status = if (plugin.plugin?.isEnabled == true) "§a运行中" else "§c已停止"
                sender.sendMessage("  §a- §f${plugin.name} $status")
            }
            sender.sendMessage("§a====================================")
        }
    }
    
    /**
     * 查看设备信息
     */
    @CommandBody
    val device = subCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendMessage("§a========== 设备信息 ==========")
            sender.sendMessage("§7IP地址: §f${DeviceInfo.getIP() ?: "获取失败"}")
            sender.sendMessage("§7MAC地址: §f${DeviceInfo.getMAC() ?: "获取失败"}")
            sender.sendMessage("§7机器码: §f${DeviceInfo.getMachineCode() ?: "获取失败"}")
            sender.sendMessage("§a==============================")
        }
    }
    
    /**
     * 列出所有云端插件
     */
    @CommandBody
    val list = subCommand {
        execute<CommandSender> { sender, _, _ ->
            val plugins = CloudCore.pluginLoader.getLoadedPlugins()
            if (plugins.isEmpty()) {
                sender.sendMessage("§a[CloudCore] §f当前没有加载任何云端插件")
                return@execute
            }
            
            sender.sendMessage("§a[CloudCore] §f已加载的云端插件:")
            plugins.forEach { plugin ->
                val status = if (plugin.plugin?.isEnabled == true) "§a[运行中]" else "§c[已停止]"
                sender.sendMessage("  §7- §f${plugin.name} $status")
                sender.sendMessage("    §7JAR: §f${plugin.jarFile.name}")
                sender.sendMessage("    §7配置: §f${plugin.configDir.absolutePath}")
            }
        }
    }
    

}
