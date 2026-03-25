package com.yangsu.service

import com.yangsu.config.DatabaseFactory.dbQuery
import com.yangsu.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object LicenseService {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val random = SecureRandom()
    
    // 授权码字符集: 英文大小写 + 数字 + 特殊符号
    private const val LICENSE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#\$%&*"
    private const val LICENSE_LENGTH = 20
    
    /**
     * 生成随机授权码 (20位，包含英文、数字、特殊符号)
     */
    fun generateLicenseCode(): String {
        return (1..LICENSE_LENGTH)
            .map { LICENSE_CHARS[random.nextInt(LICENSE_CHARS.length)] }
            .joinToString("")
    }
    
    /**
     * 批量生成授权码
     * - 指定用户名：直接授权给该用户，限制绑定数量
     * - 用户名留空：生成通用授权码，所有人可用，不限制绑定数量
     */
    suspend fun generateLicenses(request: GenerateLicenseRequest): Result<List<LicenseDTO>> = dbQuery {
        // 检查插件是否存在
        val plugin = Plugins.selectAll()
            .where { Plugins.id eq request.pluginId }
            .singleOrNull()
        
        if (plugin == null) {
            return@dbQuery Result.failure(Exception("插件不存在"))
        }
        
        // 判断是否为通用授权码（用户留空）
        val isUniversal = request.username.isNullOrBlank()
        
        // 如果指定了用户名，查找用户
        var targetUserId: Int? = null
        if (!isUniversal) {
            val targetUser = Users.selectAll()
                .where { Users.username eq request.username }
                .singleOrNull()
            
            if (targetUser == null) {
                return@dbQuery Result.failure(Exception("用户 '${request.username}' 不存在"))
            }
            targetUserId = targetUser[Users.id].value
        }
        
        val now = LocalDateTime.now()
        val expiresAt = request.expiresInDays?.let { now.plusDays(it.toLong()) }
        // 通用授权码不限制绑定数量(maxBindings=0)，指定用户则使用设置的绑定数
        val actualMaxBindings = if (isUniversal) 0 else request.maxBindings
        
        // 通用授权码支持自定义授权码
        val useCustomCode = isUniversal && !request.customCode.isNullOrBlank()
        if (useCustomCode) {
            val exists = LicenseCodes.select(LicenseCodes.code)
                .where { LicenseCodes.code eq request.customCode!! }
                .count() > 0
            if (exists) {
                return@dbQuery Result.failure(Exception("授权码 '${request.customCode}' 已存在"))
            }
        }
        
        val licenses = mutableListOf<LicenseDTO>()
        
        repeat(request.count) { index ->
            val code: String = if (useCustomCode && index == 0) {
                request.customCode!!
            } else {
                var generatedCode: String
                do {
                    generatedCode = generateLicenseCode()
                    val exists = LicenseCodes.select(LicenseCodes.code)
                        .where { LicenseCodes.code eq generatedCode }
                        .count() > 0
                } while (exists)
                generatedCode
            }
            
            val licenseId = LicenseCodes.insertAndGetId {
                it[LicenseCodes.code] = code
                it[userId] = targetUserId
                it[pluginId] = request.pluginId
                it[LicenseCodes.expiresAt] = expiresAt
                it[maxBindings] = actualMaxBindings
                it[enabled] = true
                it[createdAt] = now
                it[usedAt] = if (targetUserId != null) now else null
            }
            
            // 如果指定了用户，创建用户-插件授权关系
            if (targetUserId != null) {
                UserPluginAuth.insert {
                    it[UserPluginAuth.userId] = targetUserId
                    it[UserPluginAuth.pluginId] = request.pluginId
                    it[UserPluginAuth.licenseId] = licenseId.value
                    it[grantedAt] = now
                    it[grantedBy] = targetUserId
                }
            }
            
            licenses.add(LicenseDTO(
                id = licenseId.value,
                code = code,
                pluginId = request.pluginId,
                pluginName = plugin[Plugins.name],
                pluginDisplayName = plugin[Plugins.displayName],
                userId = targetUserId,
                username = request.username,
                expiresAt = expiresAt?.format(dateFormatter),
                maxBindings = actualMaxBindings,
                currentBindings = 0,
                enabled = true,
                createdAt = now.format(dateFormatter),
                usedAt = if (targetUserId != null) now.format(dateFormatter) else null
            ))
        }
        
        Result.success(licenses)
    }
    
    /**
     * 为用户授权插件
     */
    suspend fun grantLicense(request: GrantLicenseRequest, adminId: Int): Result<Boolean> = dbQuery {
        // 查找用户
        val user = Users.selectAll()
            .where { Users.username eq request.username }
            .singleOrNull()
            
        if (user == null) {
            return@dbQuery Result.failure(Exception("用户不存在"))
        }
        
        // 查找授权码
        val license = LicenseCodes.selectAll()
            .where { LicenseCodes.code eq request.licenseCode }
            .singleOrNull()
            
        if (license == null) {
            return@dbQuery Result.failure(Exception("授权码不存在"))
        }
        
        if (!license[LicenseCodes.enabled]) {
            return@dbQuery Result.failure(Exception("授权码已禁用"))
        }
        
        if (license[LicenseCodes.userId] != null) {
            return@dbQuery Result.failure(Exception("授权码已被使用"))
        }
        
        val pluginId = license[LicenseCodes.pluginId].value
        val userId = user[Users.id].value
        
        val now = LocalDateTime.now()
        
        // 绑定授权码到用户
        LicenseCodes.update({ LicenseCodes.id eq license[LicenseCodes.id] }) {
            it[LicenseCodes.userId] = userId
            it[usedAt] = now
        }
        
        // 创建用户-插件授权关系
        UserPluginAuth.insert {
            it[UserPluginAuth.userId] = userId
            it[UserPluginAuth.pluginId] = pluginId
            it[licenseId] = license[LicenseCodes.id].value
            it[grantedAt] = now
            it[grantedBy] = adminId
        }
        
        Result.success(true)
    }
    
    /**
     * 获取所有授权码
     */
    suspend fun getAllLicenses(): List<LicenseDTO> = dbQuery {
        (LicenseCodes innerJoin Plugins)
            .leftJoin(Users, { LicenseCodes.userId }, { Users.id })
            .selectAll()
            .map { row ->
                val licenseId = row[LicenseCodes.id].value
                val bindingCount = DeviceBindings.selectAll()
                    .where { DeviceBindings.licenseId eq licenseId }
                    .count()
                    
                LicenseDTO(
                    id = licenseId,
                    code = row[LicenseCodes.code],
                    pluginId = row[LicenseCodes.pluginId].value,
                    pluginName = row[Plugins.name],
                    pluginDisplayName = row[Plugins.displayName],
                    userId = row[LicenseCodes.userId]?.value,
                    username = row.getOrNull(Users.username),
                    expiresAt = row[LicenseCodes.expiresAt]?.format(dateFormatter),
                    maxBindings = row[LicenseCodes.maxBindings],
                    currentBindings = bindingCount.toInt(),
                    enabled = row[LicenseCodes.enabled],
                    createdAt = row[LicenseCodes.createdAt].format(dateFormatter),
                    usedAt = row[LicenseCodes.usedAt]?.format(dateFormatter)
                )
            }
    }
    
    /**
     * 获取用户的授权信息
     */
    suspend fun getUserAuthInfo(userId: Int): UserAuthInfo = dbQuery {
        val auths = UserPluginAuth
            .innerJoin(LicenseCodes, { UserPluginAuth.licenseId }, { LicenseCodes.id })
            .innerJoin(Plugins, { LicenseCodes.pluginId }, { Plugins.id })
            .selectAll()
            .where { UserPluginAuth.userId eq userId }
            .map { row ->
                val licenseId = row[LicenseCodes.id].value
                val bindings = DeviceBindings.selectAll()
                    .where { DeviceBindings.licenseId eq licenseId }
                    .map { binding ->
                        DeviceBindingDTO(
                            id = binding[DeviceBindings.id].value,
                            ip = binding[DeviceBindings.ip],
                            mac = binding[DeviceBindings.mac],
                            machineCode = binding[DeviceBindings.machineCode],
                            lastVerified = binding[DeviceBindings.lastVerified].format(dateFormatter),
                            createdAt = binding[DeviceBindings.createdAt].format(dateFormatter)
                        )
                    }
                    
                UserPluginInfo(
                    pluginId = row[Plugins.id].value,
                    pluginName = row[Plugins.name],
                    displayName = row[Plugins.displayName],
                    version = row[Plugins.version],
                    licenseCode = row[LicenseCodes.code],
                    expiresAt = row[LicenseCodes.expiresAt]?.format(dateFormatter),
                    bindings = bindings
                )
            }
            
        UserAuthInfo(plugins = auths)
    }
    
    /**
     * 禁用/启用授权码
     */
    suspend fun toggleLicense(licenseId: Int, enabled: Boolean): Boolean = dbQuery {
        LicenseCodes.update({ LicenseCodes.id eq licenseId }) {
            it[LicenseCodes.enabled] = enabled
        } > 0
    }
    
    /**
     * 修改授权码最大绑定数量
     */
    suspend fun updateMaxBindings(licenseId: Int, maxBindings: Int): Boolean = dbQuery {
        LicenseCodes.update({ LicenseCodes.id eq licenseId }) {
            it[LicenseCodes.maxBindings] = maxBindings
        } > 0
    }
    
    /**
     * 删除授权码
     * 同时删除关联的设备绑定和用户授权记录
     */
    suspend fun deleteLicense(licenseId: Int): Boolean = dbQuery {
        // 检查授权码是否存在
        val exists = LicenseCodes.selectAll()
            .where { LicenseCodes.id eq licenseId }
            .count() > 0
        
        if (!exists) {
            return@dbQuery false
        }
        
        // 删除设备绑定记录
        DeviceBindings.deleteWhere { DeviceBindings.licenseId eq licenseId }
        
        // 删除用户授权记录
        UserPluginAuth.deleteWhere { UserPluginAuth.licenseId eq licenseId }
        
        // 删除授权码
        LicenseCodes.deleteWhere { LicenseCodes.id eq licenseId } > 0
    }
    
    /**
     * 根据授权码获取信息
     */
    suspend fun getLicenseByCode(code: String): LicenseDTO? = dbQuery {
        (LicenseCodes innerJoin Plugins)
            .leftJoin(Users, { LicenseCodes.userId }, { Users.id })
            .selectAll()
            .where { LicenseCodes.code eq code }
            .singleOrNull()?.let { row ->
                val licenseId = row[LicenseCodes.id].value
                val bindingCount = DeviceBindings.selectAll()
                    .where { DeviceBindings.licenseId eq licenseId }
                    .count()
                    
                LicenseDTO(
                    id = licenseId,
                    code = row[LicenseCodes.code],
                    pluginId = row[LicenseCodes.pluginId].value,
                    pluginName = row[Plugins.name],
                    pluginDisplayName = row[Plugins.displayName],
                    userId = row[LicenseCodes.userId]?.value,
                    username = row.getOrNull(Users.username),
                    expiresAt = row[LicenseCodes.expiresAt]?.format(dateFormatter),
                    maxBindings = row[LicenseCodes.maxBindings],
                    currentBindings = bindingCount.toInt(),
                    enabled = row[LicenseCodes.enabled],
                    createdAt = row[LicenseCodes.createdAt].format(dateFormatter),
                    usedAt = row[LicenseCodes.usedAt]?.format(dateFormatter)
                )
            }
    }
    
    /**
     * 删除用户的所有授权码
     * @return 删除的授权码数量
     */
    suspend fun deleteUserLicenses(userId: Int): Int = dbQuery {
        // 获取该用户的所有授权码ID
        val licenseIds = LicenseCodes.selectAll()
            .where { LicenseCodes.userId eq userId }
            .map { it[LicenseCodes.id].value }
        
        if (licenseIds.isEmpty()) return@dbQuery 0
        
        var deletedCount = 0
        licenseIds.forEach { licenseId ->
            // 删除设备绑定记录
            DeviceBindings.deleteWhere { DeviceBindings.licenseId eq licenseId }
            
            // 删除用户授权记录
            UserPluginAuth.deleteWhere { UserPluginAuth.licenseId eq licenseId }
            
            // 删除授权码
            deletedCount += LicenseCodes.deleteWhere { LicenseCodes.id eq licenseId }
        }
        
        deletedCount
    }
}
