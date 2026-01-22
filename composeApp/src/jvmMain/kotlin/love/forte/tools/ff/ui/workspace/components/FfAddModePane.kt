package love.forte.tools.ff.ui.workspace.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import love.forte.tools.ff.ui.components.FfOutlinedButton
import love.forte.tools.ff.ui.components.FfPrimaryButton
import love.forte.tools.ff.ui.workspace.FfDraftTask
import love.forte.tools.file_flattener.composeapp.generated.resources.Res
import love.forte.tools.file_flattener.composeapp.generated.resources.ic_drop_zone
import love.forte.tools.file_flattener.composeapp.generated.resources.ic_folder_open
import org.jetbrains.compose.resources.painterResource
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * 新增任务面板，支持拖拽添加源目录
 *
 * @param drafts 草稿任务列表
 * @param onPickSources 选择源目录回调
 * @param onDropSources 拖拽源目录回调
 * @param onRemoveDraft 移除草稿回调
 * @param onUpdateDraft 更新草稿回调
 * @param onRunAll 执行所有任务回调
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FfAddModePane(
    drafts: List<FfDraftTask>,
    onPickSources: () -> Unit,
    onDropSources: (List<Path>) -> Unit,
    onRemoveDraft: (String) -> Unit,
    onUpdateDraft: (String, (FfDraftTask) -> FfDraftTask) -> Unit,
    onRunAll: () -> Unit,
) {
    // 使用 rememberUpdatedState 确保回调始终是最新的
    val onDropSourcesState by rememberUpdatedState(onDropSources)
    var showDropHighlight by remember { mutableStateOf(false) }

    // 拖拽目标
    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                showDropHighlight = true
            }

            override fun onEnded(event: DragAndDropEvent) {
                showDropHighlight = false
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
        // 标题栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "新增任务", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            FfOutlinedButton(
                text = "添加源目录",
                onClick = onPickSources,
                icon = painterResource(Res.drawable.ic_folder_open)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FfPrimaryButton(text = "开始迁移", onClick = onRunAll)
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        // 拖拽提示
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.padding(12.dp),
                text = "你可以把目录直接拖拽到右侧区域（新增模式）来快速添加源目录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 拖拽高亮区域
        AnimatedVisibility(
            visible = showDropHighlight,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(primaryColor.copy(alpha = 0.06f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_drop_zone),
                    contentDescription = "拖放区域",
                    modifier = Modifier.height(48.dp),
                    colorFilter = ColorFilter.tint(primaryColor),
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

        Spacer(modifier = Modifier.height(10.dp))

        // 空状态提示
        if (drafts.isEmpty()) {
            Text(
                text = "请先添加一个或多个源目录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        // 草稿卡片列表
        Box(modifier = Modifier.fillMaxSize()) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                drafts.forEach { draft ->
                    FfDraftCard(
                        draft = draft,
                        onRemove = { onRemoveDraft(draft.id) },
                        onUpdate = { transform -> onUpdateDraft(draft.id, transform) },
                    )
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

// region 拖拽工具函数

/** 判断是否应该接受外部拖拽 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun shouldAcceptExternalDrop(event: DragAndDropEvent): Boolean {
    val transferable = runCatching { event.awtTransferable }.getOrNull() ?: return false
    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return true
    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return true
    return DataFlavor.selectBestTextFlavor(transferable.transferDataFlavors) != null
}

/** 从拖拽数据中提取目录列表 */
internal fun extractDroppedDirectories(transferable: Transferable): List<Path> {
    // 尝试从文件列表提取
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

    // 尝试从文本 URI 提取
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

// endregion
