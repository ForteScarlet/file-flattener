package love.forte.tools.ff.storage

import kotlinx.coroutines.runBlocking
import love.forte.tools.ff.db.FfDatabaseManager
import love.forte.tools.ff.db.FfRegistryRepository
import java.nio.file.Path

/**
 * 注册目录存储的数据库适配器。
 *
 * 提供与原 [FfRegistryStore] 相同的 API，但底层使用 SQLite 数据库。
 */
class FfRegistryStoreAdapter(
    private val repository: FfRegistryRepository,
) {
    /**
     * 加载所有注册的目标目录。
     */
    fun loadTargets(): List<Path> = runBlocking {
        repository.loadTargets()
    }

    /**
     * 保存目标目录列表。
     */
    fun saveTargets(targets: Collection<Path>) = runBlocking {
        repository.saveTargets(targets)
    }

    /**
     * 异步加载所有注册的目标目录。
     */
    suspend fun loadTargetsAsync(): List<Path> = repository.loadTargets()

    /**
     * 异步保存目标目录列表。
     */
    suspend fun saveTargetsAsync(targets: Collection<Path>) = repository.saveTargets(targets)

    /**
     * 异步添加单个目标目录。
     */
    suspend fun addTargetAsync(path: Path): Boolean = repository.addTarget(path)

    /**
     * 异步移除单个目标目录。
     */
    suspend fun removeTargetAsync(path: Path): Boolean = repository.removeTarget(path)

    companion object {
        /**
         * 从数据库管理器创建适配器。
         */
        fun fromManager(manager: FfDatabaseManager): FfRegistryStoreAdapter {
            return FfRegistryStoreAdapter(manager.registryRepository)
        }
    }
}
