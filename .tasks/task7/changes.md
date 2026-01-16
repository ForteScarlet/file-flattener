# Task7 变更详情

## 文件变更明细

### 删除的文件

| 文件 | 说明 |
|------|------|
| `FfLegacyMigrationHelper.kt` | 旧数据迁移助手，负责从文件存储迁移到 SQLite |
| `FfRegistryStore.kt` | 旧的注册目录文件存储实现 |
| `FfSettingsStore.kt` | 旧的设置文件存储实现（已重构为 `FfAppSettings.kt`） |

### 新增的文件

| 文件 | 说明 |
|------|------|
| `FfAppSettings.kt` | 仅包含 `FfAppTheme` 枚举和 `FfAppSettings` 数据类 |

### 修改的文件

#### FfApp.kt
```kotlin
// 修改前：依赖 userDataDir，可能在数据库初始化前访问
val settingsStoreAdapter = remember(userDataDir) {
    userDataDir?.let { FfSettingsStoreAdapter.fromManager(databaseManager) }
}

// 修改后：依赖 dbInitState，确保数据库已初始化
val settingsStoreAdapter = remember(dbInitState) {
    if (dbInitState is FfDatabaseInitState.Ready) {
        FfSettingsStoreAdapter.fromManager(databaseManager)
    } else null
}
```

#### FfDatabaseManager.kt
```kotlin
// 修改前
data class Ready(val migrationResult: FfLegacyMigrationResult?) : FfDatabaseInitState

// 修改后
data object Ready : FfDatabaseInitState
```

移除了旧数据迁移调用：
```kotlin
// 已删除
val migrationResult = withContext(Dispatchers.IO) {
    val helper = FfLegacyMigrationHelper(db, normalizedDir)
    if (helper.needsMigration()) {
        helper.migrate()
    } else {
        null
    }
}
```

#### FfDatabaseConstants.kt
```kotlin
// 已删除
const val LEGACY_MIGRATION_DONE = "legacy_migration_done"
const val LEGACY_MIGRATION_AT = "legacy_migration_at"
```

#### FfConstants.kt
```kotlin
// 修改前
const val LegacyDefaultConcurrency: Int = 8  // 旧版本默认并发度（保留兼容用）

// 修改后
const val DefaultConcurrency: Int = 8  // 默认并发度
```

#### FfAppPaths.kt
```kotlin
// 已删除
fun registryFile(appDir: Path): Path = appDir.resolve("registry.txt")
fun settingsFile(appDir: Path): Path = appDir.resolve("settings.properties")
```

#### FfMarkerFile.kt
```kotlin
// 修改前：支持 JSON 和旧 KV 格式
fun read(directory: Path): FfMarkerConfig? {
    val raw = Files.readString(marker, StandardCharsets.UTF_8)
    val trimmed = raw.trimStart()
    return if (trimmed.startsWith("{")) {
        readJson(trimmed)
    } else {
        readLegacyKv(raw)
    }
}

// 修改后：仅支持 JSON 格式
fun read(directory: Path): FfMarkerConfig? {
    val raw = Files.readString(marker, StandardCharsets.UTF_8)
    return runCatching { FfJson.instance.decodeFromString(FfMarkerConfig.serializer(), raw.trimStart()) }
        .getOrNull()
        ?.takeIf { it.sources.isNotEmpty() && it.createdAtEpochMillis > 0 }
}
```

#### FfFlattenService.kt
```kotlin
// 修改前
val linkConcurrency: Int = FfConstants.LegacyDefaultConcurrency

// 修改后
val linkConcurrency: Int = FfConstants.DefaultConcurrency
```

#### UI 文件资源引用修复
- `FfPanelScreen.kt`
- `FfWorkspaceScreen.kt`
- `FfConfigScreen.kt`

```kotlin
// 修改前
import androidx.compose.ui.res.painterResource
painterResource("drawable/ic_arrow_back.svg")

// 修改后
import org.jetbrains.compose.resources.painterResource
import love.forte.tools.file_flattener.composeapp.generated.resources.Res
import love.forte.tools.file_flattener.composeapp.generated.resources.ic_arrow_back
painterResource(Res.drawable.ic_arrow_back)
```

## 清理的兼容性代码类型

1. **旧数据迁移** - 从 properties/txt 文件迁移到 SQLite
2. **旧设置键** - `themeMode`、`linkConcurrency` 等旧键名
3. **旧 Marker 格式** - KV 格式的 `.ff` 文件解析
4. **旧文件路径** - `registry.txt`、`settings.properties`
5. **旧常量命名** - `LegacyDefaultConcurrency`
