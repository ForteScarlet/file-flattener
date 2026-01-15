package love.forte.tools.ff.storage

import kotlinx.coroutines.runBlocking
import love.forte.tools.ff.db.FfDatabaseManager
import love.forte.tools.ff.db.FfSettingsRepository

/**
 * 设置存储的数据库适配器。
 *
 * 提供与原 [FfSettingsStore] 相同的 API，但底层使用 SQLite 数据库。
 */
class FfSettingsStoreAdapter(
    private val repository: FfSettingsRepository,
) {
    /**
     * 加载应用设置。
     */
    fun load(): FfAppSettings = runBlocking {
        repository.load()
    }

    /**
     * 保存应用设置。
     */
    fun save(settings: FfAppSettings) = runBlocking {
        repository.save(settings)
    }

    /**
     * 异步加载应用设置。
     */
    suspend fun loadAsync(): FfAppSettings = repository.load()

    /**
     * 异步保存应用设置。
     */
    suspend fun saveAsync(settings: FfAppSettings) = repository.save(settings)

    companion object {
        /**
         * 从数据库管理器创建适配器。
         */
        fun fromManager(manager: FfDatabaseManager): FfSettingsStoreAdapter {
            return FfSettingsStoreAdapter(manager.settingsRepository)
        }
    }
}
