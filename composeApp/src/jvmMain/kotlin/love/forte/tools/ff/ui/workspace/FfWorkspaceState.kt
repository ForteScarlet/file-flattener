package love.forte.tools.ff.ui.workspace

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import love.forte.tools.ff.FfConstants
import love.forte.tools.ff.FfDefaults
import love.forte.tools.ff.fs.*
import love.forte.tools.ff.storage.FfAppSettings
import love.forte.tools.ff.storage.FfRegistryStoreAdapter
import java.awt.Desktop
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString

/**
 * 工作区的视图模式
 */
enum class FfWorkspaceViewMode {
    /** 空闲：未选中任何目标，也不在新增模式 */
    Idle,
    /** 已选中某个受管目标目录 */
    TargetSelected,
    /** 新增任务模式 */
    AddMode,
    /** 迁移执行中 */
    Migrating,
}

/**
 * 迁移任务状态
 */
enum class FfMigrationTaskStatus {
    Pending, Running, Finished, Failed
}

/**
 * 迁移任务 UI 数据模型
 */
@Stable
data class FfMigrationTaskUi(
    val id: String,
    val targetDir: Path,
    val expectedTotalFiles: Int,
    val sources: List<Path>,
    val status: FfMigrationTaskStatus = FfMigrationTaskStatus.Pending,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
    val progress: FfFlattenProgress? = null,
    val report: FfFlattenReport? = null,
    val errorMessage: String? = null,
)

/**
 * 工作区状态持有者，采用单向数据流：状态向下传递，事件向上传播
 *
 * @param scope 协程作用域，用于异步操作
 * @param registryStoreAdapter 目标目录注册表适配器
 * @param settings 应用设置
 */
@Stable
class FfWorkspaceState(
    private val scope: CoroutineScope,
    private val registryStoreAdapter: FfRegistryStoreAdapter,
    private val settings: FfAppSettings,
) {
    // region 核心服务
    private val flattener = FfFlattenService()
    private val migrationService = FfMigrationService(flattener)
    private val updateService = FfUpdateService(flattener)
    // endregion

    // region 受管目标列表
    private val _managedTargets = mutableStateListOf<FfManagedTargetEntry>()
    val managedTargets: SnapshotStateList<FfManagedTargetEntry> get() = _managedTargets
    // endregion

    // region 选中状态
    var selectedTargetDir: Path? by mutableStateOf(null)
        private set

    /** 当前选中的受管目标条目 */
    val selectedEntry: FfManagedTargetEntry?
        get() = selectedTargetDir?.let { dir ->
            _managedTargets.firstOrNull { it.targetDir.normalize() == dir.normalize() }
        }
    // endregion

    // region 视图模式
    var viewMode: FfWorkspaceViewMode by mutableStateOf(FfWorkspaceViewMode.Idle)
        private set
    // endregion

    // region 全局消息（用于显示临时提示）
    var globalMessage: String? by mutableStateOf(null)
        private set

    fun showMessage(message: String?) {
        globalMessage = message
    }

    fun clearMessage() {
        globalMessage = null
    }
    // endregion

    // region 更新状态
    var isUpdating: Boolean by mutableStateOf(false)
        private set

    val ffFlattenProgressStates = FfFlattenProgressStates()
    // endregion

    // region 草稿任务（新增模式）
    private val _drafts = mutableStateListOf<FfDraftTask>()
    val drafts: SnapshotStateList<FfDraftTask> get() = _drafts
    // endregion

    // region 迁移任务
    private val _migrationTasks = mutableStateListOf<FfMigrationTaskUi>()
    val migrationTasks: SnapshotStateList<FfMigrationTaskUi> get() = _migrationTasks

    var migrationStartedAt: Long? by mutableStateOf(null)
        private set

    var migrationFinishedAt: Long? by mutableStateOf(null)
        private set
    // endregion

    // region 导航与模式切换

    /** 进入新增模式 */
    fun enterAddMode() {
        viewMode = FfWorkspaceViewMode.AddMode
        selectedTargetDir = null
        _drafts.clear()
    }

    /** 退出新增模式 */
    fun exitAddMode() {
        viewMode = FfWorkspaceViewMode.Idle
        _drafts.clear()
    }

    /** 选中某个受管目标 */
    fun selectTarget(targetDir: Path) {
        selectedTargetDir = targetDir
        viewMode = FfWorkspaceViewMode.TargetSelected
        _drafts.clear()
    }

    /** 取消选中 */
    fun clearSelection() {
        selectedTargetDir = null
        viewMode = FfWorkspaceViewMode.Idle
    }
    // endregion

    // region 数据加载

    /** 重新加载受管目标列表 */
    fun reloadTargets() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { registryStoreAdapter.loadTargets() }
            val entries = withContext(Dispatchers.IO) {
                FfWorkspaceLoader.loadManagedTargets(loaded)
            }
            _managedTargets.clear()
            _managedTargets.addAll(entries)

            // 清理无效的目标
            val valid = entries.map { it.targetDir }
            if (valid.size != loaded.size) {
                withContext(Dispatchers.IO) { registryStoreAdapter.saveTargets(valid) }
            }

            // 如果当前选中的目标已不存在，清除选中
            if (selectedTargetDir != null && valid.none { it.normalize() == selectedTargetDir?.normalize() }) {
                selectedTargetDir = null
                if (viewMode == FfWorkspaceViewMode.TargetSelected) {
                    viewMode = FfWorkspaceViewMode.Idle
                }
            }
        }
    }
    // endregion

    // region 草稿操作

    /** 更新指定草稿 */
    fun updateDraft(id: String, transform: (FfDraftTask) -> FfDraftTask) {
        val idx = _drafts.indexOfFirst { it.id == id }
        if (idx < 0) return
        _drafts[idx] = transform(_drafts[idx])
    }

    /** 移除指定草稿 */
    fun removeDraft(id: String) {
        _drafts.removeAll { it.id == id }
    }

    /** 添加源目录（异步扫描扩展名） */
    fun addSourceDirs(picked: List<Path>) {
        scope.launch {
            val unique = picked
                .asSequence()
                .map { it.toAbsolutePath().normalize() }
                .distinctBy { it.absolutePathString() }
                .toList()

            val allowed = mutableListOf<Path>()
            var ignoredManaged = 0
            for (dir in unique) {
                val managed = withContext(Dispatchers.IO) {
                    FfMarkerFile.isManagedDirectory(dir)
                }
                if (managed) {
                    ignoredManaged++
                } else {
                    allowed.add(dir)
                }
            }

            if (allowed.isEmpty()) {
                globalMessage = if (ignoredManaged > 0) "选择的目录均为受管目录，已忽略。" else "未选择有效源目录。"
                return@launch
            }

            val newDrafts = allowed.map(::createDraft)
            _drafts.addAll(newDrafts)

            // 逐个扫描扩展名
            newDrafts.forEach { draft ->
                updateDraft(draft.id) { it.copy(scanState = FfScanState.Scanning) }
                scope.launch(Dispatchers.IO) {
                    runCatching { FfDirectoryScanner.scanExtensions(draft.sourceDir) }
                        .onSuccess { stats ->
                            val all = stats.keys.toSet()
                            withContext(Dispatchers.Main) {
                                updateDraft(draft.id) {
                                    it.copy(
                                        scanState = FfScanState.Done(stats),
                                        selectedExtensions = all,
                                    )
                                }
                            }
                        }
                        .onFailure { e ->
                            withContext(Dispatchers.Main) {
                                updateDraft(draft.id) { it.copy(scanState = FfScanState.Failed(e.message.orEmpty())) }
                            }
                        }
                }
            }
        }
    }
    // endregion

    // region 迁移操作

    /** 更新迁移任务状态 */
    private fun updateMigrationTask(targetDir: Path, transform: (FfMigrationTaskUi) -> FfMigrationTaskUi) {
        val idx = _migrationTasks.indexOfFirst { it.targetDir.normalize() == targetDir.normalize() }
        if (idx < 0) return
        _migrationTasks[idx] = transform(_migrationTasks[idx])
    }

    /** 开始执行迁移 */
    fun startMigration() {
        val runnable = _drafts.filter { it.canRun() }
        if (runnable.isEmpty()) {
            globalMessage = "没有可执行的任务：请检查目标目录与扩展名选择。"
            return
        }

        // 按目标目录分组
        val grouped = runnable.groupBy {
            requireNotNull(it.targetDir).toAbsolutePath().normalize().absolutePathString()
        }

        val tasks = grouped.values.map { group ->
            val targetDir = requireNotNull(group.first().targetDir).toAbsolutePath().normalize()
            val sources = group.map { draft ->
                FfFlattenSourceConfig(
                    sourceDir = draft.sourceDir,
                    selectedExtensions = draft.selectedExtensions,
                )
            }
            val expected = group.sumOf { expectedSelectedCount(it) }
            FfMigrationTask(
                targetDir = targetDir,
                sources = sources,
                expectedTotalFiles = expected,
            )
        }

        // 初始化迁移任务 UI 列表
        _migrationTasks.clear()
        _migrationTasks.addAll(
            tasks.map {
                FfMigrationTaskUi(
                    id = UUID.randomUUID().toString(),
                    targetDir = it.targetDir.toAbsolutePath().normalize(),
                    expectedTotalFiles = it.expectedTotalFiles,
                    sources = it.sources.map { s -> s.sourceDir },
                )
            },
        )

        migrationStartedAt = System.currentTimeMillis()
        migrationFinishedAt = null
        viewMode = FfWorkspaceViewMode.Migrating

        val limit = settings.concurrencyLimit
        val linkConcurrencyPerTask = FfDefaults.defaultLinkConcurrencyPerTask(limit)

        scope.launch {
            val report = migrationService.migrate(
                tasks = tasks,
                concurrencyLimit = limit,
                linkConcurrencyPerTask = linkConcurrencyPerTask,
                onTaskProgress = { targetDir, progress ->
                    scope.launch {
                        updateMigrationTask(targetDir) { current ->
                            current.copy(
                                status = FfMigrationTaskStatus.Running,
                                startedAtEpochMillis = current.startedAtEpochMillis ?: System.currentTimeMillis(),
                                progress = progress,
                            )
                        }
                    }
                },
            )

            withContext(Dispatchers.Main) {
                report.taskResults.forEach { result ->
                    val targetDir = result.task.targetDir.toAbsolutePath().normalize()
                    updateMigrationTask(targetDir) { current ->
                        when {
                            result.report != null && result.report.failedLinks == 0 -> current.copy(
                                status = FfMigrationTaskStatus.Finished,
                                finishedAtEpochMillis = result.report.finishedAtEpochMillis,
                                report = result.report,
                                progress = null,
                                errorMessage = null,
                            )

                            result.report != null -> current.copy(
                                status = FfMigrationTaskStatus.Failed,
                                finishedAtEpochMillis = result.report.finishedAtEpochMillis,
                                report = result.report,
                                progress = null,
                                errorMessage = "失败 ${result.report.failedLinks} 个文件（可查看详情或重试）",
                            )

                            else -> current.copy(
                                status = FfMigrationTaskStatus.Failed,
                                finishedAtEpochMillis = System.currentTimeMillis(),
                                errorMessage = result.errorMessage,
                                progress = null,
                            )
                        }
                    }
                }
                migrationFinishedAt = report.finishedAtEpochMillis
            }

            // 将成功的目标目录添加到注册表
            withContext(Dispatchers.IO) {
                val targetsToAdd = report.taskResults
                    .asSequence()
                    .filter { it.report != null }
                    .map { it.task.targetDir }
                    .distinctBy { it.toAbsolutePath().normalize().absolutePathString() }
                    .toList()
                if (targetsToAdd.isNotEmpty()) {
                    val current = registryStoreAdapter.loadTargets()
                    registryStoreAdapter.saveTargets(current + targetsToAdd)
                }
            }
            reloadTargets()
            globalMessage = "迁移已完成：已刷新工作区列表。"
        }
    }

    /** 退出迁移视图（仅在迁移完成后可用） */
    fun exitMigration() {
        if (migrationFinishedAt != null) {
            _migrationTasks.clear()
            migrationStartedAt = null
            migrationFinishedAt = null
            viewMode = FfWorkspaceViewMode.Idle
            _drafts.clear()
        }
    }
    // endregion

    // region 受管目标操作

    /** 打开目标目录 */
    fun openTargetDir() {
        val target = selectedTargetDir ?: return
        runCatching { Desktop.getDesktop().open(target.toFile()) }
            .onFailure { globalMessage = it.message ?: "无法打开目录" }
    }

    /** 打开源目录 */
    fun openSourceDirs() {
        val entry = selectedEntry ?: return
        val sources = entry.sources
            .asSequence()
            .mapNotNull { runCatching { Path.of(it) }.getOrNull() }
            .distinctBy { it.toAbsolutePath().normalize().absolutePathString() }
            .toList()
        if (sources.isEmpty()) {
            globalMessage = "未找到可打开的源目录。"
            return
        }
        sources.forEach { src ->
            runCatching { Desktop.getDesktop().open(src.toFile()) }
                .onFailure { globalMessage = it.message ?: "无法打开源目录：${src.fileName}" }
        }
    }

    /** 从注册表中移除目标目录 */
    fun removeTarget() {
        val target = selectedTargetDir ?: return
        scope.launch(Dispatchers.IO) {
            val current = registryStoreAdapter.loadTargets()
                .filterNot { it.normalize() == target.normalize() }
            registryStoreAdapter.saveTargets(current)
            withContext(Dispatchers.Main) {
                selectedTargetDir = null
                viewMode = FfWorkspaceViewMode.Idle
                reloadTargets()
            }
        }
    }

    /** 更新目标目录（重新扫描源目录并刷新硬链接） */
    fun updateTarget() {
        val target = selectedTargetDir ?: return
        if (isUpdating) return
        isUpdating = true
        scope.launch {
            val result = updateService.update(
                targetDir = target,
                ffFlattenProgressStates = ffFlattenProgressStates,
                linkConcurrency = FfDefaults.defaultLinkConcurrencyPerTask(settings.concurrencyLimit),
            )
            withContext(Dispatchers.Main) {
                isUpdating = false
                when (result) {
                    is FfUpdateResult.Success -> {
                        reloadTargets()
                        globalMessage = "已更新：${target.fileName}"
                    }

                    is FfUpdateResult.Failed -> {
                        globalMessage = result.message
                    }

                    is FfUpdateResult.TrashFailed -> {
                        globalMessage = result.message
                        reloadTargets()
                    }
                }
            }
        }
    }
    // endregion

    companion object {
        /** 创建新草稿，默认目标目录为源目录下的 flatten 子目录 */
        fun createDraft(sourceDir: Path): FfDraftTask {
            val defaultTargetDir = sourceDir.resolve("flatten")
            val defaultTargetPathText = defaultTargetDir.absolutePathString()
            val validation = FfTargetValidator.validate(defaultTargetDir)
            return FfDraftTask(
                id = UUID.randomUUID().toString(),
                sourceDir = sourceDir,
                targetPathText = defaultTargetPathText,
                targetDir = defaultTargetDir,
                targetValidation = validation,
                scanState = FfScanState.Scanning,
            )
        }

        /** 计算草稿中选中的文件数量 */
        fun expectedSelectedCount(draft: FfDraftTask): Int {
            val done = draft.scanState as? FfScanState.Done ?: return 0
            return done.extensionStats
                .asSequence()
                .filter { (ext, _) -> ext in draft.selectedExtensions }
                .sumOf { it.value }
        }
    }
}

/** 判断草稿是否可执行 */
fun FfDraftTask.canRun(): Boolean {
    if (targetDir == null) return false
    if (targetValidation == FfTargetValidation.NotAllowedNotEmpty ||
        targetValidation == FfTargetValidation.NotAllowedNotDirectory
    ) return false
    if (scanState !is FfScanState.Done) return false
    if (selectedExtensions.isEmpty()) return false
    return true
}
