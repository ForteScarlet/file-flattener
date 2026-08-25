import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.koinCompiler)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.nucleus)
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
            // Koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            // Koin Annotations
            implementation(libs.koin.annotations)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            implementation(libs.nucleus.application)
            implementation(libs.nucleus.decorated.window.tao)
            implementation(libs.nucleus.decorated.window.material3)
            implementation(libs.nucleus.updater.runtime)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
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

nucleus.application {
    mainClass = "love.forte.tools.MainKt"
    jvmArgs += listOf(
        "-XX:ErrorFile=.logs/hs_err.log",
        "-XX:-HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=.logs/dump.hprof",
        "-XX:+UseZGC",
        "-XX:+ZGenerational"
    )

    nativeDistributions {
        modules("java.sql", "java.naming", "jdk.localedata", "jdk.security.auth")

        targetFormats(
            TargetFormat.Dmg,
            TargetFormat.Pkg,
            TargetFormat.Zip,

            TargetFormat.Nsis,
            TargetFormat.NsisWeb,
            TargetFormat.Msi,
            TargetFormat.Portable,
            TargetFormat.AppX,

            TargetFormat.Deb,
            TargetFormat.Rpm,
            TargetFormat.AppImage,
            TargetFormat.Pacman,
            TargetFormat.Snap,
            TargetFormat.Flatpak,

            TargetFormat.Tar,
            TargetFormat.SevenZ,
        )

        packageName = AppConfig.APP_NAME
        packageVersion = appVersion
        vendor = AppConfig.Meta.VENDOR
        description = AppConfig.Meta.DESCRIPTION
        homepage = AppConfig.Meta.DOWNLOAD_URL
        cleanupNativeLibs = true

        copyright =
            "Copyright © 2026 ${AppConfig.Meta.VENDOR}. All rights reserved."

        linux {
            iconFile.set(project.rootProject.file("icon.png"))
            shortcut = true
            menuGroup = AppConfig.APP_MENU_GROUP
            debMaintainer = AppConfig.Meta.DEB_MAINTAINER
        }

        macOS {
            iconFile.set(project.rootProject.file("icon.icns"))
            bundleID = AppConfig.appNameWithPackage
        }

        windows {
            iconFile.set(project.rootProject.file("icon.ico"))
            shortcut = true
            menuGroup = AppConfig.APP_MENU_GROUP
            nsis {
                oneClick = false
                perMachine = false
                allowElevation = true
                allowToChangeInstallationDirectory = true
                createDesktopShortcut = true
                createStartMenuShortcut = true
                runAfterFinish = true
                installerIcon.set(project.rootProject.file("icon.ico"))
                uninstallerIcon.set(project.rootProject.file("icon.ico"))
            }
        }

        publish {
            github {
                enabled = true
                owner = "ForteScarlet"
                repo = "file-flattener"
            }
        }
    }

    buildTypes.release.proguard {
        isEnabled.set(true)
        obfuscate.set(true)
        optimize.set(true)
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
