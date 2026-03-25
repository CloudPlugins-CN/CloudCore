package com.cloudcore.util

import taboolib.common.platform.function.info
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest

/**
 * 设备信息采集工具
 * 采集 IP、MAC地址、机器码 用于授权绑定
 */
object DeviceInfo {
    
    /**
     * 获取本机IP地址
     */
    fun getIP(): String? {
        return try {
            // 尝试获取非回环的IP地址
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr.hostAddress.contains(":")) continue // 跳过IPv6
                    if (!addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            // 如果找不到，返回本机地址
            InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取MAC地址
     */
    fun getMAC(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                
                val mac = iface.hardwareAddress
                if (mac != null && mac.isNotEmpty()) {
                    return mac.joinToString("-") { "%02X".format(it) }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取机器码
     * 基于多种系统信息生成唯一标识
     */
    fun getMachineCode(): String? {
        return try {
            val sb = StringBuilder()
            
            // 操作系统信息
            sb.append(System.getProperty("os.name", ""))
            sb.append(System.getProperty("os.arch", ""))
            
            // 用户信息
            sb.append(System.getProperty("user.name", ""))
            
            // 处理器信息
            sb.append(Runtime.getRuntime().availableProcessors())
            
            // 尝试获取更多硬件信息
            when {
                isWindows() -> {
                    // Windows: 获取主板序列号
                    getWindowsSerialNumber()?.let { sb.append(it) }
                }
                isLinux() -> {
                    // Linux: 获取machine-id
                    getLinuxMachineId()?.let { sb.append(it) }
                }
                isMac() -> {
                    // Mac: 获取硬件UUID
                    getMacHardwareUUID()?.let { sb.append(it) }
                }
            }
            
            // MAC地址作为辅助
            getMAC()?.let { sb.append(it) }
            
            // 生成哈希
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(sb.toString().toByteArray())
            hash.take(16).joinToString("") { "%02x".format(it) }.uppercase()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")
    private fun isLinux(): Boolean = System.getProperty("os.name").lowercase().contains("linux")
    private fun isMac(): Boolean = System.getProperty("os.name").lowercase().contains("mac")
    
    /**
     * Windows: 获取主板序列号
     */
    private fun getWindowsSerialNumber(): String? {
        return try {
            val process = Runtime.getRuntime().exec("wmic baseboard get serialnumber")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() // 跳过标题行
            reader.readLine()?.trim()?.takeIf { it.isNotBlank() && it != "To be filled by O.E.M." }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Linux: 获取machine-id
     */
    private fun getLinuxMachineId(): String? {
        return try {
            val file = File("/etc/machine-id")
            if (file.exists()) {
                file.readText().trim()
            } else {
                val dbus = File("/var/lib/dbus/machine-id")
                if (dbus.exists()) dbus.readText().trim() else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Mac: 获取硬件UUID
     */
    private fun getMacHardwareUUID(): String? {
        return try {
            val process = Runtime.getRuntime().exec("system_profiler SPHardwareDataType")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("Hardware UUID")) {
                    return line!!.split(":").getOrNull(1)?.trim()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取所有设备信息
     */
    fun getAllInfo(): DeviceInfoData {
        return DeviceInfoData(
            ip = getIP(),
            mac = getMAC(),
            machineCode = getMachineCode()
        )
    }
    
    /**
     * 打印设备信息 (调试用)
     */
    fun printInfo() {
        info("========== 设备信息 ==========")
        info("IP地址: ${getIP() ?: "获取失败"}")
        info("MAC地址: ${getMAC() ?: "获取失败"}")
        info("机器码: ${getMachineCode() ?: "获取失败"}")
        info("==============================")
    }
}

/**
 * 设备信息数据类
 */
data class DeviceInfoData(
    val ip: String?,
    val mac: String?,
    val machineCode: String?
)
