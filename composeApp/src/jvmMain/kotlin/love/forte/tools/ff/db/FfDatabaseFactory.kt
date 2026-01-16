package love.forte.tools.ff.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * FileFlattener 数据库工厂。
 *
 * 负责创建和管理 SQLite 数据库连接。
 */
object FfDatabaseFactory {
    private const val DB_FILE_NAME = "ff_data.db"

    @Volatile
    private var database: FfDatabase? = null

    @Volatile
    private var currentPath: Path? = null

    /**
     * 获取或创建数据库实例。
     */
    fun getDatabase(appDir: Path): FfDatabase {
        val normalizedPath = appDir.toAbsolutePath().normalize()

        // 如果已经为同一路径创建了数据库，直接返回
        database?.let { db ->
            if (currentPath == normalizedPath) {
                return db
            }
        }

        return createDatabase(normalizedPath).also {
            database = it
            currentPath = normalizedPath
        }
    }

    /**
     * 重置数据库缓存。
     */
    fun resetDatabase() {
        database = null
        currentPath = null
    }

    /**
     * 关闭数据库连接。
     */
    fun closeDatabase() {
        database = null
        currentPath = null
    }

    /**
     * 获取数据库文件路径。
     */
    fun getDatabasePath(appDir: Path): Path = appDir.resolve(DB_FILE_NAME)

    /**
     * 检查数据库是否存在。
     */
    fun databaseExists(appDir: Path): Boolean = Files.exists(getDatabasePath(appDir))

    /**
     * 验证数据库连接。
     */
    fun testConnection(database: FfDatabase): Boolean {
        return try {
            database.ffDatabaseQueries.testConnection().executeAsOne() == 1L
        } catch (e: Exception) {
            false
        }
    }

    private fun createDatabase(appDir: Path): FfDatabase {
        // 确保应用数据目录存在
        Files.createDirectories(appDir)

        val dbPath = appDir.resolve(DB_FILE_NAME)
        val jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

        val driver = JdbcSqliteDriver(
            url = jdbcUrl,
            properties = Properties().apply {
                put("foreign_keys", "true")
            }
        )

        // 处理 schema 创建和迁移
        val currentVersion = getCurrentSchemaVersion(driver)
        val targetVersion = FfDatabase.Schema.version

        if (currentVersion == 0L) {
            // 新数据库，创建 schema
            FfDatabase.Schema.create(driver)
            setSchemaVersion(driver, targetVersion)
        } else if (currentVersion < targetVersion) {
            // 需要迁移
            FfDatabase.Schema.migrate(driver, currentVersion, targetVersion)
            setSchemaVersion(driver, targetVersion)
        }

        return FfDatabase(driver)
    }

    private fun getCurrentSchemaVersion(driver: JdbcSqliteDriver): Long {
        return try {
            driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor ->
                    app.cash.sqldelight.db.QueryResult.Value(
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

    private fun setSchemaVersion(driver: JdbcSqliteDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }
}
