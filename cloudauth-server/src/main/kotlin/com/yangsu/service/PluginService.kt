package com.yangsu.service

import com.yangsu.config.DatabaseFactory.dbQuery
import com.yangsu.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PluginService {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private lateinit var pluginsDir: File
    
    fun init(dataDir: File) {
        pluginsDir = File(dataDir, "plugins")
        pluginsDir.mkdirs()
    }
    
    fun getPluginsDir(): File = pluginsDir
    
    suspend fun createPlugin(request: CreatePluginRequest): Result<PluginDTO> = dbQuery {
        val now = LocalDateTime.now()
        val defaultVersion = "1.0.0"
        // 生成临时插件名，上传JAR后会自动更新为plugin.yml中的name
        val tempName = "plugin_${System.currentTimeMillis()}"
        val jarPath = "$tempName-$defaultVersion.jar"
        
        val pluginId = Plugins.insertAndGetId {
            it[name] = tempName
            it[displayName] = request.displayName
            it[description] = null
            it[version] = defaultVersion
            it[Plugins.jarPath] = jarPath
            it[mainClass] = ""
            it[enabled] = true
            it[createdAt] = now
            it[updatedAt] = now
        }
        
        Result.success(PluginDTO(
            id = pluginId.value,
            name = tempName,
            displayName = request.displayName,
            description = null,
            version = defaultVersion,
            mainClass = "",
            enabled = true,
            createdAt = now.format(dateFormatter),
            updatedAt = now.format(dateFormatter)
        ))
    }
    
    suspend fun updatePlugin(id: Int, request: UpdatePluginRequest): Result<PluginDTO> = dbQuery {
        val plugin = Plugins.selectAll()
            .where { Plugins.id eq id }
            .singleOrNull()
            
        if (plugin == null) {
            return@dbQuery Result.failure(Exception("插件不存在"))
        }
        
        // 如果要更新name，检查是否已存在
        if (request.name != null && request.name != plugin[Plugins.name]) {
            val nameExists = Plugins.select(Plugins.name)
                .where { (Plugins.name eq request.name) and (Plugins.id neq id) }
                .count() > 0
            if (nameExists) {
                return@dbQuery Result.failure(Exception("插件名 '${request.name}' 已存在"))
            }
        }
        
        val now = LocalDateTime.now()
        val currentName = plugin[Plugins.name]
        val currentVersion = plugin[Plugins.version]
        
        Plugins.update({ Plugins.id eq id }) {
            request.name?.let { newName -> it[name] = newName }
            request.displayName?.let { name -> it[displayName] = name }
            request.description?.let { desc -> it[description] = desc }
            request.version?.let { ver -> 
                it[version] = ver
                val pluginName = request.name ?: currentName
                it[jarPath] = "$pluginName-$ver.jar"
            }
            request.mainClass?.let { main -> it[mainClass] = main }
            request.enabled?.let { en -> it[enabled] = en }
            it[updatedAt] = now
        }
        
        // 如果更新了name或version，需要重命名JAR文件
        if (request.name != null || request.version != null) {
            val oldJar = getPluginJarPath(currentName, currentVersion)
            if (oldJar.exists()) {
                val newName = request.name ?: currentName
                val newVersion = request.version ?: currentVersion
                val newJar = getPluginJarPath(newName, newVersion)
                oldJar.renameTo(newJar)
            }
        }
        
        // 查询更新后的数据
        Plugins.selectAll()
            .where { Plugins.id eq id }
            .singleOrNull()?.let { row ->
                val jarFile = getPluginJarPath(row[Plugins.name], row[Plugins.version])
                Result.success(PluginDTO(
                    id = row[Plugins.id].value,
                    name = row[Plugins.name],
                    displayName = row[Plugins.displayName],
                    description = row[Plugins.description],
                    version = row[Plugins.version],
                    mainClass = row[Plugins.mainClass],
                    enabled = row[Plugins.enabled],
                    createdAt = row[Plugins.createdAt].format(dateFormatter),
                    updatedAt = row[Plugins.updatedAt].format(dateFormatter),
                    jarUploaded = jarFile.exists() && jarFile.length() > 0,
                    jarSize = if (jarFile.exists()) jarFile.length() else 0
                ))
            } ?: Result.failure(Exception("更新失败"))
    }
    
    suspend fun getAllPlugins(): List<PluginDTO> = dbQuery {
        Plugins.selectAll().map { row ->
            val jarFile = getPluginJarPath(row[Plugins.name], row[Plugins.version])
            PluginDTO(
                id = row[Plugins.id].value,
                name = row[Plugins.name],
                displayName = row[Plugins.displayName],
                description = row[Plugins.description],
                version = row[Plugins.version],
                mainClass = row[Plugins.mainClass],
                enabled = row[Plugins.enabled],
                createdAt = row[Plugins.createdAt].format(dateFormatter),
                updatedAt = row[Plugins.updatedAt].format(dateFormatter),
                jarUploaded = jarFile.exists() && jarFile.length() > 0,
                jarSize = if (jarFile.exists()) jarFile.length() else 0
            )
        }
    }
    
    suspend fun getPluginById(id: Int): PluginDTO? = dbQuery {
        Plugins.selectAll()
            .where { Plugins.id eq id }
            .singleOrNull()?.let { row ->
                val jarFile = getPluginJarPath(row[Plugins.name], row[Plugins.version])
                PluginDTO(
                    id = row[Plugins.id].value,
                    name = row[Plugins.name],
                    displayName = row[Plugins.displayName],
                    description = row[Plugins.description],
                    version = row[Plugins.version],
                    mainClass = row[Plugins.mainClass],
                    enabled = row[Plugins.enabled],
                    createdAt = row[Plugins.createdAt].format(dateFormatter),
                    updatedAt = row[Plugins.updatedAt].format(dateFormatter),
                    jarUploaded = jarFile.exists() && jarFile.length() > 0,
                    jarSize = if (jarFile.exists()) jarFile.length() else 0
                )
            }
    }
    
    suspend fun getPluginByName(name: String): PluginDTO? = dbQuery {
        Plugins.selectAll()
            .where { Plugins.name eq name }
            .singleOrNull()?.let { row ->
                PluginDTO(
                    id = row[Plugins.id].value,
                    name = row[Plugins.name],
                    displayName = row[Plugins.displayName],
                    description = row[Plugins.description],
                    version = row[Plugins.version],
                    mainClass = row[Plugins.mainClass],
                    enabled = row[Plugins.enabled],
                    createdAt = row[Plugins.createdAt].format(dateFormatter),
                    updatedAt = row[Plugins.updatedAt].format(dateFormatter)
                )
            }
    }
    
    suspend fun deletePlugin(id: Int): Boolean = dbQuery {
        // 先获取插件信息以删除JAR文件
        val plugin = Plugins.selectAll()
            .where { Plugins.id eq id }
            .singleOrNull()
        
        if (plugin != null) {
            // 删除JAR文件
            val jarFile = getPluginJarPath(plugin[Plugins.name], plugin[Plugins.version])
            if (jarFile.exists()) {
                jarFile.delete()
            }
            
            // 查找该插件的所有授权码
            val licenseIds = LicenseCodes.selectAll()
                .where { LicenseCodes.pluginId eq id }
                .map { it[LicenseCodes.id].value }
            
            // 删除设备绑定记录
            licenseIds.forEach { licenseId ->
                DeviceBindings.deleteWhere { DeviceBindings.licenseId eq licenseId }
            }
            
            // 删除用户授权记录
            UserPluginAuth.deleteWhere { UserPluginAuth.pluginId eq id }
            
            // 删除授权码记录
            LicenseCodes.deleteWhere { LicenseCodes.pluginId eq id }
            
            // 删除插件记录
            Plugins.deleteWhere { Plugins.id eq id } > 0
        } else {
            false
        }
    }
    
    fun getPluginJarPath(pluginName: String, version: String): File {
        return File(pluginsDir, "$pluginName-$version.jar")
    }
    
    /**
     * 从上传的 JAR 文件更新插件信息
     * @param pluginName 从 plugin.yml 获取的插件名
     */
    fun updatePluginFromJar(id: Int, pluginName: String, newVersion: String, mainClass: String, description: String?): Boolean {
        return org.jetbrains.exposed.sql.transactions.transaction {
            val now = LocalDateTime.now()
            val currentPlugin = Plugins.selectAll().where { Plugins.id eq id }.singleOrNull()
                ?: return@transaction false
            
            val currentName = currentPlugin[Plugins.name]
            val currentVersion = currentPlugin[Plugins.version]
            
            // 检查新插件名是否已被其他插件使用
            if (pluginName != currentName) {
                val nameExists = Plugins.select(Plugins.name)
                    .where { (Plugins.name eq pluginName) and (Plugins.id neq id) }
                    .count() > 0
                if (nameExists) {
                    throw Exception("插件名 '$pluginName' 已被其他插件使用")
                }
            }
            
            // 删除旧的 JAR 文件
            val oldJar = getPluginJarPath(currentName, currentVersion)
            if (oldJar.exists()) {
                oldJar.delete()
            }
            
            Plugins.update({ Plugins.id eq id }) {
                it[name] = pluginName
                it[version] = newVersion
                it[jarPath] = "$pluginName-$newVersion.jar"
                if (mainClass.isNotBlank()) {
                    it[Plugins.mainClass] = mainClass
                }
                description?.let { desc -> it[Plugins.description] = desc }
                it[updatedAt] = now
            }
            true
        }
    }
    
    /**
     * 获取所有可用插件（公开，无需登录）
     */
    suspend fun getAllAvailablePlugins(): List<PluginSimpleDTO> = dbQuery {
        Plugins.selectAll()
            .where { Plugins.enabled eq true }
            .orderBy(Plugins.id to SortOrder.ASC)
            .map { row ->
                PluginSimpleDTO(
                    id = row[Plugins.id].value,
                    name = row[Plugins.name],
                    displayName = row[Plugins.displayName],
                    description = row[Plugins.description],
                    version = row[Plugins.version]
                )
            }
    }
    
    /**
     * 切换插件启用状态
     */
    suspend fun togglePlugin(id: Int, enabled: Boolean): Boolean = dbQuery {
        val plugin = Plugins.selectAll()
            .where { Plugins.id eq id }
            .singleOrNull()
            
        if (plugin == null) {
            return@dbQuery false
        }
        
        val now = LocalDateTime.now()
        Plugins.update({ Plugins.id eq id }) {
            it[Plugins.enabled] = enabled
            it[updatedAt] = now
        }
        true
    }
    
    /**
     * 插件置换：将插件 A 的授权码转换为插件 B
     */
    suspend fun exchangePlugin(fromPluginId: Int, toPluginId: Int): Result<String> = dbQuery {
        // 检查两个插件是否存在
        val fromPlugin = Plugins.selectAll().where { Plugins.id eq fromPluginId }.singleOrNull()
        val toPlugin = Plugins.selectAll().where { Plugins.id eq toPluginId }.singleOrNull()
        
        if (fromPlugin == null || toPlugin == null) {
            return@dbQuery Result.failure(Exception("插件不存在"))
        }
        
        // 查找该插件的所有授权码
        val licenses = LicenseCodes.selectAll()
            .where { LicenseCodes.pluginId eq fromPluginId }
            .toList()
        
        if (licenses.isEmpty()) {
            return@dbQuery Result.failure(Exception("该插件没有授权码"))
        }
        
        // 批量更新授权码的插件 ID
        var count = 0
        licenses.forEach { license ->
            LicenseCodes.update({ LicenseCodes.id eq license[LicenseCodes.id] }) {
                it[pluginId] = toPluginId
            }
            count++
        }
        
        Result.success("已将 $count 个授权码从 ${fromPlugin[Plugins.displayName]} 置换为 ${toPlugin[Plugins.displayName]}")
    }
}
