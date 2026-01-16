package love.forte.tools.ff.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * 数据库初始化状态。
 */
sealed interface FfDatabaseInitState {
    data object NotInitialized : FfDatabaseInitState
    data object Initializing : FfDatabaseInitState
    data object Ready : FfDatabaseInitState
    data class Failed(val error: Throwable) : FfDatabaseInitState
}

/**
 * 数据库管理器。
 *
 * 提供统一的数据库访问入口，管理数据库生命周期和初始化。
 */
class FfDatabaseManager private constructor() {

    private val mutex = Mutex()

    @Volatile
    private var state: FfDatabaseInitState = FfDatabaseInitState.NotInitialized

    @Volatile
    private var database: FfDatabase? = null

    @Volatile
    private var currentAppDir: Path? = null

    @Volatile
    private var _settingsRepository: FfSettingsRepository? = null

    @Volatile
    private var _registryRepository: FfRegistryRepository? = null

    @Volatile
    private var _operationHistoryRepository: FfOperationHistoryRepository? = null

    /**
     * 获取当前初始化状态。
     */
    fun getState(): FfDatabaseInitState = state

    /**
     * 获取设置 Repository。
     */
    val settingsRepository: FfSettingsRepository
        get() = _settingsRepository ?: error("Database not initialized")

    /**
     * 获取注册目录 Repository。
     */
    val registryRepository: FfRegistryRepository
        get() = _registryRepository ?: error("Database not initialized")

    /**
     * 获取操作历史 Repository。
     */
    val operationHistoryRepository: FfOperationHistoryRepository
        get() = _operationHistoryRepository ?: error("Database not initialized")

    /**
     * 初始化数据库。
     *
     * @param appDir 应用数据目录
     * @return 初始化状态
     */
    suspend fun initialize(appDir: Path): FfDatabaseInitState = mutex.withLock {
        val normalizedDir = appDir.toAbsolutePath().normalize()

        // 如果已经为同一目录初始化，直接返回
        if (state is FfDatabaseInitState.Ready &&
            currentAppDir?.toAbsolutePath()?.normalize() == normalizedDir
        ) {
            return state
        }

        state = FfDatabaseInitState.Initializing

        try {
            val db = withContext(Dispatchers.IO) {
                FfDatabaseFactory.getDatabase(normalizedDir)
            }

            database = db
            currentAppDir = normalizedDir

            // 创建 Repository 实例
            _settingsRepository = FfSettingsRepository(db)
            _registryRepository = FfRegistryRepository(db)
            _operationHistoryRepository = FfOperationHistoryRepository(db)

            // 记录数据库创建时间（如果是新数据库）
            withContext(Dispatchers.IO) {
                val createdAt = db.ffDatabaseQueries
                    .selectMetaByKey(FfDatabaseMetaKeys.CREATED_AT)
                    .executeAsOneOrNull()

                if (createdAt == null) {
                    db.ffDatabaseQueries.upsertMeta(
                        key = FfDatabaseMetaKeys.CREATED_AT,
                        value_ = System.currentTimeMillis().toString()
                    )
                }
            }

            state = FfDatabaseInitState.Ready
            state
        } catch (e: Exception) {
            state = FfDatabaseInitState.Failed(e)
            state
        }
    }

    /**
     * 切换到新的数据目录。
     */
    suspend fun switchDataDir(newAppDir: Path): FfDatabaseInitState = mutex.withLock {
        // 重置当前状态
        state = FfDatabaseInitState.NotInitialized
        database = null
        currentAppDir = null
        _settingsRepository = null
        _registryRepository = null
        _operationHistoryRepository = null

        // 重置工厂缓存
        FfDatabaseFactory.resetDatabase()

        // 初始化新目录
        initialize(newAppDir)
    }

    /**
     * 关闭数据库。
     */
    suspend fun close() = mutex.withLock {
        FfDatabaseFactory.closeDatabase()
        state = FfDatabaseInitState.NotInitialized
        database = null
        currentAppDir = null
        _settingsRepository = null
        _registryRepository = null
        _operationHistoryRepository = null
    }

    /**
     * 检查数据库是否已初始化。
     */
    fun isInitialized(): Boolean = state is FfDatabaseInitState.Ready

    companion object {
        @Volatile
        private var instance: FfDatabaseManager? = null

        /**
         * 获取单例实例。
         */
        fun getInstance(): FfDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: FfDatabaseManager().also { instance = it }
            }
        }
    }
}
