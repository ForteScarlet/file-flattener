package love.forte.tools.ff.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import love.forte.tools.ff.FfConstants
import love.forte.tools.ff.FfDefaults
import love.forte.tools.ff.fs.FfDirectoryScanner
import love.forte.tools.ff.fs.FfFlattenService
import love.forte.tools.ff.fs.FfFlattenSourceConfig
import love.forte.tools.ff.fs.FfFlattenTaskConfig
import love.forte.tools.ff.fs.FfMarkerFile
import love.forte.tools.ff.fs.FfMigrationService
import love.forte.tools.ff.fs.FfMigrationTask
import love.forte.tools.ff.fs.FfTargetValidation
import love.forte.tools.ff.fs.FfTargetValidator
import love.forte.tools.ff.fs.FfUpdateService
import love.forte.tools.ff.fs.FfUpdateResult
import love.forte.tools.ff.fs.FfTempCleanupChoice
import love.forte.tools.ff.storage.FfAppSettings
import love.forte.tools.ff.storage.FfRegistryStoreAdapter
import love.forte.tools.ff.ui.components.FfOutlinedButton
import love.forte.tools.ff.ui.components.FfPrimaryButton
import love.forte.tools.ff.ui.components.FfTertiaryButton
import love.forte.tools.ff.ui.platform.FfFileDialogs
import love.forte.tools.ff.ui.workspace.FfDraftTask
import love.forte.tools.ff.ui.workspace.FfManagedTargetEntry
import love.forte.tools.ff.ui.workspace.FfScanState
import love.forte.tools.ff.ui.workspace.FfWorkspaceLoader
import love.forte.tools.file_flattener.composeapp.generated.resources.Res
import love.forte.tools.file_flattener.composeapp.generated.resources.ic_folder_open
import love.forte.tools.file_flattener.composeapp.generated.resources.ic_refresh
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URI
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.absolutePathString

@Composable
fun FfWorkspaceScreen(
    appDir: Path,
    registryStoreAdapter: FfRegistryStoreAdapter,
    settings: FfAppSettings,
) {
    val scope = rememberCoroutineScope()
    val flattener = remember { FfFlattenService() }
    val migrationService = remember { FfMigrationService(flattener) }
    val updateService = remember { FfUpdateService(flattener) }

    var managedTargets by remember { mutableStateOf<List<FfManagedTargetEntry>>(emptyList()) }
    var selectedTargetDir by remember { mutableStateOf<Path?>(null) }
    var addMode by remember { mutableStateOf(false) }
    var globalMessage by remember { mutableStateOf<String?>(null) }
    var isUpdating by remember { mutableStateOf(false) }
    var pendingTempCleanup by remember { mutableStateOf<Path?>(null) }

    val drafts = remember { mutableStateListOf<FfDraftTask>() }

    val migrationTasks = remember { mutableStateListOf<FfMigrationTaskUi>() }
    var migrationStartedAt by remember { mutableStateOf<Long?>(null) }
    var migrationFinishedAt by remember { mutableStateOf<Long?>(null) }

    fun updateDraft(id: String, transform: (FfDraftTask) -> FfDraftTask) {
        val idx = drafts.indexOfFirst { it.id == id }
        if (idx < 0) return
        drafts[idx] = transform(drafts[idx])
    }

    fun updateMigrationTask(targetDir: Path, transform: (FfMigrationTaskUi) -> FfMigrationTaskUi) {
        val idx = migrationTasks.indexOfFirst { it.targetDir.normalize() == targetDir.normalize() }
        if (idx < 0) return
        migrationTasks[idx] = transform(migrationTasks[idx])
    }

    fun reloadTargetsAsync() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { registryStoreAdapter.loadTargets() }
            val entries = withContext(Dispatchers.IO) { FfWorkspaceLoader.loadManagedTargets(loaded) }
            managedTargets = entries
            val valid = entries.map { it.targetDir }
            if (valid.size != loaded.size) withContext(Dispatchers.IO) { registryStoreAdapter.saveTargets(valid) }
            if (selectedTargetDir != null && valid.none { it.normalize() == selectedTargetDir?.normalize() }) {
                selectedTargetDir = null
            }
        }
    }

    fun addSourceDirsAsync(picked: List<Path>) {
        scope.launch {
            val unique = picked
                .asSequence()
                .map { it.toAbsolutePath().normalize() }
                .distinctBy { it.absolutePathString() }
                .toList()

            val allowed = mutableListOf<Path>()
            var ignoredManaged = 0
            for (dir in unique) {
                val managed = withContext(Dispatchers.IO) { FfMarkerFile.isManagedDirectory(dir) }
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

            val newDrafts = allowed.map(::newDraft)
            drafts.addAll(newDrafts)
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

    fun startMigration() {
        val runnable = drafts.filter { it.canRun() }
        if (runnable.isEmpty()) {
            globalMessage = "没有可执行的任务：请检查目标目录与扩展名选择。"
            return
        }

        val grouped = runnable.groupBy { requireNotNull(it.targetDir).toAbsolutePath().normalize().absolutePathString() }
        val tasks = grouped.values.map { group ->
            val targetDir = requireNotNull(group.first().targetDir).toAbsolutePath().normalize()
            val sources = group.map { draft ->
                FfFlattenSourceConfig(
                    sourceDir = draft.sourceDir,
                    selectedExtensions = draft.selectedExtensions,
                )
            }
            val expected = group.sumOf(::expectedSelectedCount)
            FfMigrationTask(
                targetDir = targetDir,
                sources = sources,
                expectedTotalFiles = expected,
            )
        }

        migrationTasks.clear()
        migrationTasks.addAll(
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
            reloadTargetsAsync()
            globalMessage = "迁移已完成：已刷新工作区列表。"
        }
    }

    LaunchedEffect(Unit) { reloadTargetsAsync() }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left: managed targets list
        Column(modifier = Modifier.width(300.dp).fillMaxHeight().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "工作区", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.weight(1f))
                FfOutlinedButton(text = "新增", onClick = { addMode = true; selectedTargetDir = null })
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (managedTargets.isEmpty()) {
                    Text(
                        text = "暂无记录在案的目标目录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                managedTargets.forEach { entry ->
                    val selected = selectedTargetDir?.normalize() == entry.targetDir.normalize()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedTargetDir = entry.targetDir
                            addMode = false
                        },
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = entry.targetDir.fileName?.toString() ?: entry.targetDir.absolutePathString(),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${entry.fileCount} 个文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

        // Right: details / add-mode / migration
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            when {
                migrationStartedAt != null -> MigrationPane(
                    tasks = migrationTasks,
                    startedAtEpochMillis = migrationStartedAt,
                    finishedAtEpochMillis = migrationFinishedAt,
                    onBack = {
                        if (migrationFinishedAt != null) {
                            migrationTasks.clear()
                            migrationStartedAt = null
                            migrationFinishedAt = null
                        }
                    },
                )

                addMode -> AddModePane(
                    drafts = drafts,
                    onPickSources = {
                        scope.launch {
                            val picked = FfFileDialogs.pickDirectories("选择源目录（可多选）")
                            if (picked.isEmpty()) return@launch
                            addSourceDirsAsync(picked)
                        }
                    },
                    onDropSources = { dirs -> addSourceDirsAsync(dirs) },
                    onRemoveDraft = { id -> drafts.removeAll { it.id == id } },
                    onUpdateDraft = ::updateDraft,
                    onRunAll = ::startMigration,
                )

                selectedTargetDir != null -> ManagedTargetPane(
                    entry = managedTargets.firstOrNull { it.targetDir.normalize() == selectedTargetDir?.normalize() },
                    onOpenTarget = onOpen@{
                        val target = selectedTargetDir ?: return@onOpen
                        runCatching { Desktop.getDesktop().open(target.toFile()) }
                            .onFailure { globalMessage = it.message ?: "无法打开目录" }
                    },
                    onOpenSources = onOpenSources@{
                        val entry = managedTargets.firstOrNull { it.targetDir.normalize() == selectedTargetDir?.normalize() } ?: return@onOpenSources
                        val sources = entry.sources
                            .asSequence()
                            .mapNotNull { runCatching { Path.of(it) }.getOrNull() }
                            .distinctBy { it.toAbsolutePath().normalize().absolutePathString() }
                            .toList()
                        if (sources.isEmpty()) {
                            globalMessage = "未找到可打开的源目录。"
                            return@onOpenSources
                        }
                        sources.forEach { src ->
                            runCatching { Desktop.getDesktop().open(src.toFile()) }
                                .onFailure { globalMessage = it.message ?: "无法打开源目录：${src.fileName}" }
                        }
                    },
                    onRemove = onRemove@{
                        val target = selectedTargetDir ?: return@onRemove
                        scope.launch(Dispatchers.IO) {
                            val current = registryStoreAdapter.loadTargets().filterNot { it.normalize() == target.normalize() }
                            registryStoreAdapter.saveTargets(current)
                            withContext(Dispatchers.Main) {
                                selectedTargetDir = null
                                reloadTargetsAsync()
                            }
                        }
                    },
                    onUpdate = onUpdate@{
                        val target = selectedTargetDir ?: return@onUpdate
                        if (isUpdating) return@onUpdate
                        isUpdating = true
                        scope.launch {
                            val result = updateService.update(
                                targetDir = target,
                                linkConcurrency = FfDefaults.defaultLinkConcurrencyPerTask(settings.concurrencyLimit),
                            )
                            withContext(Dispatchers.Main) {
                                isUpdating = false
                                when (result) {
                                    is FfUpdateResult.Success -> {
                                        reloadTargetsAsync()
                                        globalMessage = "已更新：${target.fileName}"
                                    }
                                    is FfUpdateResult.Failed -> {
                                        globalMessage = result.message
                                    }
                                    is FfUpdateResult.TrashFailed -> {
                                        pendingTempCleanup = result.tempDir
                                        globalMessage = result.message
                                        reloadTargetsAsync()
                                    }
                                }
                            }
                        }
                    },
                    isUpdating = isUpdating,
                )

                else -> {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "选择左侧目标目录查看详情，或点击“新增”创建迁移任务。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (globalMessage != null) {
                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            modifier = Modifier.padding(10.dp),
                            text = globalMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagedTargetPane(
    entry: FfManagedTargetEntry?,
    onOpenTarget: () -> Unit,
    onOpenSources: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: () -> Unit,
    isUpdating: Boolean = false,
) {
    if (entry == null) {
        Text(text = "目标目录信息不可用（可能已被移动或 .ff 缺失）", color = MaterialTheme.colorScheme.error)
        return
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(text = "目标目录", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = entry.targetDir.absolutePathString(), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "文件数：${entry.fileCount}", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "源目录", style = MaterialTheme.typography.titleMedium)
        entry.sources.forEach { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "扩展名", style = MaterialTheme.typography.titleMedium)
        Text(
            text = entry.extensions.joinToString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FfOutlinedButton(text = "打开源目录", onClick = onOpenSources, icon = painterResource(Res.drawable.ic_folder_open))
            FfPrimaryButton(text = "打开目标目录", onClick = onOpenTarget, icon = painterResource(Res.drawable.ic_folder_open))
            FfOutlinedButton(text = "移除", onClick = onRemove, enabled = !isUpdating)
            FfOutlinedButton(
                text = if (isUpdating) "更新中…" else "更新",
                onClick = onUpdate,
                icon = painterResource(Res.drawable.ic_refresh),
                enabled = !isUpdating,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AddModePane(
    drafts: List<FfDraftTask>,
    onPickSources: () -> Unit,
    onDropSources: (List<Path>) -> Unit,
    onRemoveDraft: (String) -> Unit,
    onUpdateDraft: (String, (FfDraftTask) -> FfDraftTask) -> Unit,
    onRunAll: () -> Unit,
) {
    val onDropSourcesState by rememberUpdatedState(onDropSources)
    var showDropHighlight by remember { mutableStateOf(false) }
    var dropCount by remember { mutableStateOf(0) }
    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                showDropHighlight = true
                // 尝试预估拖拽数量
                val dirs = runCatching { extractDroppedDirectories(event.awtTransferable) }.getOrElse { emptyList() }
                dropCount = dirs.size.coerceAtLeast(1)
            }

            override fun onEnded(event: DragAndDropEvent) {
                showDropHighlight = false
                dropCount = 0
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val directories = extractDroppedDirectories(event.awtTransferable)
                if (directories.isNotEmpty()) onDropSourcesState(directories)
                return directories.isNotEmpty()
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> shouldAcceptExternalDrop(event) },
                target = dragAndDropTarget,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "新增任务", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            FfOutlinedButton(text = "添加源目录", onClick = onPickSources, icon = painterResource(Res.drawable.ic_folder_open))
            Spacer(modifier = Modifier.width(8.dp))
            FfPrimaryButton(text = "开始迁移", onClick = onRunAll)
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        // 拖拽预览区域 - 虚线边框
        AnimatedVisibility(
            visible = showDropHighlight,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .drawBehind {
                        val stroke = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                        drawRoundRect(
                            color = primaryColor,
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = stroke
                        )
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(primaryColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$dropCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                )
            }
        }

        AnimatedVisibility(
            visible = showDropHighlight,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Spacer(modifier = Modifier.height(10.dp))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.padding(12.dp),
                text = "你可以把目录直接拖拽到右侧区域（新增模式）来快速添加源目录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (drafts.isEmpty()) {
            Text(
                text = "请先添加一个或多个源目录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            drafts.forEach { draft ->
                DraftCard(
                    draft = draft,
                    onRemove = { onRemoveDraft(draft.id) },
                    onUpdate = { transform -> onUpdateDraft(draft.id, transform) },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun DraftCard(
    draft: FfDraftTask,
    onRemove: () -> Unit,
    onUpdate: ((FfDraftTask) -> FfDraftTask) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.sourceDir.fileName?.toString() ?: "源目录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = draft.sourceDir.absolutePathString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FfOutlinedButton(text = "移除", onClick = onRemove)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.targetPathText,
                onValueChange = { raw ->
                    val parsed = parsePathOrNull(raw)
                    val validation = parsed?.let { FfTargetValidator.validate(it) }
                    onUpdate {
                        it.copy(
                            targetPathText = raw,
                            targetDir = parsed,
                            targetValidation = validation,
                        )
                    }
                },
                label = { Text("目标目录（可输入不存在路径）") },
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FfOutlinedButton(text = "选择目标", onClick = {
                    scope.launch {
                        val picked = FfFileDialogs.pickDirectory("选择目标目录")
                        if (picked != null) {
                            val validation = withContext(Dispatchers.IO) { FfTargetValidator.validate(picked) }
                            onUpdate { it.copy(targetPathText = picked.absolutePathString(), targetDir = picked, targetValidation = validation) }
                        }
                    }
                })
                TargetValidationHint(draft.targetValidation)
            }

            when (val state = draft.scanState) {
                FfScanState.Idle -> Text(text = "等待扫描…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FfScanState.Scanning -> Text(text = "扫描中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is FfScanState.Failed -> Text(text = "扫描失败：${state.message}", color = MaterialTheme.colorScheme.error)
                is FfScanState.Done -> ExtensionsSelector(
                    stats = state.extensionStats,
                    selected = draft.selectedExtensions,
                    onSelectAll = { onUpdate { it.copy(selectedExtensions = state.extensionStats.keys.toSet()) } },
                    onClear = { onUpdate { it.copy(selectedExtensions = emptySet()) } },
                    onToggle = { ext ->
                        onUpdate {
                            val next = it.selectedExtensions.toMutableSet()
                            if (!next.add(ext)) next.remove(ext)
                            it.copy(selectedExtensions = next)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtensionsSelector(
    stats: Map<String, Int>,
    selected: Set<String>,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "扩展名选择", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.weight(1f))
            FfTertiaryButton(text = "全选", onClick = onSelectAll)
            FfTertiaryButton(text = "全取消", onClick = onClear)
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stats.forEach { (ext, count) ->
                FilterChip(
                    selected = ext in selected,
                    onClick = { onToggle(ext) },
                    label = { Text(text = extLabel(ext, count)) },
                )
            }
        }
    }
}

@Composable
private fun TargetValidationHint(validation: FfTargetValidation?) {
    val text = when (validation) {
        null -> "未选择"
        FfTargetValidation.OkManaged -> "受管目录"
        FfTargetValidation.OkEmpty -> "空目录"
        FfTargetValidation.OkNew -> "不存在（将创建）"
        FfTargetValidation.NotAllowedNotEmpty -> "不为空（禁止）"
        FfTargetValidation.NotAllowedNotDirectory -> "非目录（禁止）"
    }
    val color = when (validation) {
        FfTargetValidation.NotAllowedNotEmpty,
        FfTargetValidation.NotAllowedNotDirectory -> MaterialTheme.colorScheme.error

        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

private fun FfDraftTask.canRun(): Boolean {
    if (targetDir == null) return false
    if (targetValidation == FfTargetValidation.NotAllowedNotEmpty || targetValidation == FfTargetValidation.NotAllowedNotDirectory) return false
    if (scanState !is FfScanState.Done) return false
    if (selectedExtensions.isEmpty()) return false
    return true
}

private fun expectedSelectedCount(draft: FfDraftTask): Int {
    val done = draft.scanState as? FfScanState.Done ?: return 0
    return done.extensionStats
        .asSequence()
        .filter { (ext, _) -> ext in draft.selectedExtensions }
        .sumOf { it.value }
}

private fun extLabel(ext: String, count: Int): String {
    val label = if (ext == FfConstants.ExtensionNone) "无扩展名" else ".$ext"
    return "$label  ($count)"
}

private fun newDraft(sourceDir: Path): FfDraftTask {
    // 默认填充目标目录为源目录下的 flatten 子目录
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

private fun parsePathOrNull(raw: String): Path? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return try {
        Path.of(trimmed)
    } catch (_: InvalidPathException) {
        null
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun shouldAcceptExternalDrop(event: DragAndDropEvent): Boolean {
    val transferable = runCatching { event.awtTransferable }.getOrNull() ?: return false
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return true
    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return true
    return DataFlavor.selectBestTextFlavor(transferable.transferDataFlavors) != null
}

private fun extractDroppedDirectories(transferable: Transferable): List<Path> {
    val byFileList = runCatching {
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return@runCatching emptyList()
        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
        files
            .orEmpty()
            .filterIsInstance<File>()
            .asSequence()
            .filter { it.isDirectory }
            .map { it.toPath() }
            .toList()
    }.getOrElse { emptyList() }
    if (byFileList.isNotEmpty()) return byFileList

    val byString = runCatching {
        val text = extractDroppedTextOrNull(transferable).orEmpty()
        if (text.isBlank()) return@runCatching emptyList()
        parseUriList(text)
    }.getOrElse { emptyList() }
    return byString
}

private fun extractDroppedTextOrNull(transferable: Transferable): String? {
    return runCatching {
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return@runCatching transferable.getTransferData(DataFlavor.stringFlavor) as? String
        }

        val best = DataFlavor.selectBestTextFlavor(transferable.transferDataFlavors) ?: return@runCatching null
        best.getReaderForText(transferable).readText()
    }.getOrNull()
}

private fun parseUriList(text: String): List<Path> {
    return text
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line -> runCatching { URI(line) }.getOrNull() }
        .filter { it.scheme.equals("file", ignoreCase = true) }
        .mapNotNull { uri -> runCatching { Path.of(uri) }.getOrNull() }
        .filter { it.toFile().isDirectory }
        .distinctBy { it.toAbsolutePath().normalize().absolutePathString() }
        .toList()
}

private enum class FfMigrationTaskStatus { Pending, Running, Finished, Failed }

private data class FfMigrationTaskUi(
    val id: String,
    val targetDir: Path,
    val expectedTotalFiles: Int,
    val sources: List<Path>,
    val status: FfMigrationTaskStatus = FfMigrationTaskStatus.Pending,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
    val progress: love.forte.tools.ff.fs.FfFlattenProgress? = null,
    val report: love.forte.tools.ff.fs.FfFlattenReport? = null,
    val errorMessage: String? = null,
)

@Composable
private fun MigrationPane(
    tasks: List<FfMigrationTaskUi>,
    startedAtEpochMillis: Long?,
    finishedAtEpochMillis: Long?,
    onBack: () -> Unit,
) {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(finishedAtEpochMillis) {
        if (finishedAtEpochMillis != null) return@LaunchedEffect
        while (true) {
            now.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }

    val total = tasks.size
    val finished = tasks.count { it.status == FfMigrationTaskStatus.Finished }
    val failed = tasks.count { it.status == FfMigrationTaskStatus.Failed }
    val running = tasks.count { it.status == FfMigrationTaskStatus.Running }
    val allDone = finishedAtEpochMillis != null

    val end = finishedAtEpochMillis ?: now.value
    val elapsedMs = (startedAtEpochMillis?.let { end - it } ?: 0L).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = if (allDone) "迁移完成" else "迁移进行中", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            if (allDone) {
                FfOutlinedButton(text = "返回", onClick = onBack)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "任务：$total | 进行中：$running | 完成：$finished | 失败：$failed | 总耗时：${formatDuration(elapsedMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            tasks.forEach { task ->
                MigrationTaskCard(task = task)
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun MigrationTaskCard(task: FfMigrationTaskUi) {
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(task.status) {
        if (task.status != FfMigrationTaskStatus.Running) return@LaunchedEffect
        while (true) {
            now.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }

    val doneCount = task.report?.completedFiles ?: task.progress?.completedFiles ?: 0
    val createdLinks = task.report?.createdLinks ?: task.progress?.createdLinks ?: 0
    val skippedExisting = task.report?.skippedExisting ?: task.progress?.skippedExisting ?: 0
    val failedLinks = task.report?.failedLinks ?: task.progress?.failedLinks ?: 0
    val expected = task.expectedTotalFiles
    val ratio = if (expected <= 0) 0f else (doneCount.toFloat() / expected.toFloat())
    val progress = when (task.status) {
        FfMigrationTaskStatus.Running -> ratio.coerceAtMost(0.99f)
        FfMigrationTaskStatus.Pending -> 0f
        FfMigrationTaskStatus.Finished,
        FfMigrationTaskStatus.Failed -> 1f
    }.coerceIn(0f, 1f)

    val startedAt = task.startedAtEpochMillis ?: task.report?.startedAtEpochMillis
    val finishedAt = task.finishedAtEpochMillis ?: task.report?.finishedAtEpochMillis
    val end = finishedAt ?: now.value
    val elapsedMs = (startedAt?.let { end - it } ?: 0L).coerceAtLeast(0)

    val statusText = when (task.status) {
        FfMigrationTaskStatus.Pending -> "等待中"
        FfMigrationTaskStatus.Running -> "进行中"
        FfMigrationTaskStatus.Finished -> "已完成"
        FfMigrationTaskStatus.Failed -> "失败"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.targetDir.fileName?.toString() ?: "目标目录",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == FfMigrationTaskStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = task.targetDir.absolutePathString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

            Text(
                text = "进度：$doneCount / $expected | 创建：$createdLinks | 跳过：$skippedExisting | 失败：$failedLinks | 耗时：${formatDuration(elapsedMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val current = task.progress?.currentFile
            if (!current.isNullOrBlank() && task.status == FfMigrationTaskStatus.Running) {
                Text(
                    text = "当前：$current",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!task.errorMessage.isNullOrBlank()) {
                Text(text = "错误：${task.errorMessage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m${seconds}s" else "${seconds}s"
}
