import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.conveyor)
}

val appVersion = resolveAppVersion()

group = AppConfig.APP_PACKAGE
version = appVersion

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.animation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutinesCore)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            // implementation(libs.compose.desktop)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.jvm)
        }
    }
}

dependencies {
    linuxAmd64("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:${libs.versions.compose.get()}")
    linuxAarch64("org.jetbrains.compose.desktop:desktop-jvm-linux-arm64:${libs.versions.compose.get()}")
    windowsAmd64("org.jetbrains.compose.desktop:desktop-jvm-windows-x64:${libs.versions.compose.get()}")
    windowsAarch64("org.jetbrains.compose.desktop:desktop-jvm-windows-arm64:${libs.versions.compose.get()}")
    macAmd64("org.jetbrains.compose.desktop:desktop-jvm-macos-x64:${libs.versions.compose.get()}")
    macAarch64("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:${libs.versions.compose.get()}")
}

buildConfig {
    packageName("love.forte.tools.ff")
    className("FfBuildConfig")
    useKotlinOutput {
        internalVisibility = true
    }
    documentation.set("编译时生成的构建配置, 编译时自动生成，请勿手动修改")
    buildConfigField("VERSION", appVersion)
    buildConfigField("APP_NAME", AppConfig.APP_NAME)
    buildConfigField("GITHUB_URL", AppConfig.Meta.GITHUB_URL)
    buildConfigField("DOWNLOAD_URL", AppConfig.Meta.DOWNLOAD_URL)
}

compose.desktop {
    application {
        mainClass = "love.forte.tools.MainKt"
        jvmArgs += listOf(
            "-XX:ErrorFile=.logs/hs_err.log",
            "-XX:-HeapDumpOnOutOfMemoryError",
            "-XX:HeapDumpPath=.logs/dump.hprof",
        )

        nativeDistributions {
            modules("java.sql", "java.naming")

            targetFormats(
                TargetFormat.Dmg, TargetFormat.Deb,
                TargetFormat.Rpm, TargetFormat.Pkg,
                TargetFormat.Msi, TargetFormat.Exe
            )

            packageName = AppConfig.APP_NAME
            packageVersion = appVersion
            vendor = AppConfig.Meta.VENDOR
            description = AppConfig.Meta.DESCRIPTION

            copyright =
                "Copyright © 2026 ${AppConfig.Meta.VENDOR}. All rights reserved."

            linux {
                shortcut = true
                menuGroup = AppConfig.APP_MENU_GROUP
                // TODO iconFile.set(project.rootDir.resolve("icon.png"))
                debMaintainer = AppConfig.Meta.DEB_MAINTAINER
            }

            macOS {
                bundleID = AppConfig.appNameWithPackage
                // TODO iconFile.set(project.rootDir.resolve("icon.icns"))
            }

            windows {
                shortcut = true
                dirChooser = true
                menuGroup = AppConfig.APP_MENU_GROUP
                perUserInstall = true
                menu = true
                // TODO iconFile.set(project.rootDir.resolve("icon.ico"))
                upgradeUuid = AppConfig.Meta.WINDOWS_UPGRADE_UUID
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
            obfuscate.set(false)
            optimize.set(false)
        }
    }
}

sqldelight {
    databases {
        register("FfDatabase") {
            packageName.set("love.forte.tools.ff.db")
            srcDirs("src/jvmMain/sqldelight")
            // SQLDelight 的 schema 版本由 .sqm 迁移文件自动推导
            // 迁移文件命名: <从该版本升级>.sqm (例如 1.sqm = 从 v1 升级到 v2)
            // 当前只有 1.sqm，所以 schema 版本自动为 2 (初始 1 + 迁移文件数量)
            deriveSchemaFromMigrations.set(true)
        }
    }
}

// https://conveyor.hydraulic.dev/21.0/configs/maven-gradle/#gradle
tasks.register<ConveyorExecTask>("convey") {
    dependsOn("jvmJar", "writeConveyorConfig")
    description = "执行 Conveyor 本地打包"
    conveyorExecutable.set(project.resolveConveyorExecutable())
    configFile.set(rootDir.resolve("conveyor.conf"))
}

tasks.register<ConveyorExecTask>("conveyCi") {
    dependsOn("jvmJar", "writeConveyorConfig")
    description = "执行 Conveyor CI 打包"
    conveyorExecutable.set(project.resolveConveyorExecutable())
    configFile.set(rootDir.resolve("ci.conveyor.conf"))
}

