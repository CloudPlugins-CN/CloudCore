# CloudPlugins 授权管理系统

一个完整的 Minecraft 插件授权管理解决方案，包含授权服务器和客户端 SDK。

## 项目结构

```
CloudCore/
├── cloudauth-server/    # 授权管理服务器 (Kotlin + Ktor)
└── cloudcore-plugin/    # 核心授权插件 SDK (Java)
```

## 模块说明

### 1. cloudauth-server (授权服务器)

**技术栈：**
- 语言：Kotlin
- Web框架：Ktor 3.0
- 数据库：SQLite + Exposed ORM
- 前端：Vue 3 (单页面应用)
- 邮件：Jakarta Mail

**功能：**
- 用户注册/登录（邮箱验证码）
- 找回密码
- 插件管理（上传JAR、版本管理）
- 授权码生成与管理
- 设备绑定与解绑
- 管理员控制台

### 2. cloudcore-plugin (核心插件)

**技术栈：**
- 语言：Java
- API：Bukkit/Spigot

**功能：**
- 提供授权验证 SDK
- 供其他插件调用进行授权检查

## 快速开始

### 构建项目

```bash
# 构建所有模块
./gradlew build

# 仅构建授权服务器
./gradlew :cloudauth-server:build -x test
```

### 运行授权服务器

```bash
java -jar cloudauth-server/build/libs/cloudauth-server-all.jar
```

服务器默认运行在 `http://localhost:8080`

### 默认管理员账户

- 用户名：`SunShine`
- 密码：`SunShine123`
- 角色：超级管理员

## 配置文件

`application.properties` 配置示例：

```properties
# 服务器端口
server.port=8080

# 数据目录
data.dir=./data

# SMTP邮件配置
smtp.host=smtp.163.com
smtp.port=25
smtp.username=your_email@163.com
smtp.password=your_smtp_auth_code
smtp.from-name=CloudPlugins

# JWT配置
jwt.secret=your-secret-key
jwt.expiration-hours=168
```

## 环境要求

- JDK 17+
- Gradle 8.0+
- Minecraft 服务端 1.8+

## 许可证

私有项目，保留所有权利。
