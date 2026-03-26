package com.yangsu.service

import com.yangsu.config.DatabaseFactory.dbQuery
import com.yangsu.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 插件置换配置服务
 */
object PluginExchangeService {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * 创建置换配置（管理员）
     */
    suspend fun createExchangeConfig(request: CreateExchangeConfigRequest): Result<ExchangeConfigDTO> = dbQuery {
        // 检查插件是否存在
        val fromPlugin = Plugins.selectAll().where { Plugins.id eq request.fromPluginId }.singleOrNull()
        val toPlugin = Plugins.selectAll().where { Plugins.id eq request.toPluginId }.singleOrNull()
        
        if (fromPlugin == null || toPlugin == null) {
            return@dbQuery Result.failure(Exception("插件不存在"))
        }
        
        // 检查是否已存在相同的置换配置
        val exists = PluginExchangeConfigs.selectAll()
            .where { 
                (PluginExchangeConfigs.fromPluginId eq request.fromPluginId) and
                (PluginExchangeConfigs.toPluginId eq request.toPluginId)
            }
            .count() > 0
        
        if (exists) {
            return@dbQuery Result.failure(Exception("该置换配置已存在"))
        }
        
        val now = LocalDateTime.now()
        val configId = PluginExchangeConfigs.insertAndGetId {
            it[fromPluginId] = request.fromPluginId
            it[toPluginId] = request.toPluginId
            it[enabled] = request.enabled
            it[createdAt] = now
        }
        
        Result.success(ExchangeConfigDTO(
            id = configId.value,
            fromPluginId = fromPlugin[Plugins.id].value,
            fromPluginName = fromPlugin[Plugins.name],
            fromPluginDisplayName = fromPlugin[Plugins.displayName],
            toPluginId = toPlugin[Plugins.id].value,
            toPluginName = toPlugin[Plugins.name],
            toPluginDisplayName = toPlugin[Plugins.displayName],
            enabled = request.enabled,
            createdAt = now.format(dateFormatter)
        ))
    }
    
    /**
     * 获取所有置换配置（管理员）
     */
    suspend fun getAllExchangeConfigs(): List<ExchangeConfigDTO> = dbQuery {
        // 获取所有置换配置
        val configs = PluginExchangeConfigs
            .selectAll()
            .orderBy(PluginExchangeConfigs.createdAt to SortOrder.DESC)
            .toList()
        
        // 获取所有插件信息用于映射
        val allPlugins = Plugins.selectAll().toList().associateBy { it[Plugins.id].value }
        
        configs.mapNotNull { config ->
            val fromPluginId = config[PluginExchangeConfigs.fromPluginId].value
            val toPluginId = config[PluginExchangeConfigs.toPluginId].value
            val fromPlugin = allPlugins[fromPluginId]
            val toPlugin = allPlugins[toPluginId]
            
            if (fromPlugin == null || toPlugin == null) {
                return@mapNotNull null
            }
            
            ExchangeConfigDTO(
                id = config[PluginExchangeConfigs.id].value,
                fromPluginId = fromPluginId,
                fromPluginName = fromPlugin[Plugins.name],
                fromPluginDisplayName = fromPlugin[Plugins.displayName],
                toPluginId = toPluginId,
                toPluginName = toPlugin[Plugins.name],
                toPluginDisplayName = toPlugin[Plugins.displayName],
                enabled = config[PluginExchangeConfigs.enabled],
                createdAt = config[PluginExchangeConfigs.createdAt].format(dateFormatter)
            )
        }
    }
    
    /**
     * 获取启用的置换配置（用户可见）
     */
    suspend fun getEnabledExchangeConfigs(): List<ExchangeConfigDTO> = dbQuery {
        // 获取所有启用的置换配置
        val configs = PluginExchangeConfigs
            .selectAll()
            .where { PluginExchangeConfigs.enabled eq true }
            .orderBy(PluginExchangeConfigs.createdAt to SortOrder.DESC)
            .toList()
        
        // 获取所有插件信息用于映射
        val allPlugins = Plugins.selectAll().toList().associateBy { it[Plugins.id].value }
        
        configs.mapNotNull { config ->
            val fromPluginId = config[PluginExchangeConfigs.fromPluginId].value
            val toPluginId = config[PluginExchangeConfigs.toPluginId].value
            val fromPlugin = allPlugins[fromPluginId]
            val toPlugin = allPlugins[toPluginId]
            
            if (fromPlugin == null || toPlugin == null) {
                return@mapNotNull null
            }
            
            ExchangeConfigDTO(
                id = config[PluginExchangeConfigs.id].value,
                fromPluginId = fromPluginId,
                fromPluginName = fromPlugin[Plugins.name],
                fromPluginDisplayName = fromPlugin[Plugins.displayName],
                toPluginId = toPluginId,
                toPluginName = toPlugin[Plugins.name],
                toPluginDisplayName = toPlugin[Plugins.displayName],
                enabled = config[PluginExchangeConfigs.enabled],
                createdAt = config[PluginExchangeConfigs.createdAt].format(dateFormatter)
            )
        }
    }
    
    /**
     * 删除置换配置（管理员）
     */
    suspend fun deleteExchangeConfig(id: Int): Boolean = dbQuery {
        PluginExchangeConfigs.deleteWhere { PluginExchangeConfigs.id eq id } > 0
    }
    
    /**
     * 禁用/启用置换配置（管理员）
     */
    suspend fun toggleExchangeConfig(id: Int, isEnabled: Boolean): Boolean = dbQuery {
        val config = PluginExchangeConfigs.selectAll()
            .where { PluginExchangeConfigs.id eq id }
            .singleOrNull()
        
        if (config == null) {
            return@dbQuery false
        }
        
        PluginExchangeConfigs.update({ PluginExchangeConfigs.id eq id }) {
            it[PluginExchangeConfigs.enabled] = isEnabled
        }
        true
    }
    
    /**
     * 用户执行置换
     */
    suspend fun userExchange(userId: Int, exchangeConfigId: Int): Result<String> = dbQuery {
        // 获取置换配置
        val config = PluginExchangeConfigs.selectAll()
            .where { PluginExchangeConfigs.id eq exchangeConfigId }
            .singleOrNull()
        
        if (config == null) {
            return@dbQuery Result.failure(Exception("置换配置不存在"))
        }
        
        if (!config[PluginExchangeConfigs.enabled]) {
            return@dbQuery Result.failure(Exception("该置换配置已禁用"))
        }
        
        // 获取源插件和目标插件信息
        val fromPlugin = Plugins.selectAll().where { Plugins.id eq config[PluginExchangeConfigs.fromPluginId] }.singleOrNull()
        val toPlugin = Plugins.selectAll().where { Plugins.id eq config[PluginExchangeConfigs.toPluginId] }.singleOrNull()
        
        if (fromPlugin == null || toPlugin == null) {
            return@dbQuery Result.failure(Exception("插件信息不完整"))
        }
        
        // 检查用户是否有源插件的授权码
        val userLicenses = UserPluginAuth
            .innerJoin(LicenseCodes, { UserPluginAuth.licenseId }, { LicenseCodes.id })
            .select(UserPluginAuth.licenseId, LicenseCodes.code)
            .where { 
                (UserPluginAuth.userId eq userId) and
                (LicenseCodes.pluginId eq config[PluginExchangeConfigs.fromPluginId])
            }
            .toList()
        
        if (userLicenses.isEmpty()) {
            return@dbQuery Result.failure(Exception("你没有可置换的插件授权码"))
        }
        
        // 批量更新用户的授权码为目标插件
        var count = 0
        userLicenses.forEach { license ->
            LicenseCodes.update({ LicenseCodes.id eq license[UserPluginAuth.licenseId] }) {
                it[pluginId] = config[PluginExchangeConfigs.toPluginId]
            }
            
            // 更新用户 - 插件关联
            UserPluginAuth.update({ 
                (UserPluginAuth.userId eq userId) and
                (UserPluginAuth.licenseId eq license[UserPluginAuth.licenseId])
            }) {
                it[pluginId] = config[PluginExchangeConfigs.toPluginId]
            }
            
            count++
        }
        
        Result.success("成功置换 $count 个授权码从 ${fromPlugin[Plugins.displayName]} 到 ${toPlugin[Plugins.displayName]}")
    }
}
