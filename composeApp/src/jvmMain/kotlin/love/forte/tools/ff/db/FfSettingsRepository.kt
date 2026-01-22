package love.forte.tools.ff.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import love.forte.tools.ff.FfConstants
import love.forte.tools.ff.FfDefaults
import love.forte.tools.ff.storage.FfAppSettings
import love.forte.tools.ff.storage.FfAppTheme

/**
 * 设置数据访问层。
 */
class FfSettingsRepository(
    private val database: FfDatabase,
) {
    /**
     * 加载应用设置。
     */
    suspend fun load(): FfAppSettings = withContext(Dispatchers.IO) {
        FfAppSettings(
            theme = getTheme(),
            concurrencyLimit = getConcurrencyLimit(),
        )
    }

    /**
     * 保存应用设置。
     */
    suspend fun save(settings: FfAppSettings) = withContext(Dispatchers.IO) {
        database.transaction {
            setTheme(settings.theme)
            setConcurrencyLimit(settings.concurrencyLimit)
        }
    }

    private fun getTheme(): FfAppTheme {
        val raw = database.ffDatabaseQueries
            .selectSettingByKey(FfSettingsKeys.THEME)
            .executeAsOneOrNull()
        return raw?.let {
            runCatching { enumValueOf<FfAppTheme>(it) }.getOrNull()
        } ?: FfAppTheme.CherryRed
    }

    private fun setTheme(theme: FfAppTheme) {
        database.ffDatabaseQueries.upsertSetting(FfSettingsKeys.THEME, theme.name)
    }

    private fun getConcurrencyLimit(): Int {
        return database.ffDatabaseQueries
            .selectSettingByKey(FfSettingsKeys.CONCURRENCY_LIMIT)
            .executeAsOneOrNull()
            ?.toIntOrNull()
            ?.coerceIn(1, FfConstants.MAX_LINK_CONCURRENCY)
            ?: FfDefaults.defaultConcurrencyLimit()
    }

    private fun setConcurrencyLimit(limit: Int) {
        val coerced = limit.coerceIn(1, FfConstants.MAX_LINK_CONCURRENCY)
        database.ffDatabaseQueries.upsertSetting(FfSettingsKeys.CONCURRENCY_LIMIT, coerced.toString())
    }
}
