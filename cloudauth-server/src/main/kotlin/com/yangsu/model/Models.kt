package com.yangsu.model

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

// ==================== 数据库表定义 ====================

/**
 * 用户表
 */
object Users : IntIdTable("users") {
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 255)  // BCrypt hashed
    val email = varchar("email", 100).nullable()
    val isAdmin = bool("is_admin").default(false)
    val isSuperAdmin = bool("is_super_admin").default(false)  // 超级管理员
    val banned = bool("banned").default(false)  // 是否被封禁
    val lastUnbindAllAt = datetime("last_unbind_all_at").nullable()  // 上次解绑全部的时间
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

/**
 * 插件表
 */
object Plugins : IntIdTable("plugins") {
    val name = varchar("name", 100).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val description = text("description").nullable()
    val version = varchar("version", 20)
    val jarPath = varchar("jar_path", 500)  // 插件JAR文件路径
    val mainClass = varchar("main_class", 200)  // 插件主类
    val enabled = bool("enabled").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

/**
 * 授权码表
 */
object LicenseCodes : IntIdTable("license_codes") {
    val code = varchar("code", 30).uniqueIndex()  // 30位授权码
    val userId = reference("user_id", Users).nullable()  // 绑定的用户
    val pluginId = reference("plugin_id", Plugins)  // 对应的插件
    val expiresAt = datetime("expires_at").nullable()  // 过期时间,null为永久
    val maxBindings = integer("max_bindings").default(1)  // 最大绑定数
    val enabled = bool("enabled").default(true)
    val createdAt = datetime("created_at")
    val usedAt = datetime("used_at").nullable()  // 首次使用时间
}

/**
 * 设备绑定表
 * 绑定 IP/MAC/机器码, 三选二匹配即可通过验证
 */
object DeviceBindings : IntIdTable("device_bindings") {
    val licenseId = reference("license_id", LicenseCodes)
    val ip = varchar("ip", 50).nullable()
    val mac = varchar("mac", 50).nullable()
    val machineCode = varchar("machine_code", 100).nullable()  // 机器码
    val lastVerified = datetime("last_verified")
    val createdAt = datetime("created_at")
}

/**
 * 用户授权关联表 (用户-插件授权关系)
 * 同一用户可以拥有同一插件的多个授权码
 */
object UserPluginAuth : IntIdTable("user_plugin_auth") {
    val userId = reference("user_id", Users)
    val pluginId = reference("plugin_id", Plugins)
    val licenseId = reference("license_id", LicenseCodes)
    val grantedAt = datetime("granted_at")
    val grantedBy = reference("granted_by", Users)  // 授权的管理员
}

/**
 * 操作日志表
 */
object AuditLogs : IntIdTable("audit_logs") {
    val userId = reference("user_id", Users).nullable()
    val action = varchar("action", 50)  // LOGIN, VERIFY, BIND, UNBIND, etc.
    val target = varchar("target", 100).nullable()  // 操作目标
    val details = text("details").nullable()  // JSON格式的详细信息
    val ip = varchar("ip", 50).nullable()
    val createdAt = datetime("created_at")
}

/**
 * 系统配置表
 */
object SystemConfig : IntIdTable("system_config") {
    val key = varchar("config_key", 50).uniqueIndex()
    val value = varchar("config_value", 255)
    val updatedAt = datetime("updated_at")
}

/**
 * 邮箱验证码表
 */
object VerificationCodes : IntIdTable("verification_codes") {
    val email = varchar("email", 100)
    val code = varchar("code", 10)  // 6位验证码
    val type = varchar("type", 20)  // REGISTER, FORGOT_PASSWORD
    val expiresAt = datetime("expires_at")  // 过期时间
    val used = bool("used").default(false)  // 是否已使用
    val createdAt = datetime("created_at")
}

// ==================== 请求模型 ====================

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    val verificationCode: String
)

@Serializable
data class SendCodeRequest(
    val email: String,
    val type: String  // REGISTER, FORGOT_PASSWORD
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
    val verificationCode: String,
    val newPassword: String
)

@Serializable
data class CreatePluginRequest(
    val displayName: String
)

@Serializable
data class UpdatePluginRequest(
    val name: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val version: String? = null,
    val mainClass: String? = null,
    val enabled: Boolean? = null
)

@Serializable
data class GenerateLicenseRequest(
    val pluginId: Int,  // 单个插件
    val username: String? = null,  // 指定用户名，直接授权给该用户
    val count: Int = 1,
    val maxBindings: Int = 1,
    val expiresInDays: Int? = null,  // null为永久
    val customCode: String? = null   // 自定义授权码（仅通用授权码可用）
)

@Serializable
data class GrantLicenseRequest(
    val username: String,
    val licenseCode: String
)

@Serializable
data class VerifyRequest(
    val username: String,
    val licenseCode: String,
    val ip: String?,
    val mac: String?,
    val machineCode: String?
)

@Serializable
data class UnbindRequest(
    val licenseCode: String
)

@Serializable
data class UserUnbindRequest(
    val bindingId: Int
)

@Serializable
data class SetConfigRequest(
    val key: String,
    val value: String
)

@Serializable
data class ChangePasswordRequest(
    val userId: Int,
    val newPassword: String
)

@Serializable
data class CreateAdminRequest(
    val username: String,
    val password: String
)

@Serializable
data class SmtpConfigRequest(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val fromName: String = "CloudAuth"
)

// ==================== 响应模型 ====================

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)

@Serializable
data class LoginResponse(
    val token: String,
    val username: String,
    val isAdmin: Boolean,
    val isSuperAdmin: Boolean = false
)

@Serializable
data class UserDTO(
    val id: Int,
    val username: String,
    val email: String? = null,
    val isAdmin: Boolean,
    val isSuperAdmin: Boolean = false,
    val banned: Boolean = false,
    val createdAt: String
)

@Serializable
data class PluginDTO(
    val id: Int,
    val name: String,
    val displayName: String,
    val description: String?,
    val version: String,
    val mainClass: String,
    val enabled: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val jarUploaded: Boolean = false,  // JAR文件是否已上传
    val jarSize: Long = 0              // JAR文件大小(字节)
)

@Serializable
data class LicenseDTO(
    val id: Int,
    val code: String,
    val pluginId: Int,
    val pluginName: String,
    val pluginDisplayName: String,  // 插件显示名
    val userId: Int?,
    val username: String?,
    val expiresAt: String?,
    val maxBindings: Int,
    val currentBindings: Int,
    val enabled: Boolean,
    val createdAt: String,
    val usedAt: String?
)

@Serializable
data class DeviceBindingDTO(
    val id: Int,
    val ip: String?,
    val mac: String?,
    val machineCode: String?,
    val lastVerified: String,
    val createdAt: String
)

@Serializable
data class VerifyResponse(
    val valid: Boolean,
    val pluginName: String? = null,
    val pluginVersion: String? = null,
    val downloadUrl: String? = null,  // 插件下载地址
    val message: String? = null
)

@Serializable
data class UserAuthInfo(
    val plugins: List<UserPluginInfo>
)

@Serializable
data class UserPluginInfo(
    val pluginId: Int,
    val pluginName: String,
    val displayName: String,
    val version: String,
    val licenseCode: String,
    val expiresAt: String?,
    val bindings: List<DeviceBindingDTO>
)

@Serializable
data class StatsDTO(
    val totalUsers: Long,
    val totalPlugins: Long,
    val totalLicenses: Long,
    val activeLicenses: Long,
    val unbindCooldownHours: Int = 24
)

@Serializable
data class VerifyUserDTO(
    val username: String,
    val isAdmin: Boolean
)

@Serializable
data class PluginSimpleDTO(
    val id: Int,
    val name: String,
    val displayName: String,
    val description: String?,
    val version: String
)

@Serializable
data class ExchangePluginRequest(
    val fromPluginId: Int,
    val toPluginId: Int
)

@Serializable
data class ClaimPluginRequest(
    val targetPluginId: Int,
    val excludePlugins: List<Int>? = null  // 排除的插件 ID 列表
)
