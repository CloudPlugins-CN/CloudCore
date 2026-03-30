import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8

plugins {
    java
    id("io.izzel.taboolib") version "2.0.36"
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
}

taboolib {
    env {
        install(Basic)
        install(Bukkit)
        install(BukkitHook)
        install(BukkitUI)
        install(BukkitUtil)
        install(MinecraftChat)
        install(CommandHelper)
        install(DatabasePlayer)
    }
    description {
        name = "CloudCore"
        desc("云系列插件核心 - 云端授权与插件管理系统")
        contributors {
            name("SunShine")
        }
        dependencies {
            name("AttributePlus").optional(true)
            name("SX-Attribute").optional(true)
            name("CraneAttribute").optional(true)
        }
    }
    version { taboolib = "6.2.4-99fb800" }
    
    // 打包依赖并重定位到 com.cloudcore.libs
    // Kotlin 使用外部依赖，不打包
    relocate("okhttp3.", "com.cloudcore.libs.okhttp3.")
    relocate("okio.", "com.cloudcore.libs.okio.")
    relocate("com.google.gson.", "com.cloudcore.libs.gson.")
    relocate("com.google.errorprone.", "com.cloudcore.libs.errorprone.")
    relocate("org.intellij.", "com.cloudcore.libs.intellij.")
    relocate("org.jetbrains.", "com.cloudcore.libs.jetbrains.")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
    
    // HTTP Client & JSON - taboolib 会打包并重定位
    taboo("com.squareup.okhttp3:okhttp:4.12.0")
    taboo("com.google.code.gson:gson:2.11.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.jar {
    archiveBaseName.set("CloudCore")
    // 从 JAR 中排除 Kotlin 标准库
    exclude("kotlin/**")
    exclude("kotlinx/**")
}

// 清理JAR中的空目录
tasks.register("cleanEmptyDirs") {
    dependsOn("taboolibMainTask")
    doLast {
        val jarFile = file("build/libs/CloudCore-${project.version}.jar")
        if (jarFile.exists()) {
            // 使用 Ant 任务来处理
            ant.withGroovyBuilder {
                "zip"("destfile" to jarFile.absolutePath + ".new", "duplicate" to "preserve") {
                    "zipfileset"("src" to jarFile.absolutePath) {
                        "exclude"("name" to "org/")
                        "exclude"("name" to "com/google/")
                    }
                }
            }
            jarFile.delete()
            file(jarFile.absolutePath + ".new").renameTo(jarFile)
        }
    }
}

tasks.named("build") {
    finalizedBy("cleanEmptyDirs")
}
