import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Year
import java.time.ZoneId

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
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.jvm)
        }
    }
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
                "Copyright © 2024-${Year.now(ZoneId.of("Asia/Shanghai")).value} ${AppConfig.Meta.VENDOR}. All rights reserved."

            linux {
                shortcut = true
                menuGroup = AppConfig.APP_MENU_GROUP
                iconFile.set(project.rootDir.resolve("icon.png"))
                debMaintainer = AppConfig.Meta.DEB_MAINTAINER
            }

            macOS {
                bundleID = AppConfig.appNameWithPackage
                iconFile.set(project.rootDir.resolve("icon.icns"))
            }

            windows {
                shortcut = true
                dirChooser = true
                menuGroup = AppConfig.APP_MENU_GROUP
                perUserInstall = true
                menu = true
                iconFile.set(project.rootDir.resolve("icon.ico"))
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

// https://conveyor.hydraulic.dev/21.0/configs/maven-gradle/#gradle
tasks.register<ConveyorExecTask>("convey") {
    dependsOn("jar", "writeConveyorConfig")
    description = "执行 Conveyor 本地打包"
}

tasks.register<ConveyorExecTask>("conveyCi") {
    dependsOn("jar", "writeConveyorConfig")
    description = "执行 Conveyor CI 打包"
    configFile.set("ci.conveyor.conf")
}

