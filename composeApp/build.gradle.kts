import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kotlin.text.set

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.sqldelight)
}

kotlin {
    val ffJdkVendor = providers.gradleProperty("ff.jdkVendor").orNull ?: "JetBrains"
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.matching(ffJdkVendor))
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
            // https://docs.kmpstudy.com/compose-navigation-routing.html
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")

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
    useKotlinOutput()

    buildConfigField("String", "APP_VERSION", "\"${project.version}\"")
    buildConfigField("String", "REPO_URL", "\"https://github.com/ForteScarlet/file-flattener\"")
}

compose.desktop {
    application {
        mainClass = "love.forte.tools.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "love.forte.tools"
            packageVersion = project.version.toString()
        }
    }
}
