package com.yangsu.service

import com.yangsu.config.DatabaseFactory.dbQuery
import com.yangsu.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AuthService {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * 验证授权并绑定设备
     * 绑定逻辑: IP/MAC/机器码 三选二匹配即可通过验证
     */
    suspend fun verify(request: VerifyRequest): VerifyResponse = dbQuery {
        // 1. 查找用户
        val user = Users.selectAll()
            .where { Users.username eq request.username }
            .singleOrNull()
            
        if (user == null) {
            return@dbQuery VerifyResponse(false, message = "用户不存在")
        }
        
        // 2. 检查用户是否被封禁
        if (user[Users.banned]) {
            return@dbQuery VerifyResponse(false, message = "账号已被封禁")
        }
        
        // 3. 查找授权码
        val license = LicenseCodes.selectAll()
            .where { LicenseCodes.code eq request.licenseCode }
            .singleOrNull()
            
        if (license == null) {
            return@dbQuery VerifyResponse(false, message = "授权码不存在")
        }
        
        // 3. 检查授权码是否启用
        if (!license[LicenseCodes.enabled]) {
            return@dbQuery VerifyResponse(false, message = "授权码已禁用")
        }
        
        // 4. 检查授权码是否属于该用户
        val licenseUserId = license[LicenseCodes.userId]?.value
        if (licenseUserId != user[Users.id].value) {
            return@dbQuery VerifyResponse(false, message = "授权码不属于该用户")
        }
        
        // 5. 检查是否过期
        val expiresAt = license[LicenseCodes.expiresAt]
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return@dbQuery VerifyResponse(false, message = "授权码已过期")
        }
        
        // 6. 获取插件信息
        val plugin = Plugins.selectAll()
            .where { Plugins.id eq license[LicenseCodes.pluginId] }
            .singleOrNull()
            
        if (plugin == null || !plugin[Plugins.enabled]) {
            return@dbQuery VerifyResponse(false, message = "插件不存在或已禁用")
        }
        
        val licenseId = license[LicenseCodes.id].value
        val maxBindings = license[LicenseCodes.maxBindings]
        
        // 7. 检查设备绑定
        val existingBindings = DeviceBindings.selectAll()
            .where { DeviceBindings.licenseId eq licenseId }
            .toList()
            
        val now = LocalDateTime.now()
        
        // 如果已有绑定, 检查是否匹配 (三选二)
        if (existingBindings.isNotEmpty()) {
            val matchedBinding = existingBindings.find { binding ->
                matchDevice(
                    binding[DeviceBindings.ip],
                    binding[DeviceBindings.mac],
                    binding[DeviceBindings.machineCode],
                    request.ip,
                    request.mac,
                    request.machineCode
                )
            }
            
            if (matchedBinding != null) {
                // 匹配到已有设备，更新验证时间
                DeviceBindings.update({ DeviceBindings.id eq matchedBinding[DeviceBindings.id] }) {
                    it[lastVerified] = now
                    request.ip?.let { ip -> it[DeviceBindings.ip] = ip }
                    request.mac?.let { mac -> it[DeviceBindings.mac] = mac }
                    request.machineCode?.let { mc -> it[machineCode] = mc }
                }
            } else {
                // 未匹配，检查是否还能添加新设备
                if (maxBindings > 0 && existingBindings.size >= maxBindings) {
                    // 已达到最大绑定数
                    return@dbQuery VerifyResponse(false, message = "设备绑定数已达上限(${maxBindings})，请先解绑其他设备")
                }
                // 还有名额，添加新设备绑定
                DeviceBindings.insert {
                    it[DeviceBindings.licenseId] = licenseId
                    it[ip] = request.ip
                    it[mac] = request.mac
                    it[machineCode] = request.machineCode
                    it[lastVerified] = now
                    it[createdAt] = now
                }
            }
        } else {
            // 首次绑定
            DeviceBindings.insert {
                it[DeviceBindings.licenseId] = licenseId
                it[ip] = request.ip
                it[mac] = request.mac
                it[machineCode] = request.machineCode
                it[lastVerified] = now
                it[createdAt] = now
            }
        }
        
        // 构建下载URL (对应 ClientRoutes 中的 /api/client/download)
        val downloadUrl = "/api/client/download/${plugin[Plugins.name]}/${plugin[Plugins.version]}"
        
        VerifyResponse(
            valid = true,
            pluginName = plugin[Plugins.name],
            pluginVersion = plugin[Plugins.version],
            downloadUrl = downloadUrl,
            message = "验证成功"
        )
    }
    
    /**
     * 设备匹配逻辑: 三选二匹配
     * IP/MAC/机器码 中有两个匹配即可通过
     */
    private fun matchDevice(
        boundIp: String?, boundMac: String?, boundMachineCode: String?,
        requestIp: String?, requestMac: String?, requestMachineCode: String?
    ): Boolean {
        var matchCount = 0
        
        if (!boundIp.isNullOrBlank() && !requestIp.isNullOrBlank() && boundIp == requestIp) {
            matchCount++
        }
        if (!boundMac.isNullOrBlank() && !requestMac.isNullOrBlank() && 
            boundMac.equals(requestMac, ignoreCase = true)) {
            matchCount++
        }
        if (!boundMachineCode.isNullOrBlank() && !requestMachineCode.isNullOrBlank() && 
            boundMachineCode == requestMachineCode) {
            matchCount++
        }
        
        return matchCount >= 2
    }
    
    /**
     * 管理员解绑指定用户的所有设备
     */
    suspend fun adminUnbindUser(userId: Int): Int = dbQuery {
        // 查找该用户的所有授权码
        val licenseIds = LicenseCodes.selectAll()
            .where { LicenseCodes.userId eq userId }
            .map { it[LicenseCodes.id].value }
        
        if (licenseIds.isEmpty()) return@dbQuery 0
        
        // 删除这些授权码的所有绑定
        var total = 0
        licenseIds.forEach { licenseId ->
            total += DeviceBindings.deleteWhere { DeviceBindings.licenseId eq licenseId }
        }
        total
    }
    
    /**
     * 管理员解绑所有用户的所有设备
     */
    suspend fun adminUnbindAll(): Int = dbQuery {
        DeviceBindings.deleteAll()
    }
    
    /**
     * 解绑设备
     */
    suspend fun unbind(licenseCode: String): Result<Boolean> = dbQuery {
        val license = LicenseCodes.selectAll()
            .where { LicenseCodes.code eq licenseCode }
            .singleOrNull()
            
        if (license == null) {
            return@dbQuery Result.failure(Exception("授权码不存在"))
        }
        
        val deleted = DeviceBindings.deleteWhere { 
            DeviceBindings.licenseId eq license[LicenseCodes.id] 
        }
        
        Result.success(deleted > 0)
    }
    
    /**
     * 用户解绑单个设备（检查冷却时间）
     * 冷却检查: 设备绑定时间 和 上次解绑全部时间，取较晚的一个
     */
    suspend fun userUnbind(userId: Int, bindingId: Int): Result<Boolean> = dbQuery {
        // 查找绑定记录
        val binding = DeviceBindings.selectAll()
            .where { DeviceBindings.id eq bindingId }
            .singleOrNull()
            
        if (binding == null) {
            return@dbQuery Result.failure(Exception("绑定记录不存在"))
        }
        
        // 检查该绑定是否属于该用户
        val licenseId = binding[DeviceBindings.licenseId].value
        val license = LicenseCodes.selectAll()
            .where { LicenseCodes.id eq licenseId }
            .singleOrNull()
            
        if (license == null || license[LicenseCodes.userId]?.value != userId) {
            return@dbQuery Result.failure(Exception("无权解绑此设备"))
        }
        
        // 获取用户信息，检查上次解绑全部时间
        val user = Users.selectAll()
            .where { Users.id eq userId }
            .singleOrNull()
        val lastUnbindAllAt = user?.get(Users.lastUnbindAllAt)
        
        // 检查冷却时间
        val cooldownHours = getUnbindCooldownHours()
        val bindingCreatedAt = binding[DeviceBindings.createdAt]
        
        // 冷却基准时间: 设备绑定时间 和 上次解绑全部时间，取较晚的
        val cooldownBaseTime = if (lastUnbindAllAt != null && lastUnbindAllAt.isAfter(bindingCreatedAt)) {
            lastUnbindAllAt
        } else {
            bindingCreatedAt
        }
        val cooldownEnd = cooldownBaseTime.plusHours(cooldownHours.toLong())
        
        if (LocalDateTime.now().isBefore(cooldownEnd)) {
            val remaining = java.time.Duration.between(LocalDateTime.now(), cooldownEnd)
            val hours = remaining.toHours()
            val minutes = remaining.toMinutes() % 60
            return@dbQuery Result.failure(Exception("解绑冷却中，请在 ${hours}小时${minutes}分钟 后再试"))
        }
        
        // 执行解绑
        val deleted = DeviceBindings.deleteWhere { DeviceBindings.id eq bindingId }
        Result.success(deleted > 0)
    }
    
    /**
     * 用户解绑自己的所有设备（会触发全局冷却）
     */
    suspend fun userUnbindAll(userId: Int): Result<Int> = dbQuery {
        // 查找该用户的所有授权码
        val licenseIds = LicenseCodes.selectAll()
            .where { LicenseCodes.userId eq userId }
            .map { it[LicenseCodes.id].value }
        
        if (licenseIds.isEmpty()) {
            return@dbQuery Result.success(0)
        }
        
        // 删除这些授权码的所有绑定
        var total = 0
        licenseIds.forEach { licenseId ->
            total += DeviceBindings.deleteWhere { DeviceBindings.licenseId eq licenseId }
        }
        
        // 更新用户的上次解绑全部时间
        Users.update({ Users.id eq userId }) {
            it[lastUnbindAllAt] = LocalDateTime.now()
        }
        
        Result.success(total)
    }
    
    /**
     * 获取解绑冷却时间(小时)
     */
    fun getUnbindCooldownHours(): Int {
        return SystemConfig.selectAll()
            .where { SystemConfig.key eq "unbind_cooldown_hours" }
            .singleOrNull()?.get(SystemConfig.value)?.toIntOrNull() ?: 24
    }
    
    /**
     * 设置解绑冷却时间(小时)
     */
    suspend fun setUnbindCooldownHours(hours: Int): Boolean = dbQuery {
        val exists = SystemConfig.selectAll()
            .where { SystemConfig.key eq "unbind_cooldown_hours" }
            .count() > 0
            
        if (exists) {
            SystemConfig.update({ SystemConfig.key eq "unbind_cooldown_hours" }) {
                it[value] = hours.toString()
                it[updatedAt] = LocalDateTime.now()
            } > 0
        } else {
            SystemConfig.insert {
                it[key] = "unbind_cooldown_hours"
                it[value] = hours.toString()
                it[updatedAt] = LocalDateTime.now()
            }
            true
        }
    }
    
    /**
     * 获取统计信息
     */
    suspend fun getStats(): StatsDTO = dbQuery {
        val totalUsers = Users.selectAll()
            .where { Users.isAdmin eq false }
            .count()
        val totalPlugins = Plugins.selectAll().count()
        val totalLicenses = LicenseCodes.selectAll().count()
        val activeLicenses = LicenseCodes.selectAll()
            .where { LicenseCodes.enabled eq true }
            .count()
        val unbindCooldown = getUnbindCooldownHours()
            
        StatsDTO(
            totalUsers = totalUsers,
            totalPlugins = totalPlugins,
            totalLicenses = totalLicenses,
            activeLicenses = activeLicenses,
            unbindCooldownHours = unbindCooldown
        )
    }
}
