package com.yangsu.service

import com.yangsu.config.AppConfig
import com.yangsu.config.DatabaseFactory.dbQuery
import com.yangsu.model.VerificationCodes
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.jetbrains.exposed.sql.*
import java.time.LocalDateTime
import java.util.*

object EmailService {
    
    /**
     * 发送验证码邮件
     */
    fun sendVerificationCode(email: String, type: String): Result<String> {
        // 生成6位验证码（数字+大写字母组合）
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val code = (1..6).map { chars.random() }.joinToString("")
        
        // 检查是否需要限流（同一邮箱1分钟内只能发一次）
        val canSend = dbQuery {
            val recentCode = VerificationCodes.selectAll()
                .where { 
                    (VerificationCodes.email eq email) and 
                    (VerificationCodes.type eq type) and
                    (VerificationCodes.createdAt greater LocalDateTime.now().minusMinutes(1))
                }
                .count()
            recentCode == 0L
        }
        
        if (!canSend) {
            return Result.failure(Exception("请求过于频繁，请1分钟后重试"))
        }
        
        // 获取SMTP配置
        val smtpConfig = getSmtpConfig()
        if (smtpConfig == null) {
            return Result.failure(Exception("邮件服务未配置，请联系管理员"))
        }
        
        // 发送邮件
        try {
            val subject = when (type) {
                "REGISTER" -> "CloudPlugins - 注册验证码"
                "FORGOT_PASSWORD" -> "CloudPlugins - 找回密码验证码"
                else -> "CloudPlugins - 验证码"
            }
            
            val content = """
                <div style="font-family: 'Microsoft YaHei', sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #409EFF; border-bottom: 2px solid #409EFF; padding-bottom: 10px;">CloudPlugins 授权系统</h2>
                    <p>您好，</p>
                    <p>您的验证码是：</p>
                    <div style="background: #f5f7fa; padding: 20px; text-align: center; margin: 20px 0; border-radius: 8px;">
                        <span style="font-size: 32px; font-weight: bold; color: #409EFF; letter-spacing: 5px;">${code}</span>
                    </div>
                    <p style="color: #666;">验证码有效期为 <strong>5分钟</strong>，请尽快使用。</p>
                    <p style="color: #999; font-size: 12px;">如果这不是您的操作，请忽略此邮件。</p>
                </div>
            """.trimIndent()
            
            sendEmail(smtpConfig, email, subject, content)
            
            // 保存验证码到数据库
            dbQuery {
                // 作废之前未使用的验证码
                VerificationCodes.update({ 
                    (VerificationCodes.email eq email) and 
                    (VerificationCodes.type eq type) and
                    (VerificationCodes.used eq false)
                }) {
                    it[used] = true
                }
                
                // 插入新验证码
                VerificationCodes.insert {
                    it[VerificationCodes.email] = email
                    it[VerificationCodes.code] = code
                    it[VerificationCodes.type] = type
                    it[expiresAt] = LocalDateTime.now().plusMinutes(5)
                    it[VerificationCodes.used] = false
                    it[createdAt] = LocalDateTime.now()
                }
            }
            
            return Result.success("验证码已发送")
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(Exception("邮件发送失败: ${e.message}"))
        }
    }
    
    /**
     * 验证验证码
     */
    fun verifyCode(email: String, code: String, type: String): Boolean = dbQuery {
        val validCode = VerificationCodes.selectAll()
            .where { 
                (VerificationCodes.email eq email) and 
                (VerificationCodes.code eq code) and
                (VerificationCodes.type eq type) and
                (VerificationCodes.used eq false) and
                (VerificationCodes.expiresAt greater LocalDateTime.now())
            }
            .singleOrNull()
        
        if (validCode != null) {
            // 标记为已使用
            VerificationCodes.update({ VerificationCodes.id eq validCode[VerificationCodes.id] }) {
                it[used] = true
            }
            true
        } else {
            false
        }
    }
    
    /**
     * 授权码信息 (用于邮件发送)
     */
    data class LicenseEmailInfo(
        val pluginName: String,      // 插件名
        val displayName: String,     // 显示名
        val licenseCode: String,     // 授权码
        val maxBindings: Int         // 最大绑定数量
    )
    
    /**
     * 发送领取授权码邮件
     * @param email 用户邮箱
     * @param username 用户名
     * @param license 授权码信息
     */
    fun sendClaimLicenseEmail(email: String, username: String, license: LicenseEmailInfo): Result<String> {
        // 获取SMTP配置
        val smtpConfig = getSmtpConfig()
        if (smtpConfig == null) {
            return Result.failure(Exception("邮件服务未配置"))
        }
        
        try {
            val subject = "CloudPlugins - 您已成功领取授权码"
            
            val bindingsText = if (license.maxBindings == 0) "不限" else "${license.maxBindings}"
            
            val content = """
                <div style="font-family: 'Microsoft YaHei', sans-serif; max-width: 700px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #67c23a; border-bottom: 2px solid #67c23a; padding-bottom: 10px;">CloudPlugins 授权系统</h2>
                    <p>尊敬的 <strong>$username</strong>，</p>
                    <p>恭喜您成功领取插件授权码！</p>
                    
                    <table style="width: 100%; border-collapse: collapse; margin: 20px 0; background: #f9f9f9; border-radius: 8px; overflow: hidden;">
                        <thead>
                            <tr style="background: #67c23a; color: white;">
                                <th style="padding: 12px 15px; text-align: left;">插件</th>
                                <th style="padding: 12px 15px; text-align: left;">授权码</th>
                                <th style="padding: 12px 15px; text-align: center;">最大绑定数</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td style="padding: 12px 15px; border-bottom: 1px solid #eee;">
                                    <strong>${license.pluginName}</strong> - ${license.displayName}
                                </td>
                                <td style="padding: 12px 15px; border-bottom: 1px solid #eee; font-family: monospace; font-size: 14px; color: #67c23a;">
                                    ${license.licenseCode}
                                </td>
                                <td style="padding: 12px 15px; border-bottom: 1px solid #eee; text-align: center;">
                                    $bindingsText
                                </td>
                            </tr>
                        </tbody>
                    </table>
                    
                    <div style="background: #fff3cd; border: 1px solid #ffc107; padding: 15px; border-radius: 8px; margin: 20px 0;">
                        <strong>温馨提示：</strong>
                        <ul style="margin: 10px 0; padding-left: 20px;">
                            <li>请妥善保管您的授权码，切勿泄露给他人</li>
                            <li>授权码可在 CloudCore 插件配置文件中使用</li>
                            <li>如有任何问题，请联系管理员</li>
                        </ul>
                    </div>
                    
                    <p style="color: #999; font-size: 12px;">此邮件由系统自动发送，请勿回复。</p>
                </div>
            """.trimIndent()
            
            sendEmail(smtpConfig, email, subject, content)
            return Result.success("领取授权码邮件已发送")
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(Exception("邮件发送失败: ${e.message}"))
        }
    }
    
    /**
     * 发送授权码邮件
     * @param email 用户邮箱
     * @param username 用户名
     * @param licenses 授权码信息列表
     */
    fun sendLicenseEmail(email: String, username: String, licenses: List<LicenseEmailInfo>): Result<String> {
        // 获取SMTP配置
        val smtpConfig = getSmtpConfig()
        if (smtpConfig == null) {
            return Result.failure(Exception("邮件服务未配置"))
        }
        
        try {
            val subject = "CloudPlugins - 您的授权码已开通"
            
            // 构建授权码列表HTML
            val licenseListHtml = licenses.joinToString("") { license ->
                val bindingsText = if (license.maxBindings == 0) "不限" else "${license.maxBindings}"
                """
                <tr>
                    <td style="padding: 12px 15px; border-bottom: 1px solid #eee;">
                        <strong>${license.pluginName}</strong> - ${license.displayName}
                    </td>
                    <td style="padding: 12px 15px; border-bottom: 1px solid #eee; font-family: monospace; font-size: 14px; color: #409EFF;">
                        ${license.licenseCode}
                    </td>
                    <td style="padding: 12px 15px; border-bottom: 1px solid #eee; text-align: center;">
                        $bindingsText
                    </td>
                </tr>
                """.trimIndent()
            }
            
            val content = """
                <div style="font-family: 'Microsoft YaHei', sans-serif; max-width: 700px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #409EFF; border-bottom: 2px solid #409EFF; padding-bottom: 10px;">CloudPlugins 授权系统</h2>
                    <p>尊敬的 <strong>$username</strong>，</p>
                    <p>感谢您对 CloudPlugins 的支持！以下是您的授权码：</p>
                    
                    <table style="width: 100%; border-collapse: collapse; margin: 20px 0; background: #f9f9f9; border-radius: 8px; overflow: hidden;">
                        <thead>
                            <tr style="background: #409EFF; color: white;">
                                <th style="padding: 12px 15px; text-align: left;">插件</th>
                                <th style="padding: 12px 15px; text-align: left;">授权码</th>
                                <th style="padding: 12px 15px; text-align: center;">最大绑定数</th>
                            </tr>
                        </thead>
                        <tbody>
                            $licenseListHtml
                        </tbody>
                    </table>
                    
                    <div style="background: #fff3cd; border: 1px solid #ffc107; padding: 15px; border-radius: 8px; margin: 20px 0;">
                        <strong>温馨提示：</strong>
                        <ul style="margin: 10px 0; padding-left: 20px;">
                            <li>请妙善保管您的授权码，切勿泄露给他人</li>
                            <li>授权码可在 CloudCore 插件配置文件中使用</li>
                            <li>如有任何问题，请联系管理员</li>
                        </ul>
                    </div>
                    
                    <p style="color: #999; font-size: 12px;">此邮件由系统自动发送，请勿回复。</p>
                </div>
            """.trimIndent()
            
            sendEmail(smtpConfig, email, subject, content)
            return Result.success("授权码邮件已发送")
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(Exception("邮件发送失败: ${e.message}"))
        }
    }
    
    private data class SmtpConfig(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val fromName: String
    )
    
    /**
     * 从配置文件读取SMTP配置
     */
    private fun getSmtpConfig(): SmtpConfig? {
        return if (AppConfig.isSmtpConfigured()) {
            SmtpConfig(
                host = AppConfig.smtpHost!!,
                port = AppConfig.smtpPort,
                username = AppConfig.smtpUsername!!,
                password = AppConfig.smtpPassword!!,
                fromName = AppConfig.smtpFromName
            )
        } else {
            null
        }
    }
    
    private fun sendEmail(config: SmtpConfig, to: String, subject: String, htmlContent: String) {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            put("mail.smtp.ssl.enable", config.port == 465)
            put("mail.smtp.ssl.trust", config.host)
        }
        
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(config.username, config.password)
            }
        })
        
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.username, config.fromName, "UTF-8"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, "UTF-8")
            setContent(htmlContent, "text/html; charset=UTF-8")
        }
        
        Transport.send(message)
    }
}
