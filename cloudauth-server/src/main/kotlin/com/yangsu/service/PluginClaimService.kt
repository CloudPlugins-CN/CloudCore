package com.yangsu.service

import com.yangsu.config.DatabaseFactory.dbQuery
import com.yangsu.model.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 插件领取配置服务
 */
object PluginClaimService {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * 创建领取配置（管理员）
     */
    fun createClaimConfig(request: CreateClaimConfigRequest): Result<ClaimConfigDTO> = dbQuery {
        // 检查插件是否存在
        val plugin = Plugins.selectAll().where { Plugins.id eq request.pluginId }.singleOrNull()
        if (plugin == null) {
            return@dbQuery Result.failure(Exception("插件不存在"))
        }
        
        // 检查需要的插件是否存在（如果指定了）
        if (request.requiredPluginId != null) {
            val requiredPlugin = Plugins.selectAll().where { Plugins.id eq request.requiredPluginId }.singleOrNull()
            if (requiredPlugin == null) {
                return@dbQuery Result.failure(Exception("要求的插件不存在"))
            }
        }
        
        // 检查是否已存在相同的领取配置
        val exists = PluginClaimConfigs.selectAll()
            .where { PluginClaimConfigs.pluginId eq request.pluginId }
            .count() > 0
        
        if (exists) {
            return@dbQuery Result.failure(Exception("该插件的领取配置已存在"))
        }
        
        val now = LocalDateTime.now()
        val excludeIdsStr = request.excludePluginIds.joinToString(",")
        val configId = PluginClaimConfigs.insertAndGetId {
            it[pluginId] = request.pluginId
            it[requiredPluginId] = request.requiredPluginId
            it[requiredAuthCount] = request.requiredAuthCount
            it[excludePluginIds] = excludeIdsStr
            it[freePluginNotCount] = request.freePluginNotCount
            it[enabled] = request.enabled
            it[createdAt] = now
        }
        
        // 获取插件信息
        val pluginName = plugin[Plugins.name]
        val pluginDisplayName = plugin[Plugins.displayName]
        
        // 获取要求的插件信息
        val requiredPluginInfo = request.requiredPluginId?.let { reqId ->
            Plugins.selectAll().where { Plugins.id eq reqId }.singleOrNull()
        }
        
        Result.success(ClaimConfigDTO(
            id = configId.value,
            pluginId = request.pluginId,
            pluginName = pluginName,
            pluginDisplayName = pluginDisplayName,
            requiredPluginId = request.requiredPluginId,
            requiredPluginName = requiredPluginInfo?.get(Plugins.name),
            requiredPluginDisplayName = requiredPluginInfo?.get(Plugins.displayName),
            requiredAuthCount = request.requiredAuthCount,
            excludePluginIds = request.excludePluginIds,
            freePluginNotCount = request.freePluginNotCount,
            enabled = request.enabled,
            createdAt = now.format(dateFormatter)
        ))
    }
    
    /**
     * 获取所有领取配置（管理员）
     */
    fun getAllClaimConfigs(): List<ClaimConfigDTO> = dbQuery {
        // 获取所有领取配置
        val configs = PluginClaimConfigs
            .selectAll()
            .orderBy(PluginClaimConfigs.createdAt to SortOrder.DESC)
            .toList()
        
        // 获取所有插件信息用于映射
        val allPlugins = Plugins.selectAll().toList().associateBy { it[Plugins.id].value }
        
        configs.mapNotNull { config ->
            val pluginId = config[PluginClaimConfigs.pluginId].value
            val requiredPluginId = config[PluginClaimConfigs.requiredPluginId]?.value
            val plugin = allPlugins[pluginId]
            
            if (plugin == null) {
                return@mapNotNull null
            }
            
            val requiredPlugin = requiredPluginId?.let { allPlugins[it] }
            // 处理可能没有 excludePluginIds 字段的旧数据
            val excludeIdsStr = try {
                config[PluginClaimConfigs.excludePluginIds]
            } catch (e: Exception) {
                ""
            }
            val excludeIds = if (excludeIdsStr.isBlank()) emptyList() else excludeIdsStr.split(",").map { it.toInt() }
            
            // 处理可能没有 freePluginNotCount 字段的旧数据
            val freePluginNotCount = try {
                config[PluginClaimConfigs.freePluginNotCount]
            } catch (e: Exception) {
                false
            }
            
            ClaimConfigDTO(
                id = config[PluginClaimConfigs.id].value,
                pluginId = pluginId,
                pluginName = plugin[Plugins.name],
                pluginDisplayName = plugin[Plugins.displayName],
                requiredPluginId = requiredPluginId,
                requiredPluginName = requiredPlugin?.get(Plugins.name),
                requiredPluginDisplayName = requiredPlugin?.get(Plugins.displayName),
                requiredAuthCount = config[PluginClaimConfigs.requiredAuthCount],
                excludePluginIds = excludeIds,
                freePluginNotCount = freePluginNotCount,
                enabled = config[PluginClaimConfigs.enabled],
                createdAt = config[PluginClaimConfigs.createdAt].format(dateFormatter)
            )
        }
    }
    
    /**
     * 获取启用的领取配置（用户可见）
     */
    fun getEnabledClaimConfigs(userId: Int): List<UserClaimablePluginDTO> = dbQuery {
        // 获取用户当前拥有的所有授权码
        val userLicenses = UserPluginAuth
            .innerJoin(LicenseCodes, { UserPluginAuth.licenseId }, { LicenseCodes.id })
            .select(UserPluginAuth.licenseId, LicenseCodes.pluginId)
            .where { UserPluginAuth.userId eq userId }
            .distinct()
            .toList()
        
        val totalCount = userLicenses.size
        val ownedPluginIds = userLicenses.map { it[LicenseCodes.pluginId].value }.toSet()
        
        // 获取用户已领取的插件 ID
        val claimedPluginIds = UserPluginAuth.selectAll()
            .where { UserPluginAuth.userId eq userId }
            .map { it[UserPluginAuth.pluginId].value }
            .toSet()
        
        // 获取所有启用的领取配置
        val configs = PluginClaimConfigs
            .selectAll()
            .where { PluginClaimConfigs.enabled eq true }
            .toList()
        
        // 获取所有插件信息
        val allPlugins = Plugins.selectAll().toList().associateBy { it[Plugins.id].value }
        
        configs.mapNotNull { config ->
            val pluginId = config[PluginClaimConfigs.pluginId].value
            val plugin = allPlugins[pluginId] ?: return@mapNotNull null
            
            // 用户已领取过该插件
            if (claimedPluginIds.contains(pluginId)) {
                return@mapNotNull null
            }
            
            val requiredPluginId = config[PluginClaimConfigs.requiredPluginId]?.value
            val requiredAuthCount = config[PluginClaimConfigs.requiredAuthCount]
            // 处理可能没有 excludePluginIds 字段的旧数据
            val excludeIdsStr = try {
                config[PluginClaimConfigs.excludePluginIds]
            } catch (e: Exception) {
                ""
            }
            val excludePluginIds = if (excludeIdsStr.isBlank()) emptySet() else excludeIdsStr.split(",").map { it.toInt() }.toSet()
            
            // 处理可能没有 freePluginNotCount 字段的旧数据
            val freePluginNotCount = try {
                config[PluginClaimConfigs.freePluginNotCount]
            } catch (e: Exception) {
                false
            }
            
            // 计算排除指定插件后的授权码数量
            var validLicenses = userLicenses.filter { it[LicenseCodes.pluginId].value !in excludePluginIds }
            
            // 如果设置了免费插件不计入，过滤掉价格为0的插件
            if (freePluginNotCount) {
                validLicenses = validLicenses.filter { license ->
                    val pluginId = license[LicenseCodes.pluginId].value
                    val pluginPrice = allPlugins[pluginId]?.get(Plugins.price) ?: java.math.BigDecimal.ZERO
                    pluginPrice > java.math.BigDecimal.ZERO
                }
            }
            
            val validCount = validLicenses.size
            val validOwnedPluginIds = validLicenses.map { it[LicenseCodes.pluginId].value }.toSet()
            
            // 检查用户是否满足领取条件
            val meetsRequirement = if (requiredPluginId != null) {
                // 需要拥有指定插件（排除的插件不算）
                validOwnedPluginIds.contains(requiredPluginId)
            } else {
                // 只需要达到授权码数量要求（排除的插件不算，免费插件可能不计入）
                validCount >= requiredAuthCount
            }
            
            UserClaimablePluginDTO(
                id = pluginId,
                name = plugin[Plugins.name],
                displayName = plugin[Plugins.displayName],
                description = plugin[Plugins.description] ?: "",
                version = plugin[Plugins.version],
                requiredAuthCount = requiredAuthCount,
                requiredPluginId = requiredPluginId,
                requiredPluginName = requiredPluginId?.let { allPlugins[it]?.get(Plugins.name) } ?: "",
                excludePluginIds = excludePluginIds.toList(),
                claimed = false,
                canClaim = meetsRequirement,
                configId = config[PluginClaimConfigs.id].value
            )
        }
    }
    
    /**
     * 切换领取配置启用状态
     */
    fun toggleClaimConfig(id: Int, isEnabled: Boolean): Boolean = dbQuery {
        val config = PluginClaimConfigs.selectAll()
            .where { PluginClaimConfigs.id eq id }
            .singleOrNull()
        
        if (config == null) {
            return@dbQuery false
        }
        
        PluginClaimConfigs.update({ PluginClaimConfigs.id eq id }) {
            it[enabled] = isEnabled
        }
        true
    }
    
    /**
     * 更新领取配置（管理员）
     */
    fun updateClaimConfig(id: Int, request: UpdateClaimConfigRequest): Result<ClaimConfigDTO> = dbQuery {
        val config = PluginClaimConfigs.selectAll()
            .where { PluginClaimConfigs.id eq id }
            .singleOrNull()
        
        if (config == null) {
            return@dbQuery Result.failure(Exception("领取配置不存在"))
        }
        
        // 如果要更新插件ID，检查插件是否存在
        val pluginId = request.pluginId ?: config[PluginClaimConfigs.pluginId].value
        val requiredPluginId = request.requiredPluginId ?: config[PluginClaimConfigs.requiredPluginId]?.value
        
        if (request.pluginId != null) {
            val plugin = Plugins.selectAll().where { Plugins.id eq pluginId }.singleOrNull()
            if (plugin == null) {
                return@dbQuery Result.failure(Exception("插件不存在"))
            }
            
            // 检查是否已存在相同的领取配置（排除当前配置）
            val exists = PluginClaimConfigs.selectAll()
                .where { 
                    (PluginClaimConfigs.pluginId eq pluginId) and
                    (PluginClaimConfigs.id neq id)
                }
                .count() > 0
            
            if (exists) {
                return@dbQuery Result.failure(Exception("该插件的领取配置已存在"))
            }
        }
        
        if (request.requiredPluginId != null) {
            val requiredPlugin = Plugins.selectAll().where { Plugins.id eq requiredPluginId }.singleOrNull()
            if (requiredPlugin == null) {
                return@dbQuery Result.failure(Exception("要求的插件不存在"))
            }
        }
        
        // 更新配置 - 使用原始SQL避免类型问题
        val updates = mutableListOf<String>()
        request.pluginId?.let { updates.add("plugin_id = $it") }
        request.requiredPluginId?.let { updates.add("required_plugin_id = $it") } ?: run { updates.add("required_plugin_id = NULL") }
        request.requiredAuthCount?.let { updates.add("required_auth_count = $it") }
        request.excludePluginIds?.let { updates.add("exclude_plugin_ids = '${it.joinToString(",")}'") }
        request.freePluginNotCount?.let { updates.add("free_plugin_not_count = ${if (it) 1 else 0}") }
        request.enabled?.let { updates.add("enabled = ${if (it) 1 else 0}") }
        
        if (updates.isNotEmpty()) {
            TransactionManager.current().exec("UPDATE plugin_claim_configs SET ${updates.joinToString(", ")} WHERE id = $id")
        }
        
        // 查询更新后的数据
        val updatedConfig = PluginClaimConfigs.selectAll()
            .where { PluginClaimConfigs.id eq id }
            .singleOrNull()
        
        if (updatedConfig == null) {
            return@dbQuery Result.failure(Exception("更新失败"))
        }
        
        val plugin = Plugins.selectAll().where { Plugins.id eq pluginId }.singleOrNull()
            ?: return@dbQuery Result.failure(Exception("插件信息不完整"))
        
        val requiredPlugin = requiredPluginId?.let {
            Plugins.selectAll().where { Plugins.id eq it }.singleOrNull()
        }
        
        // 处理可能没有 excludePluginIds 字段的旧数据
        val excludeIdsStr = try {
            updatedConfig[PluginClaimConfigs.excludePluginIds]
        } catch (e: Exception) {
            ""
        }
        val excludeIds = if (excludeIdsStr.isBlank()) emptyList() else excludeIdsStr.split(",").map { it.toInt() }
        
        // 处理可能没有 freePluginNotCount 字段的旧数据
        val freePluginNotCount = try {
            updatedConfig[PluginClaimConfigs.freePluginNotCount]
        } catch (e: Exception) {
            false
        }
        
        Result.success(ClaimConfigDTO(
            id = updatedConfig[PluginClaimConfigs.id].value,
            pluginId = pluginId,
            pluginName = plugin[Plugins.name],
            pluginDisplayName = plugin[Plugins.displayName],
            requiredPluginId = requiredPluginId,
            requiredPluginName = requiredPlugin?.get(Plugins.name),
            requiredPluginDisplayName = requiredPlugin?.get(Plugins.displayName),
            requiredAuthCount = request.requiredAuthCount ?: updatedConfig[PluginClaimConfigs.requiredAuthCount],
            excludePluginIds = request.excludePluginIds ?: excludeIds,
            freePluginNotCount = request.freePluginNotCount ?: freePluginNotCount,
            enabled = request.enabled ?: updatedConfig[PluginClaimConfigs.enabled],
            createdAt = updatedConfig[PluginClaimConfigs.createdAt].format(dateFormatter)
        ))
    }
    
    /**
     * 删除领取配置
     */
    fun deleteClaimConfig(id: Int): Boolean = dbQuery {
        PluginClaimConfigs.deleteWhere { PluginClaimConfigs.id eq id } > 0
    }
    
    /**
     * 用户领取插件 - 返回领取结果和邮件信息
     */
    fun claimPlugin(userId: Int, configId: Int): Result<ClaimResult> = dbQuery {
        // 获取领取配置
        val config = PluginClaimConfigs.selectAll()
            .where { PluginClaimConfigs.id eq configId }
            .singleOrNull()
        
        if (config == null) {
            return@dbQuery Result.failure(Exception("领取配置不存在"))
        }
        
        if (!config[PluginClaimConfigs.enabled]) {
            return@dbQuery Result.failure(Exception("该领取配置已禁用"))
        }
        
        val pluginId = config[PluginClaimConfigs.pluginId].value
        val requiredPluginId = config[PluginClaimConfigs.requiredPluginId]?.value
        val requiredAuthCount = config[PluginClaimConfigs.requiredAuthCount]
        
        // 处理可能没有 freePluginNotCount 字段的旧数据
        val freePluginNotCount = try {
            config[PluginClaimConfigs.freePluginNotCount]
        } catch (e: Exception) {
            false
        }
        
        // 获取插件信息
        val plugin = Plugins.selectAll().where { Plugins.id eq pluginId }.singleOrNull()
            ?: return@dbQuery Result.failure(Exception("插件不存在"))
        
        // 检查用户是否已经领取过该插件
        val alreadyClaimed = UserPluginAuth.selectAll()
            .where { (UserPluginAuth.userId eq userId) and (UserPluginAuth.pluginId eq pluginId) }
            .count() > 0
        
        if (alreadyClaimed) {
            return@dbQuery Result.failure(Exception("你已经领取过该插件的授权码"))
        }
        
        // 获取用户当前拥有的所有授权码
        val userLicenses = UserPluginAuth
            .innerJoin(LicenseCodes, { UserPluginAuth.licenseId }, { LicenseCodes.id })
            .select(UserPluginAuth.licenseId, LicenseCodes.pluginId)
            .where { UserPluginAuth.userId eq userId }
            .distinct()
            .toList()
        
        // 获取所有插件信息（用于判断价格）
        val allPlugins = Plugins.selectAll().toList().associateBy { it[Plugins.id].value }
        
        // 如果设置了免费插件不计入，过滤掉价格为0的插件
        var validLicenses = userLicenses
        if (freePluginNotCount) {
            validLicenses = userLicenses.filter { license ->
                val ownedPluginId = license[LicenseCodes.pluginId].value
                val pluginPrice = allPlugins[ownedPluginId]?.get(Plugins.price) ?: java.math.BigDecimal.ZERO
                pluginPrice > java.math.BigDecimal.ZERO
            }
        }
        
        val totalCount = validLicenses.size
        val ownedPluginIds = validLicenses.map { it[LicenseCodes.pluginId].value }.toSet()
        
        // 检查用户是否满足领取条件
        val meetsRequirement = if (requiredPluginId != null) {
            ownedPluginIds.contains(requiredPluginId)
        } else {
            totalCount >= requiredAuthCount
        }
        
        if (!meetsRequirement) {
            return@dbQuery Result.failure(Exception("不满足领取条件"))
        }
        
        // 生成新的授权码
        val code = generateClaimCode()
        val now = LocalDateTime.now()
        
        // 创建授权码
        val licenseId = LicenseCodes.insertAndGetId {
            it[LicenseCodes.code] = code
            it[LicenseCodes.pluginId] = pluginId
            it[LicenseCodes.userId] = userId
            it[LicenseCodes.maxBindings] = 1
            it[LicenseCodes.enabled] = true
            it[LicenseCodes.createdAt] = now
        }
        
        // 创建用户-插件授权关系
        UserPluginAuth.insert {
            it[UserPluginAuth.userId] = userId
            it[UserPluginAuth.pluginId] = pluginId
            it[UserPluginAuth.licenseId] = licenseId.value
            it[UserPluginAuth.grantedAt] = now
            it[UserPluginAuth.grantedBy] = userId  // 用户自己领取，授权人是自己
        }
        
        Result.success(ClaimResult(
            message = "成功领取 ${plugin[Plugins.displayName]} 的授权码: $code",
            licenseCode = code,
            pluginName = plugin[Plugins.name],
            pluginDisplayName = plugin[Plugins.displayName]
        ))
    }
    
    /**
     * 领取结果数据类
     */
    data class ClaimResult(
        val message: String,
        val licenseCode: String,
        val pluginName: String,
        val pluginDisplayName: String
    )
    
    /**
     * 生成随机授权码
     */
    private fun generateClaimCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..20)
            .map { chars.random() }
            .joinToString("")
    }
}
