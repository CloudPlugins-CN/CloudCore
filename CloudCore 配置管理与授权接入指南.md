# CloudCore 配置管理与授权接入指南

## 📖 概述

CloudCore 提供了统一的配置文件管理和授权验证功能，所有通过 CloudCore 加载的云端插件的配置文件都会存放在：

```
plugins/CloudCore/Config/{插件名}/
```

### 核心功能

- **配置管理**：统一的配置文件管理 API
- **授权验证**：基于授权码的插件验证机制
- **设备绑定**：IP/MAC/机器码三选二绑定策略
- **插件加载**：自动下载和加载已授权的插件

---

## 🚀 快速开始

### 1. 基础使用示例

```kotlin
import com.cloudcore.api.CloudConfigManager
import com.cloudcore.api.CloudCoreAPI
import org.bukkit.plugin.java.JavaPlugin

class MyPlugin : JavaPlugin() {
    
    private lateinit var configManager: CloudConfigManager
    
    override fun onEnable() {
        // 步骤 1: 检查自身授权
        if (!CloudCoreAPI.isAuthorized(name)) {
            CloudCoreAPI.severe(name, "未找到有效授权，请联系管理员获取授权码")
            isEnabled = false
            return
        }
        
        // 步骤 2: 初始化配置管理器
        configManager = CloudConfigManager.of(this)
        
        // 步骤 3: 保存默认配置
        configManager.saveDefaultConfig()
        
        // 步骤 4: 读取配置
        val config = configManager.getConfig()
        val debug = config.getBoolean("debug", false)
        
        CloudCoreAPI.info(name, "插件已启用！调试模式：$debug")
    }
    
    override fun onDisable() {
        // 保存所有配置
        configManager.saveAllConfigs()
    }
}
```

---

## 📁 配置管理 API

### CloudConfigManager 使用

#### 初始化

```kotlin
// 获取配置管理器实例（单例模式，相同插件返回相同实例）
val configManager = CloudConfigManager.of(plugin)

// 获取插件配置目录
val folder: File = configManager.configFolder
// 路径：plugins/CloudCore/Config/{插件名}/
```

#### 主配置文件操作

```kotlin
// 获取主配置 (config.yml)
val config = configManager.getConfig()

// 保存主配置
configManager.saveConfig()

// 重新加载主配置
val newConfig = configManager.reloadConfig()

// 从 JAR 保存默认主配置（不覆盖已有）
val created = configManager.saveDefaultConfig()
```

#### 通用配置文件操作

```kotlin
// 加载任意配置文件
val messages = configManager.loadConfig("messages.yml")
val playerData = configManager.loadConfig("data/players.yml")

// 保存配置到文件
configManager.saveConfig("messages.yml", messages)

// 使用缓存（推荐，避免重复加载）
val cachedConfig = configManager.getCachedConfig("messages.yml")

// 保存所有缓存的配置
configManager.saveAllConfigs()

// 重新加载指定配置
configManager.reloadConfig("messages.yml")

// 重新加载所有配置
configManager.reloadAllConfigs()
```

#### 资源文件操作

```kotlin
// 从 JAR 保存资源文件（不覆盖）
configManager.saveResource("config.yml")
configManager.saveResource("lang/zh_CN.yml")

// 从 JAR 保存资源到指定名称
configManager.saveResource("default-config.yml", "config.yml")

// 强制覆盖保存
configManager.saveResourceForce("config.yml")

// 从字符串内容保存
configManager.saveDefaultConfig("test.yml", """
    key: value
    list:
      - item1
      - item2
""".trimIndent())
```

#### 文件管理

```kotlin
// 检查文件是否存在
if (configManager.exists("config.yml")) {
    // 文件存在
}

// 删除配置文件
configManager.delete("old-config.yml")

// 创建子目录
val dataFolder = configManager.createFolder("data")
val logsFolder = configManager.createFolder("logs")

// 列出所有 yml 文件
val ymlFiles = configManager.listFiles("yml")

// 列出指定目录下的文件
val dataFiles = configManager.listFiles("data", "yml")

// 递归列出所有文件
val allFiles = configManager.listAllFiles("yml")
```

#### 便捷读写方法

```kotlin
// 读取配置值
val name = configManager.getString("config.yml", "player.name", "默认值")
val level = configManager.getInt("config.yml", "player.level", 1)
val enabled = configManager.getBoolean("config.yml", "feature.enabled", false)
val rate = configManager.getDouble("config.yml", "settings.rate", 1.0)
val list = configManager.getStringList("config.yml", "blacklist")

// 设置配置值
configManager.set("config.yml", "player.name", "Steve")
configManager.set("config.yml", "player.level", 10)

// 记得保存
configManager.saveConfig("config.yml")
```

---

## 🔐 授权验证接入

### 1. 基础授权检查

```kotlin
import com.cloudcore.api.CloudCoreAPI

// 最简单的授权检查方式
if (CloudCoreAPI.isAuthorized("MyPlugin")) {
    // 插件已授权，可以正常使用
    CloudCoreAPI.info("MyPlugin", "授权验证通过!")
} else {
    // 插件未授权，限制功能或提示用户
    CloudCoreAPI.warning("MyPlugin", "未找到有效授权，请联系管理员获取授权码")
}
```

### 2. 获取详细授权信息

```kotlin
import com.cloudcore.api.CloudCoreAPI

// 获取单个插件的授权详情
val authorization = CloudCoreAPI.getAuthorization("MyPlugin")
authorization?.let {
    println("插件名称：${it.pluginName}")
    println("插件版本：${it.pluginVersion}")
    println("下载 URL: ${it.downloadUrl}")
    println("授权码：${it.licenseCode}")
}

// 获取所有已授权的插件列表
val authorizedPlugins = CloudCoreAPI.getAuthorizedPlugins()
authorizedPlugins.forEach { auth ->
    CloudCoreAPI.info("MyPlugin", "已授权插件：${auth.pluginName} v${auth.pluginVersion}")
}
```

### 3. 完整的授权验证示例

```kotlin
package com.example.myplugin

import com.cloudcore.api.CloudCoreAPI
import org.bukkit.plugin.java.JavaPlugin

class MyCloudPlugin : JavaPlugin() {
    
    override fun onEnable() {
        // 步骤 1: 检查自身授权
        if (!CloudCoreAPI.isAuthorized(name)) {
            CloudCoreAPI.severe(name, "未找到有效授权，插件已停止运行!")
            CloudCoreAPI.severe(name, "请联系管理员获取授权码并在 CloudCore/config.yml 中配置")
            isEnabled = false
            return
        }
        
        // 步骤 2: 获取授权详细信息（可选）
        val authorization = CloudCoreAPI.getAuthorization(name)
        authorization?.let {
            CloudCoreAPI.info(name, "====================================")
            CloudCoreAPI.info(name, "插件名称：${it.pluginName}")
            CloudCoreAPI.info(name, "插件版本：${it.pluginVersion}")
            CloudCoreAPI.info(name, "授权验证通过，插件正常启动!")
            CloudCoreAPI.info(name, "====================================")
        }
        
        // 步骤 3: 继续插件的正常加载流程...
        loadPluginFeatures()
    }
    
    private fun loadPluginFeatures() {
        // 插件功能加载逻辑
    }
}
```

### 5. 查看已加载的插件信息

```kotlin
// 获取所有已通过 CloudCore 加载的插件
val loadedPlugins = CloudCoreAPI.getLoadedPlugins()
loadedPlugins.forEach { plugin ->
    CloudCoreAPI.info("MyPlugin", "已加载插件：${plugin.name}")
    CloudCoreAPI.info("MyPlugin", "  - 版本：${plugin.version}")
    CloudCoreAPI.info("MyPlugin", "  - 配置目录：${plugin.configDir}")
    CloudCoreAPI.info("MyPlugin", "  - 状态：${if (plugin.enabled) "已启用" else "已禁用"}")
}

// 检查特定插件是否已加载
if (CloudCoreAPI.isLoaded("AnotherPlugin")) {
    CloudCoreAPI.info("MyPlugin", "AnotherPlugin 已加载，可以进行交互")
}
```

### 6. 依赖其他插件的示例

```kotlin
package com.example.dependentplugin

import com.cloudcore.api.CloudCoreAPI
import org.bukkit.plugin.java.JavaPlugin

/**
 * 依赖 CloudCore 授权的插件示例
 */
class DependentPlugin : JavaPlugin() {
    
    private val requiredPlugins = listOf("CorePlugin", "APIPlugin")
    
    override fun onEnable() {
        // 1. 检查自身授权
        if (!CloudCoreAPI.isAuthorized(name)) {
            CloudCoreAPI.severe(name, "未找到有效授权!")
            isEnabled = false
            return
        }
        
        // 2. 检查依赖的插件是否已授权
        val missingPlugins = requiredPlugins.filterNot { CloudCoreAPI.isAuthorized(it) }
        if (missingPlugins.isNotEmpty()) {
            CloudCoreAPI.severe(name, "缺少必要的依赖插件：${missingPlugins.joinToString(", ")}")
            CloudCoreAPI.severe(name, "请在 CloudCore/config.yml 中添加这些插件的授权码")
            isEnabled = false
            return
        }
        
        // 3. 所有检查通过，正常启动
        CloudCoreAPI.info(name, "所有依赖检查通过，插件启动成功!")
        enableFeatures()
    }
    
    private fun enableFeatures() {
        // 启用插件功能
    }
}
```

---

## ☕ Java 使用示例

```java
import com.cloudcore.api.CloudConfigManager;
import com.cloudcore.api.CloudCoreAPI;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {
    
    private CloudConfigManager configManager;
    
    @Override
    public void onEnable() {
        // 检查授权
        if (!CloudCoreAPI.isAuthorized(getName())) {
            CloudCoreAPI.severe(getName(), "未找到有效授权");
            setEnabled(false);
            return;
        }
        
        // 获取配置管理器
        configManager = CloudConfigManager.of(this);
        
        // 保存默认配置
        configManager.saveDefaultConfig();
        
        // 获取配置
        Configuration config = configManager.getConfig();
        String value = config.getString("key");
        
        CloudCoreAPI.info(getName(), "插件已启用：" + value);
    }
    
    @Override
    public void onDisable() {
        configManager.saveAllConfigs();
    }
}
```

---

## 📂 配置文件目录结构

```
plugins/
└── CloudCore/
    ├── config.yml              # CloudCore 自身配置
    └── Config/                 # 云端插件配置目录
        ├── MyPlugin/           # 插件 1 的配置
        │   ├── config.yml
        │   ├── messages.yml
        │   └── data/
        │       └── players.yml
        └── AnotherPlugin/      # 插件 2 的配置
            └── config.yml
```

---

## 🛠️ CloudCoreAPI 工具方法

```kotlin
import com.cloudcore.api.CloudCoreAPI

// 检查授权
val authorized = CloudCoreAPI.isAuthorized("MyPlugin")

// 获取目录
val dataFolder = CloudCoreAPI.getDataFolder()   // plugins/CloudCore/
val tempFolder = CloudCoreAPI.getTempFolder()   // 系统临时目录

// 日志
CloudCoreAPI.info("MyPlugin", "信息")
CloudCoreAPI.warning("MyPlugin", "警告")
CloudCoreAPI.severe("MyPlugin", "错误")
```

### 日志输出格式

```
[INFO] [MyPlugin] 这是一条信息
[WARN] [MyPlugin] 这是一条警告
[ERROR] [MyPlugin] 这是一条错误
```

---

## ⚠️ 注意事项

### 配置管理

1. **只有通过 CloudCore 加载的插件** 才应使用此 API 管理配置
2. 配置目录在首次访问时自动创建
3. 使用 `saveDefaultConfig()` 或 `saveResource()` 不会覆盖已有文件
4. 使用 `saveResourceForce()` 会强制覆盖文件
5. 建议在 `onDisable()` 中调用 `saveAllConfigs()` 保存所有修改
6. `CloudConfigManager.of(plugin)` 使用单例模式，相同插件返回相同实例

### 授权验证

7. **授权验证是异步进行的**，插件启动时应检查授权状态再决定是否启用
8. **依赖 CloudCore 的插件** 应在 `onEnable()` 早期进行授权检查
9. 如果插件未找到授权，建议设置 `isEnabled = false` 来禁用插件
10. 授权验证采用 **IP/MAC/机器码三选二** 的绑定策略
11. 解绑设备有 **24 小时冷却时间**

---

## 📝 完整示例插件

```kotlin
package com.example.myplugin

import com.cloudcore.api.CloudConfigManager
import com.cloudcore.api.CloudCoreAPI
import org.bukkit.plugin.java.JavaPlugin

class MyCloudPlugin : JavaPlugin() {
    
    private lateinit var configManager: CloudConfigManager
    
    override fun onEnable() {
        // 初始化配置管理器
        configManager = CloudConfigManager.of(this)
        
        // 保存默认配置文件
        configManager.saveDefaultConfig()                    // config.yml
        configManager.saveResource("messages.yml")           // messages.yml
        configManager.saveResource("data/defaults.yml")      // data/defaults.yml
        
        // 加载配置
        val config = configManager.getConfig()
        val messages = configManager.loadConfig("messages.yml")
        
        // 读取配置
        val debug = config.getBoolean("debug", false)
        val prefix = messages.getString("prefix") ?: "[MyPlugin]"
        
        // 日志
        CloudCoreAPI.info(name, "插件已启用!")
        CloudCoreAPI.info(name, "调试模式：$debug")
        
        // 检查授权
        if (CloudCoreAPI.isAuthorized(name)) {
            CloudCoreAPI.info(name, "授权验证通过!")
        }
    }
    
    override fun onDisable() {
        // 保存所有配置
        configManager.saveAllConfigs()
        CloudCoreAPI.info(name, "插件已禁用!")
    }
    
    fun reloadPlugin() {
        configManager.reloadAllConfigs()
        CloudCoreAPI.info(name, "配置已重新加载!")
    }
}
```

---

## 🔍 常见问题

### Q1: 插件启动时报 "CloudCore 未运行"？
**A:** 确保 CloudCore 插件已正确安装并在该插件之前加载。

### Q2: 如何重新加载配置？
**A:** 使用 `configManager.reloadAllConfigs()` 或 `CloudCoreAPI.reload()`。

### Q3: 授权码在哪里配置？
**A:** 在 `plugins/CloudCore/config.yml` 中的 `license-codes` 字段配置。

### Q4: 如何查看设备绑定信息？
**A:** 使用 `CloudCoreAPI.getAuthorization("插件名")` 获取授权信息，包含绑定详情。

---

**文档版本**: 1.0  
**最后更新**: 2026-03-25
