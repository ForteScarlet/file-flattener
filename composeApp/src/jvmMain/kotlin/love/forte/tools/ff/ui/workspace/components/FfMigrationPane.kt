package love.forte.tools.ff.ui.workspace.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import love.forte.tools.ff.ui.components.FfOutlinedButton
import love.forte.tools.ff.ui.workspace.FfMigrationTaskStatus
import love.forte.tools.ff.ui.workspace.FfMigrationTaskUi
import kotlin.io.path.absolutePathString

/** 迁移成功时进度条的颜色（绿色） */
private val ProgressColorSuccess = Color(0xFF4CAF50)

/**
 * 迁移执行面板，展示所有迁移任务的进度和状态
 *
 * @param tasks 迁移任务列表
 * @param startedAtEpochMillis 迁移开始时间戳
 * @param finishedAtEpochMillis 迁移完成时间戳（null 表示进行中）
 * @param onBack 返回回调（仅在完成后可用）
 */
@Composable
fun FfMigrationPane(
    tasks: List<FfMigrationTaskUi>,
    startedAtEpochMillis: Long?,
    finishedAtEpochMillis: Long?,
    onBack: () -> Unit,
) {
    // 实时更新当前时间用于计算耗时
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(finishedAtEpochMillis) {
        if (finishedAtEpochMillis != null) return@LaunchedEffect
        while (true) {
            now.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }

    // 统计数据
    val total = tasks.size
    val finished = tasks.count { it.status == FfMigrationTaskStatus.Finished }
    val failed = tasks.count { it.status == FfMigrationTaskStatus.Failed }
    val running = tasks.count { it.status == FfMigrationTaskStatus.Running }
    val allDone = finishedAtEpochMillis != null

    val end = finishedAtEpochMillis ?: now.value
    val elapsedMs = (startedAtEpochMillis?.let { end - it } ?: 0L).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (allDone) "迁移完成" else "迁移进行中",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            if (allDone) {
                FfOutlinedButton(text = "返回", onClick = onBack)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 统计摘要
        Text(
            text = "任务：$total | 进行中：$running | 完成：$finished | 失败：$failed | 总耗时：${formatDuration(elapsedMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        // 任务卡片列表
        Box(modifier = Modifier.fillMaxSize()) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tasks.forEach { task ->
                    FfMigrationTaskCard(task = task)
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState),
            )
        }
    }
}

/**
 * 单个迁移任务卡片
 *
 * 展示单个迁移任务的详细信息，包括：
 * - 目标目录名称和完整路径
 * - 进度条（带颜色过渡动画：完成时由红转绿）
 * - 详细统计信息（进度、创建、跳过、失败数量）
 * - 当前处理文件（仅进行中时显示）
 * - 错误信息（如有）
 */
@Composable
private fun FfMigrationTaskCard(task: FfMigrationTaskUi) {
    // 进行中时实时更新时间
    val now = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(task.status) {
        if (task.status != FfMigrationTaskStatus.Running) return@LaunchedEffect
        while (true) {
            now.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }

    // 进度数据（优先取 report，其次取 progress）
    val doneCount = task.report?.completedFiles ?: task.progress?.completedFiles ?: 0
    val createdLinks = task.report?.createdLinks ?: task.progress?.createdLinks ?: 0
    val skippedExisting = task.report?.skippedExisting ?: task.progress?.skippedExisting ?: 0
    val failedLinks = task.report?.failedLinks ?: task.progress?.failedLinks ?: 0
    val expected = task.expectedTotalFiles

    // 进度比例
    val ratio = if (expected <= 0) 0f else (doneCount.toFloat() / expected.toFloat())
    val progress = when (task.status) {
        FfMigrationTaskStatus.Running -> ratio.coerceAtMost(0.99f)
        FfMigrationTaskStatus.Pending -> 0f
        FfMigrationTaskStatus.Finished,
        FfMigrationTaskStatus.Failed -> 1f
    }.coerceIn(0f, 1f)

    // 耗时计算
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

    // 进度条颜色：根据任务状态决定目标颜色，使用动画实现平滑过渡
    // - 完成：绿色（成功色）
    // - 失败：错误色
    // - 其他（等待/进行中）：主题主色（红色系）
    val progressColor by animateColorAsState(
        targetValue = when (task.status) {
            FfMigrationTaskStatus.Finished -> ProgressColorSuccess
            FfMigrationTaskStatus.Failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "progress-color"
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 标题行
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
                    color = if (task.status == FfMigrationTaskStatus.Failed)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 完整路径
            Text(
                text = task.targetDir.absolutePathString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 进度条：使用动画颜色，完成时由红色平滑过渡为绿色
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.24f)
            )

            // 详细统计
            Text(
                text = "进度：$doneCount / $expected | 创建：$createdLinks | 跳过：$skippedExisting | 失败：$failedLinks | 耗时：${formatDuration(elapsedMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 当前处理文件
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

            // 错误信息
            if (!task.errorMessage.isNullOrBlank()) {
                Text(
                    text = "错误：${task.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** 格式化时长（毫秒 -> 可读字符串） */
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m${seconds}s" else "${seconds}s"
}
