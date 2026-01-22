package love.forte.tools.ff.ui.workspace.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import love.forte.tools.ff.FfConstants
import love.forte.tools.ff.fs.FfTargetValidation
import love.forte.tools.ff.fs.FfTargetValidator
import love.forte.tools.ff.ui.components.FfOutlinedButton
import love.forte.tools.ff.ui.components.FfTertiaryButton
import love.forte.tools.ff.ui.platform.FfFileDialogs
import love.forte.tools.ff.ui.workspace.FfDraftTask
import love.forte.tools.ff.ui.workspace.FfScanState
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * 草稿任务卡片，展示单个源目录的配置
 *
 * @param draft 草稿任务数据
 * @param onRemove 移除回调
 * @param onUpdate 更新回调（接收转换函数）
 */
@Composable
fun FfDraftCard(
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
            // 头部：源目录信息 + 操作按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    // 源目录名称
                    Text(
                        text = draft.sourceDir.fileName?.toString() ?: "源目录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // 源目录完整路径
                    Text(
                        text = draft.sourceDir.absolutePathString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 折叠时显示目标目录
                    if (!draft.expanded && draft.targetDir != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "→ ${draft.targetDir.absolutePathString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                FfTertiaryButton(
                    text = if (draft.expanded) "折叠" else "展开",
                    onClick = { onUpdate { it.copy(expanded = !it.expanded) } },
                )
                Spacer(modifier = Modifier.width(4.dp))
                FfOutlinedButton(text = "移除", onClick = onRemove)
            }

            // 展开时显示详细配置
            AnimatedVisibility(
                visible = draft.expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // 目标目录输入
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

                    // 目标目录选择 + 验证提示
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FfOutlinedButton(text = "选择目标", onClick = {
                            scope.launch {
                                val picked = FfFileDialogs.pickDirectory("选择目标目录")
                                if (picked != null) {
                                    val validation = withContext(Dispatchers.IO) {
                                        FfTargetValidator.validate(picked)
                                    }
                                    onUpdate {
                                        it.copy(
                                            targetPathText = picked.absolutePathString(),
                                            targetDir = picked,
                                            targetValidation = validation
                                        )
                                    }
                                }
                            }
                        })
                        FfTargetValidationHint(draft.targetValidation)
                    }

                    // 扫描状态 / 扩展名选择器
                    when (val state = draft.scanState) {
                        FfScanState.Idle -> Text(
                            text = "等待扫描…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FfScanState.Scanning -> Text(
                            text = "扫描中…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        is FfScanState.Failed -> Text(
                            text = "扫描失败：${state.message}",
                            color = MaterialTheme.colorScheme.error
                        )
                        is FfScanState.Done -> FfExtensionsSelector(
                            stats = state.extensionStats,
                            selected = draft.selectedExtensions,
                            onSelectAll = {
                                onUpdate { it.copy(selectedExtensions = state.extensionStats.keys.toSet()) }
                            },
                            onClear = {
                                onUpdate { it.copy(selectedExtensions = emptySet()) }
                            },
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
    }
}

/**
 * 扩展名选择器
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FfExtensionsSelector(
    stats: Map<String, Int>,
    selected: Set<String>,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    label = { Text(text = formatExtLabel(ext, count)) },
                )
            }
        }
    }
}

/**
 * 目标目录验证状态提示
 */
@Composable
fun FfTargetValidationHint(validation: FfTargetValidation?) {
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

/** 解析路径字符串，无效则返回 null */
private fun parsePathOrNull(raw: String): Path? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return try {
        Path.of(trimmed)
    } catch (_: InvalidPathException) {
        null
    }
}

/** 格式化扩展名标签 */
private fun formatExtLabel(ext: String, count: Int): String {
    val label = if (ext == FfConstants.EXTENSION_NONE) "无扩展名" else ".$ext"
    return "$label  ($count)"
}
