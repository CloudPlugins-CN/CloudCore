// CloudCore - 云端授权系统根项目
//   - cloudauth-server: 授权后端服务
//   - cloudcore-plugin: CloudCore MC插件

plugins {
    kotlin("jvm") version "2.2.0" apply false
    id("io.izzel.taboolib") version "2.0.36" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}