package love.forte.tools.ff.storage

import love.forte.tools.ff.FfDefaults

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
