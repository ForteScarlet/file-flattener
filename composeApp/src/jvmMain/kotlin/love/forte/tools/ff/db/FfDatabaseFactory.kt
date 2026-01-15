package love.forte.tools.ff.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.absolutePathString

/**
 * 数据库工厂，负责创建和管理数据库连接。
 */
object FfDatabaseFactory {
    private const val DB_FILE_NAME = "ff_data.db"

    private val mutex = Mutex()
    private var cachedDatabase: FfDatabase? = null
    private var cachedAppDir: Path? = null

    /**
     * 获取或创建数据库实例。
     *
     * @param appDir 应用数据目录
     * @return 数据库实例
     */
    suspend fun getDatabase(appDir: Path): FfDatabase = mutex.withLock {
        val normalizedDir = appDir.toAbsolutePath().normalize()

        // 如果缓存的数据库对应的目录相同，直接返回
        cachedDatabase?.let { db ->
            if (cachedAppDir?.toAbsolutePath()?.normalize() == normalizedDir) {
                return db
            }
        }

        // 创建新的数据库连接
        val database = withContext(Dispatchers.IO) {
            createDatabase(normalizedDir)
        }

        cachedDatabase = database
        cachedAppDir = normalizedDir
        database
    }

    /**
     * 关闭数据库连接。
     */
    suspend fun closeDatabase() = mutex.withLock {
        cachedDatabase = null
        cachedAppDir = null
    }

    /**
     * 重置数据库连接（用于切换数据目录）。
     */
    suspend fun resetDatabase() = mutex.withLock {
        cachedDatabase = null
        cachedAppDir = null
    }

    private fun createDatabase(appDir: Path): FfDatabase {
        Files.createDirectories(appDir)
        val dbPath = appDir.resolve(DB_FILE_NAME)
        val jdbcUrl = "jdbc:sqlite:${dbPath.absolutePathString()}"

        val driver = JdbcSqliteDriver(
            url = jdbcUrl,
            properties = Properties().apply {
                // 启用 WAL 模式以提高并发性能
                put("journal_mode", "WAL")
                // 启用外键约束
                put("foreign_keys", "ON")
            }
        )

        // 获取当前版本
        val currentVersion = getCurrentVersion(driver)

        if (currentVersion == 0L) {
            // 新数据库，创建表结构
            FfDatabase.Schema.create(driver)
        } else if (currentVersion < FfDatabase.Schema.version) {
            // 需要迁移
            FfDatabase.Schema.migrate(driver, currentVersion, FfDatabase.Schema.version)
        }

        return FfDatabase(driver)
    }

    private fun getCurrentVersion(driver: SqlDriver): Long {
        return try {
            driver.executeQuery<Long>(
                identifier = null,
                sql = "PRAGMA user_version;",
                mapper = { cursor ->
                    QueryResult.Value(
                        if (cursor.next().value) {
                            cursor.getLong(0) ?: 0L
                        } else {
                            0L
                        }
                    )
                },
                parameters = 0
            ).value
        } catch (e: Exception) {
            0L
        }
    }
}
