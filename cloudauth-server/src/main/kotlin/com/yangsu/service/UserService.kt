package com.yangsu.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.yangsu.config.DatabaseFactory.dbQuery
import com.yangsu.model.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object UserService {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    suspend fun register(request: RegisterRequest): Result<UserDTO> = dbQuery {
        // 检查用户名是否已存在
        val exists = Users.select(Users.username)
            .where { Users.username eq request.username }
            .count() > 0
            
        if (exists) {
            return@dbQuery Result.failure(Exception("用户名已存在"))
        }
        
        // 检查邮箱是否已被使用
        val emailExists = Users.select(Users.email)
            .where { Users.email eq request.email }
            .count() > 0
            
        if (emailExists) {
            return@dbQuery Result.failure(Exception("邮箱已被使用"))
        }
        
        val hashedPassword = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())
        val now = LocalDateTime.now()
        
        val userId = Users.insertAndGetId {
            it[username] = request.username
            it[password] = hashedPassword
            it[email] = request.email
            it[isAdmin] = false
            it[createdAt] = now
            it[updatedAt] = now
        }
        
        Result.success(UserDTO(
            id = userId.value,
            username = request.username,
            email = request.email,
            isAdmin = false,
            isSuperAdmin = false,
            banned = false,
            createdAt = now.format(dateFormatter)
        ))
    }
    
    suspend fun login(request: LoginRequest): Result<Pair<UserDTO, Boolean>> = dbQuery {
        val user = Users.selectAll()
            .where { Users.username eq request.username }
            .singleOrNull()
            
        if (user == null) {
            return@dbQuery Result.failure(Exception("用户不存在"))
        }
        
        // 检查是否被封禁
        if (user[Users.banned]) {
            return@dbQuery Result.failure(Exception("账号已被封禁"))
        }
        
        val passwordMatch = BCrypt.verifyer().verify(
            request.password.toCharArray(),
            user[Users.password]
        ).verified
        
        if (!passwordMatch) {
            return@dbQuery Result.failure(Exception("密码错误"))
        }
        
        Result.success(Pair(
            UserDTO(
                id = user[Users.id].value,
                username = user[Users.username],
                email = user[Users.email],
                isAdmin = user[Users.isAdmin],
                isSuperAdmin = user[Users.isSuperAdmin],
                banned = user[Users.banned],
                createdAt = user[Users.createdAt].format(dateFormatter)
            ),
            user[Users.isAdmin]
        ))
    }
    
    suspend fun getAllUsers(): List<UserDTO> = dbQuery {
        Users.selectAll().map { row ->
            UserDTO(
                id = row[Users.id].value,
                username = row[Users.username],
                email = row[Users.email],
                isAdmin = row[Users.isAdmin],
                isSuperAdmin = row[Users.isSuperAdmin],
                banned = row[Users.banned],
                createdAt = row[Users.createdAt].format(dateFormatter)
            )
        }
    }
    
    suspend fun getUserByUsername(username: String): UserDTO? = dbQuery {
        Users.selectAll()
            .where { Users.username eq username }
            .singleOrNull()?.let { row ->
                UserDTO(
                    id = row[Users.id].value,
                    username = row[Users.username],
                    email = row[Users.email],
                    isAdmin = row[Users.isAdmin],
                    isSuperAdmin = row[Users.isSuperAdmin],
                    banned = row[Users.banned],
                    createdAt = row[Users.createdAt].format(dateFormatter)
                )
            }
    }
    
    suspend fun getUserById(id: Int): UserDTO? = dbQuery {
        Users.selectAll()
            .where { Users.id eq id }
            .singleOrNull()?.let { row ->
                UserDTO(
                    id = row[Users.id].value,
                    username = row[Users.username],
                    email = row[Users.email],
                    isAdmin = row[Users.isAdmin],
                    isSuperAdmin = row[Users.isSuperAdmin],
                    banned = row[Users.banned],
                    createdAt = row[Users.createdAt].format(dateFormatter)
                )
            }
    }
    
    /**
     * 通过邮箱找回密码
     */
    suspend fun resetPasswordByEmail(email: String, newPassword: String): Result<Boolean> = dbQuery {
        val user = Users.selectAll()
            .where { Users.email eq email }
            .singleOrNull()
            
        if (user == null) {
            return@dbQuery Result.failure(Exception("邮箱未注册"))
        }
        
        val hashedPassword = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
        val now = LocalDateTime.now()
        
        Users.update({ Users.email eq email }) {
            it[password] = hashedPassword
            it[updatedAt] = now
        }
        
        Result.success(true)
    }
    
    /**
     * 检查邮箱是否已注册
     */
    suspend fun emailExists(email: String): Boolean = dbQuery {
        Users.selectAll()
            .where { Users.email eq email }
            .count() > 0
    }
    
    /**
     * 管理员修改用户密码
     */
    suspend fun changePassword(userId: Int, newPassword: String): Boolean = dbQuery {
        val hashedPassword = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
        val now = LocalDateTime.now()
        
        Users.update({ Users.id eq userId }) {
            it[password] = hashedPassword
            it[updatedAt] = now
        } > 0
    }
    
    /**
     * 检查用户是否存在（用于验证token有效性）
     */
    suspend fun userExists(userId: Int): Boolean = dbQuery {
        Users.selectAll()
            .where { Users.id eq userId }
            .count() > 0
    }
    
    /**
     * 超级管理员创建管理员账户
     */
    suspend fun createAdmin(username: String, password: String): Result<UserDTO> = dbQuery {
        val exists = Users.select(Users.username)
            .where { Users.username eq username }
            .count() > 0
            
        if (exists) {
            return@dbQuery Result.failure(Exception("用户名已存在"))
        }
        
        val hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        val now = LocalDateTime.now()
        
        val userId = Users.insertAndGetId {
            it[Users.username] = username
            it[Users.password] = hashedPassword
            it[isAdmin] = true
            it[isSuperAdmin] = false
            it[createdAt] = now
            it[updatedAt] = now
        }
        
        Result.success(UserDTO(
            id = userId.value,
            username = username,
            isAdmin = true,
            isSuperAdmin = false,
            banned = false,
            createdAt = now.format(dateFormatter)
        ))
    }
    
    /**
     * 检查用户是否为超级管理员
     */
    suspend fun isSuperAdmin(userId: Int): Boolean = dbQuery {
        Users.selectAll()
            .where { Users.id eq userId }
            .singleOrNull()?.get(Users.isSuperAdmin) ?: false
    }
    
    /**
     * 封禁/解封用户
     */
    suspend fun toggleBan(userId: Int, banned: Boolean): Boolean = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[Users.banned] = banned
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }
    
    /**
     * 设置/取消用户管理员权限
     */
    suspend fun setAdmin(userId: Int, isAdmin: Boolean): Boolean = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[Users.isAdmin] = isAdmin
            it[updatedAt] = LocalDateTime.now()
        } > 0
    }
    
    /**
     * 删除用户（级联删除授权码、设备绑定等）
     */
    suspend fun deleteUser(userId: Int): Boolean = dbQuery {
        // 检查用户是否存在
        val user = Users.selectAll()
            .where { Users.id eq userId }
            .singleOrNull()
        
        if (user == null) {
            return@dbQuery false
        }
        
        // 不能删除超级管理员
        if (user[Users.isSuperAdmin]) {
            return@dbQuery false
        }
        
        // 获取该用户的所有授权码 ID
        val licenseIds = LicenseCodes.selectAll()
            .where { LicenseCodes.userId eq userId }
            .map { it[LicenseCodes.id].value }
        
        // 删除所有相关的设备绑定
        licenseIds.forEach { licenseId ->
            DeviceBindings.deleteWhere { DeviceBindings.licenseId eq licenseId }
        }
        
        // 删除用户 - 插件授权关系
        UserPluginAuth.deleteWhere { UserPluginAuth.userId eq userId }
        
        // 删除所有授权码
        LicenseCodes.deleteWhere { LicenseCodes.userId eq userId }
        
        // 删除用户
        Users.deleteWhere { Users.id eq userId }
        true
    }
}
