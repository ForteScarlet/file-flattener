package love.forte.tools.ff.storage

import love.forte.tools.ff.FfConstants
import love.forte.tools.ff.FfDefaults
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

enum class FfAppTheme {
    /**
     * 樱桃红（红系主题）
     */
    CherryRed,

    /**
     * 典雅黑（黑灰系主题）
     */
    ClassicBlack,
}

data class FfAppSettings(
    val theme: FfAppTheme = FfAppTheme.CherryRed,
    val concurrencyLimit: Int = FfDefaults.defaultConcurrencyLimit(),
)

class FfSettingsStore(
    private val appDir: Path,
) {
    private val file: Path = FfAppPaths.settingsFile(appDir)

    fun load(): FfAppSettings {
        val props = Properties()
        if (Files.exists(file)) {
            Files.newInputStream(file).use(props::load)
        }
        val theme = props.getProperty(Keys.Theme)
            ?.let(::parseTheme)
            ?: props.getProperty(Keys.ThemeModeLegacy)
                ?.let(::parseLegacyThemeModeToTheme)
            ?: FfAppTheme.CherryRed

        val concurrencyLimit = props.getProperty(Keys.ConcurrencyLimit)
            ?: props.getProperty(Keys.LinkConcurrencyLegacy)
        val parsedConcurrencyLimit = concurrencyLimit
            ?.toIntOrNull()
            ?.coerceIn(1, FfConstants.MaxLinkConcurrency)
            ?: FfDefaults.defaultConcurrencyLimit()

        return FfAppSettings(
            theme = theme,
            concurrencyLimit = parsedConcurrencyLimit,
        )
    }

    fun save(settings: FfAppSettings) {
        Files.createDirectories(appDir)
        val props = Properties()
        props[Keys.Theme] = settings.theme.name
        props[Keys.ConcurrencyLimit] = settings.concurrencyLimit.toString()
        Files.newOutputStream(file).use { out ->
            props.store(out, "file-flattener settings")
        }
    }

    private fun parseTheme(raw: String): FfAppTheme =
        runCatching { enumValueOf<FfAppTheme>(raw) }.getOrDefault(FfAppTheme.CherryRed)

    /**
     * 兼容旧版本的 themeMode（System/Light/Dark）。
     * - Dark -> ClassicBlack
     * - 其它 -> CherryRed
     */
    private fun parseLegacyThemeModeToTheme(raw: String): FfAppTheme {
        return when (raw) {
            "Dark" -> FfAppTheme.ClassicBlack
            "System", "Light" -> FfAppTheme.CherryRed
            else -> FfAppTheme.CherryRed
        }
    }

    private object Keys {
        const val Theme: String = "theme"
        const val ThemeModeLegacy: String = "themeMode"
        const val ConcurrencyLimit: String = "concurrencyLimit"
        const val LinkConcurrencyLegacy: String = "linkConcurrency"
    }
}
