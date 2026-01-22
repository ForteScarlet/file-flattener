package love.forte.tools.ff.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import love.forte.tools.ff.storage.FfAppSettings
import love.forte.tools.ff.storage.FfRegistryStoreAdapter
import love.forte.tools.ff.ui.components.FfOutlinedButton
import love.forte.tools.ff.ui.platform.FfFileDialogs
import love.forte.tools.ff.ui.workspace.FfWorkspaceState
import love.forte.tools.ff.ui.workspace.FfWorkspaceViewMode
import love.forte.tools.ff.ui.workspace.components.FfAddModePane
import love.forte.tools.ff.ui.workspace.components.FfManagedTargetPane
import love.forte.tools.ff.ui.workspace.components.FfMigrationPane
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * 工作区主界面
 *
 * 采用单向数据流架构：
 * - 状态通过 FfWorkspaceState 统一管理并向下传递
 * - 用户事件通过回调向上传播
 *
 * @param appDir 应用数据目录
 * @param registryStoreAdapter 目标目录注册表适配器
 * @param settings 应用设置
 */
@Composable
fun FfWorkspaceScreen(
    appDir: Path,
    registryStoreAdapter: FfRegistryStoreAdapter,
    settings: FfAppSettings,
) {
    val scope = rememberCoroutineScope()

    // 创建并记住状态持有者
    val state = remember(registryStoreAdapter, settings) {
        FfWorkspaceState(scope, registryStoreAdapter, settings)
    }

    // 初始化时加载数据
    LaunchedEffect(Unit) {
        state.reloadTargets()
    }

    // 主布局：左侧列表 + 右侧详情
    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧：受管目标列表
        FfTargetListPane(
            managedTargets = state.managedTargets,
            selectedTargetDir = state.selectedTargetDir,
            isAddMode = state.viewMode == FfWorkspaceViewMode.AddMode,
            onSelectTarget = { state.selectTarget(it) },
            onEnterAddMode = { state.enterAddMode() },
            onExitAddMode = { state.exitAddMode() },
        )

        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

        // 右侧：详情面板
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            FfDetailPane(
                state = state,
                onPickSources = {
                    scope.launch {
                        val picked = FfFileDialogs.pickDirectories("选择源目录（可多选）")
                        if (picked.isNotEmpty()) {
                            state.addSourceDirs(picked)
                        }
                    }
                },
            )

            // 全局消息提示
            state.globalMessage?.let { message ->
                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            modifier = Modifier.padding(10.dp),
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左侧目标列表面板
 */
@Composable
private fun FfTargetListPane(
    managedTargets: List<love.forte.tools.ff.ui.workspace.FfManagedTargetEntry>,
    selectedTargetDir: Path?,
    isAddMode: Boolean,
    onSelectTarget: (Path) -> Unit,
    onEnterAddMode: () -> Unit,
    onExitAddMode: () -> Unit,
) {
    Column(modifier = Modifier.width(300.dp).fillMaxHeight().padding(12.dp)) {
        // 标题栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "工作区", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            if (isAddMode) {
                FfOutlinedButton(text = "取消", onClick = onExitAddMode)
            } else {
                FfOutlinedButton(text = "新增", onClick = onEnterAddMode)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        // 目标列表
        Box(modifier = Modifier.fillMaxSize()) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (managedTargets.isEmpty()) {
                    Text(
                        text = "暂无记录在案的目标目录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                managedTargets.forEach { entry ->
                    val selected = selectedTargetDir?.normalize() == entry.targetDir.normalize()
                    FfTargetListItem(
                        entry = entry,
                        selected = selected,
                        onClick = { onSelectTarget(entry.targetDir) },
                    )
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState),
            )
        }
    }
}

/**
 * 目标列表项
 */
@Composable
private fun FfTargetListItem(
    entry: love.forte.tools.ff.ui.workspace.FfManagedTargetEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
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

/**
 * 右侧详情面板，根据当前视图模式显示不同内容
 */
@Composable
private fun FfDetailPane(
    state: FfWorkspaceState,
    onPickSources: () -> Unit,
) {
    when (state.viewMode) {
        // 迁移执行中
        FfWorkspaceViewMode.Migrating -> {
            FfMigrationPane(
                tasks = state.migrationTasks,
                startedAtEpochMillis = state.migrationStartedAt,
                finishedAtEpochMillis = state.migrationFinishedAt,
                onBack = { state.exitMigration() },
            )
        }

        // 新增模式
        FfWorkspaceViewMode.AddMode -> {
            FfAddModePane(
                drafts = state.drafts,
                onPickSources = onPickSources,
                onDropSources = { dirs -> state.addSourceDirs(dirs) },
                onRemoveDraft = { id -> state.removeDraft(id) },
                onUpdateDraft = { id, transform -> state.updateDraft(id, transform) },
                onRunAll = { state.startMigration() },
            )
        }

        // 已选中目标
        FfWorkspaceViewMode.TargetSelected -> {
            FfManagedTargetPane(
                entry = state.selectedEntry,
                isUpdating = state.isUpdating,
                onOpenTarget = { state.openTargetDir() },
                onOpenSources = { state.openSourceDirs() },
                onRemove = { state.removeTarget() },
                onUpdate = { state.updateTarget() },
            )
        }

        // 空闲状态
        FfWorkspaceViewMode.Idle -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "选择左侧目标目录查看详情，或点击「新增」创建迁移任务。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
