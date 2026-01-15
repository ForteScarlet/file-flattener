package love.forte.tools.ff.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import love.forte.tools.ff.storage.FfAppPaths
import love.forte.tools.ff.storage.FfRegistryStore
import love.forte.tools.ff.storage.FfSettingsStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/**
 * 旧数据迁移结果。
 */
data class FfLegacyMigrationResult(
    val success: Boolean,
    val settingsMigrated: Boolean,
    val registryMigrated: Boolean,
    val settingsCount: Int,
    val registryCount: Int,
    val errors: List<String>,
)

/**
 * 旧数据迁移助手。
 *
 * 负责将旧的文件存储格式（settings.properties, registry.txt）迁移到 SQLite 数据库。
 */
class FfLegacyMigrationHelper(
    private val database: FfDatabase,
    private val appDir: Path,
) {
    private val settingsFile: Path = FfAppPaths.settingsFile(appDir)
    private val registryFile: Path = FfAppPaths.registryFile(appDir)

    /**
     * 检查是否需要进行旧数据迁移。
     */
    suspend fun needsMigration(): Boolean = withContext(Dispatchers.IO) {
        // 检查数据库中是否已标记迁移完成
        val migrationDone = database.ffDatabaseQueries
            .selectMetaByKey(FfDatabaseMetaKeys.LEGACY_MIGRATION_DONE)
            .executeAsOneOrNull()
            ?.toBooleanStrictOrNull()
            ?: false

        if (migrationDone) return@withContext false

        // 检查是否存在旧数据文件
        settingsFile.exists() || registryFile.exists()
    }

    /**
     * 执行旧数据迁移。
     */
    suspend fun migrate(): FfLegacyMigrationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var settingsMigrated = false
        var registryMigrated = false
        var settingsCount = 0
        var registryCount = 0

        try {
            database.transaction {
                // 迁移设置
                if (settingsFile.exists()) {
                    val result = migrateSettings()
                    settingsMigrated = result.first
                    settingsCount = result.second
                    if (!settingsMigrated) {
                        errors.add("Settings migration failed")
                    }
                }

                // 迁移注册目录
                if (registryFile.exists()) {
                    val result = migrateRegistry()
                    registryMigrated = result.first
                    registryCount = result.second
                    if (!registryMigrated) {
                        errors.add("Registry migration failed")
                    }
                }

                // 标记迁移完成
                markMigrationComplete()
            }

            // 备份旧文件
            backupLegacyFiles()

            FfLegacyMigrationResult(
                success = errors.isEmpty(),
                settingsMigrated = settingsMigrated,
                registryMigrated = registryMigrated,
                settingsCount = settingsCount,
                registryCount = registryCount,
                errors = errors,
            )
        } catch (e: Exception) {
            errors.add("Migration error: ${e.message}")
            FfLegacyMigrationResult(
                success = false,
                settingsMigrated = settingsMigrated,
                registryMigrated = registryMigrated,
                settingsCount = settingsCount,
                registryCount = registryCount,
                errors = errors,
            )
        }
    }

    private fun migrateSettings(): Pair<Boolean, Int> {
        return try {
            val oldStore = FfSettingsStore(appDir)
            val oldSettings = oldStore.load()

            database.ffDatabaseQueries.upsertSetting(
                key = FfSettingsKeys.THEME,
                value_ = oldSettings.theme.name
            )
            database.ffDatabaseQueries.upsertSetting(
                key = FfSettingsKeys.CONCURRENCY_LIMIT,
                value_ = oldSettings.concurrencyLimit.toString()
            )

            true to 2
        } catch (e: Exception) {
            false to 0
        }
    }

    private fun migrateRegistry(): Pair<Boolean, Int> {
        return try {
            val oldStore = FfRegistryStore(appDir)
            val targets = oldStore.loadTargets()
            val now = System.currentTimeMillis()

            targets.forEachIndexed { index, path ->
                database.ffDatabaseQueries.insertRegistry(
                    target_path = path.absolutePathString(),
                    created_at = now + index // 保持顺序
                )
            }

            true to targets.size
        } catch (e: Exception) {
            false to 0
        }
    }

    private fun markMigrationComplete() {
        val now = System.currentTimeMillis().toString()
        database.ffDatabaseQueries.upsertMeta(
            key = FfDatabaseMetaKeys.LEGACY_MIGRATION_DONE,
            value_ = "true"
        )
        database.ffDatabaseQueries.upsertMeta(
            key = FfDatabaseMetaKeys.LEGACY_MIGRATION_AT,
            value_ = now
        )
    }

    private fun backupLegacyFiles() {
        val timestamp = System.currentTimeMillis()
        val filesToBackup = listOf(settingsFile, registryFile)

        filesToBackup.filter { it.exists() }.forEach { file ->
            val backup = file.resolveSibling("${file.fileName}.bak.$timestamp")
            runCatching {
                Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
