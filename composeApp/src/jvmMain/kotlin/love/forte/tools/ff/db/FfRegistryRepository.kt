package love.forte.tools.ff.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * 注册目录数据访问层。
 */
class FfRegistryRepository(
    private val database: FfDatabase,
) {
    /**
     * 加载所有注册的目标目录路径。
     */
    suspend fun loadTargets(): List<Path> = withContext(Dispatchers.IO) {
        database.ffDatabaseQueries.selectAllRegistry()
            .executeAsList()
            .asSequence()
            .map { Path.of(it.target_path) }
            .filter { it.exists() && it.isDirectory() }
            .distinctBy { it.normalize().absolutePathString() }
            .toList()
    }

    /**
     * 加载所有注册的目标目录路径（包括可能不存在的）。
     */
    suspend fun loadAllTargetPaths(): List<String> = withContext(Dispatchers.IO) {
        database.ffDatabaseQueries.selectAllRegistry()
            .executeAsList()
            .map { it.target_path }
    }

    /**
     * 保存目标目录列表。
     * 这会替换所有现有的注册目录。
     */
    suspend fun saveTargets(targets: Collection<Path>) = withContext(Dispatchers.IO) {
        val normalized = targets
            .asSequence()
            .map { it.normalize().toAbsolutePath() }
            .distinctBy { it.absolutePathString() }
            .toList()

        val now = System.currentTimeMillis()

        database.transaction {
            // 清除所有现有记录
            database.ffDatabaseQueries.deleteAllRegistry()

            // 插入新记录
            normalized.forEachIndexed { index, path ->
                database.ffDatabaseQueries.insertRegistry(
                    target_path = path.absolutePathString(),
                    created_at = now + index // 保持顺序
                )
            }
        }
    }

    /**
     * 添加单个目标目录。
     */
    suspend fun addTarget(path: Path): Boolean = withContext(Dispatchers.IO) {
        val normalizedPath = path.normalize().toAbsolutePath().absolutePathString()

        // 检查是否已存在
        val exists = database.ffDatabaseQueries.registryExistsByPath(normalizedPath)
            .executeAsOne()

        if (exists) return@withContext false

        database.ffDatabaseQueries.insertRegistry(
            target_path = normalizedPath,
            created_at = System.currentTimeMillis()
        )
        true
    }

    /**
     * 移除单个目标目录。
     */
    suspend fun removeTarget(path: Path): Boolean = withContext(Dispatchers.IO) {
        val normalizedPath = path.normalize().toAbsolutePath().absolutePathString()

        // 检查是否存在
        val exists = database.ffDatabaseQueries.registryExistsByPath(normalizedPath)
            .executeAsOne()

        if (!exists) return@withContext false

        database.ffDatabaseQueries.deleteRegistryByPath(normalizedPath)
        true
    }

    /**
     * 检查路径是否已注册。
     */
    suspend fun isRegistered(path: Path): Boolean = withContext(Dispatchers.IO) {
        val normalizedPath = path.normalize().toAbsolutePath().absolutePathString()
        database.ffDatabaseQueries.registryExistsByPath(normalizedPath).executeAsOne()
    }
}
