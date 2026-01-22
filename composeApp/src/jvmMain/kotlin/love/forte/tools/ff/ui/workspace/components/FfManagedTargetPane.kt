package love.forte.tools.ff.ui.workspace.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import love.forte.tools.ff.ui.components.FfOutlinedButton
import love.forte.tools.ff.ui.components.FfPrimaryButton
import love.forte.tools.ff.ui.workspace.FfManagedTargetEntry
import love.forte.tools.file_flattener.composeapp.generated.resources.Res
import love.forte.tools.file_flattener.composeapp.generated.resources.ic_folder_open
import love.forte.tools.file_flattener.composeapp.generated.resources.ic_refresh
import org.jetbrains.compose.resources.painterResource
import kotlin.io.path.absolutePathString

/**
 * 受管目标目录详情面板
 *
 * @param entry 受管目标条目（null 表示数据不可用）
 * @param isUpdating 是否正在更新
 * @param onOpenTarget 打开目标目录回调
 * @param onOpenSources 打开源目录回调
 * @param onRemove 移除目标回调
 * @param onUpdate 更新目标回调
 */
@Composable
fun FfManagedTargetPane(
    entry: FfManagedTargetEntry?,
    isUpdating: Boolean,
    onOpenTarget: () -> Unit,
    onOpenSources: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: () -> Unit,
) {
    if (entry == null) {
        Text(
            text = "目标目录信息不可用（可能已被移动或 .ff 缺失）",
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            // 目标目录信息
            Text(text = "目标目录", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.targetDir.absolutePathString(),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "文件数：${entry.fileCount}",
                style = MaterialTheme.typography.bodyMedium
            )

            // 源目录列表
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "源目录", style = MaterialTheme.typography.titleMedium)
            entry.sources.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 扩展名列表
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "扩展名", style = MaterialTheme.typography.titleMedium)
            Text(
                text = entry.extensions.joinToString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 操作按钮
            Spacer(modifier = Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FfOutlinedButton(
                    text = "打开源目录",
                    onClick = onOpenSources,
                    icon = painterResource(Res.drawable.ic_folder_open)
                )
                FfPrimaryButton(
                    text = "打开目标目录",
                    onClick = onOpenTarget,
                    icon = painterResource(Res.drawable.ic_folder_open)
                )
                FfOutlinedButton(
                    text = "移除",
                    onClick = onRemove,
                    enabled = !isUpdating
                )
                FfOutlinedButton(
                    text = if (isUpdating) "更新中…" else "更新",
                    onClick = onUpdate,
                    icon = painterResource(Res.drawable.ic_refresh),
                    enabled = !isUpdating,
                )
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState),
        )
    }
}
