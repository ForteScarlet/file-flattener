/**
 * 应用程序构建配置常量。
 * 集中管理所有与应用程序相关的基础配置信息。
 */
object AppConfig {
    const val APP_NAME = "FileFlattener"
    const val APP_NAME_LOWER = "file-flattener"
    const val APP_NAME_CN = "目录平铺助手"
    const val APP_PACKAGE = "love.forte.tools.file-flattener"
    const val APP_MENU_GROUP = "forteApp"
    const val DEFAULT_VERSION = "1.0.3" // next: 1.0.4

    val appNameWithPackage: String
        get() = "$APP_PACKAGE.$APP_NAME"

    // 属性/环境变量名常量
    object PropertyNames {
        const val VERSION = "appVersion"
        const val VERSION_ENV = "APP_VERSION"
        const val CONVEYOR_EXECUTABLE = "conveyorExecutable"
        const val CONVEYOR_EXECUTABLE_ENV = "CONVEYOR_EXECUTABLE"
    }

    // 元数据
    object Meta {
        const val VENDOR = "Forte Scarlet"
        const val DESCRIPTION = "目录平铺助手，一款桌面应用程序，通过文件链接将多个目录中的文件递归平铺到单一目标目录。"
        const val GITHUB_URL = "https://github.com/ForteScarlet/file-flattener"
        const val DOWNLOAD_URL = "https://fortescarlet.github.io/file-flattener/download"
        const val DEB_MAINTAINER = "ForteScarlet@163.com"
        const val WINDOWS_UPGRADE_UUID = "342114b6-d99e-4192-b2c9-1adec7a4eebc"
    }
}
