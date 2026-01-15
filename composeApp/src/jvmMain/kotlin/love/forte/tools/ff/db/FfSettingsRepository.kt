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
        val theme = database.ffDatabaseQueries.selectSettingByKey(FfSettingsKeys.THEME)
            .executeAsOneOrNull()
            ?.let(::parseTheme)
            ?: FfAppTheme.CherryRed

        val concurrencyLimit = database.ffDatabaseQueries.selectSettingByKey(FfSettingsKeys.CONCURRENCY_LIMIT)
            .executeAsOneOrNull()
            ?.toIntOrNull()
            ?.coerceIn(1, FfConstants.MaxLinkConcurrency)
            ?: FfDefaults.defaultConcurrencyLimit()

        FfAppSettings(
            theme = theme,
            concurrencyLimit = concurrencyLimit,
        )
    }

    /**
     * 保存应用设置。
     */
    suspend fun save(settings: FfAppSettings) = withContext(Dispatchers.IO) {
        database.transaction {
            database.ffDatabaseQueries.upsertSetting(
                key = FfSettingsKeys.THEME,
                value_ = settings.theme.name
            )
            database.ffDatabaseQueries.upsertSetting(
                key = FfSettingsKeys.CONCURRENCY_LIMIT,
                value_ = settings.concurrencyLimit.toString()
            )
        }
    }

    /**
     * 获取单个设置值。
     */
    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        database.ffDatabaseQueries.selectSettingByKey(key).executeAsOneOrNull()
    }

    /**
     * 设置单个值。
     */
    suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        database.ffDatabaseQueries.upsertSetting(key, value)
    }

    private fun parseTheme(raw: String): FfAppTheme =
        runCatching { enumValueOf<FfAppTheme>(raw) }.getOrDefault(FfAppTheme.CherryRed)
}
