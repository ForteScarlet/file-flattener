# Task7 完成报告

## 任务概述

根据 task7.md 的要求，完成以下两项主要任务：
1. 移除所有兼容性代码
2. 修复启动报错问题

## 问题分析

### 启动报错问题

**错误信息**：
```
java.lang.IllegalStateException: Database not initialized
    at love.forte.tools.ff.db.FfDatabaseManager.getSettingsRepository(FfDatabaseManager.kt:55)
```

**根本原因**：
在 `FfApp.kt` 中，`settingsStoreAdapter` 和 `registryStoreAdapter` 的创建依赖于 `userDataDir`，但在 `userDataDir` 被设置后立即执行 `remember` 块，此时数据库初始化（在另一个 `LaunchedEffect` 中异步进行）尚未完成。

**修复方案**：
将 adapter 的创建条件从依赖 `userDataDir` 改为依赖 `dbInitState`，确保只有在数据库初始化完成后才创建 adapter。

### 兼容性代码清理

项目未上线，不需要考虑任何兼容性，因此清理了所有为"兼容旧代码"而存在的内容。

## 修改清单

### 1. FfApp.kt
- 修复数据库初始化时序问题
- 将 `settingsStoreAdapter` 和 `registryStoreAdapter` 的创建条件从 `userDataDir` 改为 `dbInitState`

### 2. 删除的文件
- `FfLegacyMigrationHelper.kt` - 旧数据迁移助手（从文件存储迁移到 SQLite）
- `FfRegistryStore.kt` - 旧的注册目录文件存储实现

### 3. FfDatabaseManager.kt
- 移除 `FfLegacyMigrationResult` 引用
- 将 `FfDatabaseInitState.Ready` 从 `data class` 改为 `data object`（不再携带迁移结果）
- 移除旧数据迁移调用逻辑

### 4. FfDatabaseConstants.kt
- 移除 `LEGACY_MIGRATION_DONE` 常量
- 移除 `LEGACY_MIGRATION_AT` 常量

### 5. FfConstants.kt
- 将 `LegacyDefaultConcurrency` 重命名为 `DefaultConcurrency`
- 更新注释，移除"保留兼容用"说明

### 6. FfAppPaths.kt
- 移除 `registryFile()` 方法
- 移除 `settingsFile()` 方法

### 7. FfSettingsStore.kt → FfAppSettings.kt
- 重命名文件
- 移除 `FfSettingsStore` 类（旧的文件存储实现）
- 移除 legacy key 处理逻辑（`ThemeModeLegacy`、`LinkConcurrencyLegacy`）
- 仅保留 `FfAppTheme` 枚举和 `FfAppSettings` 数据类

### 8. FfMarkerFile.kt
- 移除 `readLegacyKv()` 方法（读取旧格式 KV marker 文件）
- 移除 `parseLine()` 辅助方法
- 移除 `Keys` 对象
- 简化 `read()` 方法，仅支持 JSON 格式

### 9. FfFlattenService.kt
- 将默认并发度从 `FfConstants.LegacyDefaultConcurrency` 改为 `FfConstants.DefaultConcurrency`

### 10. 资源引用修复（附带修复）
- `FfPanelScreen.kt` - 修复 `painterResource` 使用方式
- `FfWorkspaceScreen.kt` - 修复 `painterResource` 使用方式
- `FfConfigScreen.kt` - 修复 `painterResource` 使用方式
- 统一使用 Compose Resources API (`org.jetbrains.compose.resources.painterResource`)

## 验证结果

- ✅ 编译通过
- ✅ 应用启动正常（原 `Database not initialized` 错误已修复）
- ✅ 所有兼容性代码已清理

## 架构改进

清理后的代码更加简洁：
- 数据存储统一使用 SQLite 数据库
- 移除了文件存储的历史包袱
- Marker 文件仅支持 JSON 格式
- 资源引用统一使用 Compose Resources API
