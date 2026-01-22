package love.forte.tools.ff.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import love.forte.tools.ff.FfConstants
import love.forte.tools.ff.FfDefaults
import love.forte.tools.ff.storage.FfAppSettings
import love.forte.tools.ff.storage.FfAppTheme
import love.forte.tools.ff.storage.FfAppPaths
import love.forte.tools.ff.ui.components.FfCenteredContentLayout
import love.forte.tools.ff.ui.components.FfOutlinedButton
import love.forte.tools.ff.ui.components.FfPrimaryButton
import love.forte.tools.ff.ui.components.FfTextButton
import love.forte.tools.ff.ui.platform.FfFileDialogs
import love.forte.tools.file_flattener.composeapp.generated.resources.Res
import love.forte.tools.file_flattener.composeapp.generated.resources.ic_folder_open
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Composable
fun FfConfigScreen(
    appDir: Path,
    settings: FfAppSettings,
    onUpdateSettings: (FfAppSettings) -> Unit,
    onUpdateUserDataDir: (Path) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var userDataDirText by remember(appDir) { mutableStateOf(appDir.absolutePathString()) }
    var userDataDirError by remember { mutableStateOf<String?>(null) }
    val defaultUserDataDir = remember { FfAppPaths.defaultAppDir() }

    LaunchedEffect(appDir) { userDataDirError = null }

    FfCenteredContentLayout {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(text = "配置", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "用户数据目录", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "用于存放 registry、settings、数据库等用户数据。修改后会尽量迁移旧数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = userDataDirText,
                onValueChange = {
                    userDataDirText = it
                    userDataDirError = null
                },
                label = { Text("用户数据目录") },
                singleLine = true,
            )

            if (!userDataDirError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = userDataDirError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FfOutlinedButton(text = "选择目录", onClick = {
                    scope.launch {
                        val picked = FfFileDialogs.pickDirectory("选择用户数据目录")
                        if (picked != null) {
                            userDataDirText = picked.absolutePathString()
                            userDataDirError = null
                        }
                    }
                })
                FfPrimaryButton(text = "应用", onClick = {
                    val raw = userDataDirText.trim()
                    if (raw.isEmpty()) {
                        userDataDirError = "路径不能为空"
                        return@FfPrimaryButton
                    }
                    val parsed = runCatching { Path.of(raw) }
                        .getOrElse { e ->
                            userDataDirError = when (e) {
                                is InvalidPathException -> "路径不合法"
                                else -> e.message ?: "路径不合法"
                            }
                            return@FfPrimaryButton
                        }
                    userDataDirError = null
                    onUpdateUserDataDir(parsed)
                })
                FfTextButton(text = "恢复默认", onClick = {
                    userDataDirText = defaultUserDataDir.absolutePathString()
                    userDataDirError = null
                    onUpdateUserDataDir(defaultUserDataDir)
                })
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    runCatching { Desktop.getDesktop().open(appDir.toFile()) }
                        .onFailure { userDataDirError = it.message ?: "无法打开目录" }
                }) {
                    Image(painter = painterResource(Res.drawable.ic_folder_open), contentDescription = "open-user-data-dir")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "主题配色", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                FfTextButton(text = "恢复默认", onClick = {
                    onUpdateSettings(settings.copy(theme = FfAppTheme.CherryRed))
                })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOption(
                    text = "樱桃红",
                    selected = settings.theme == FfAppTheme.CherryRed,
                    modifier = Modifier.weight(1f),
                ) {
                    onUpdateSettings(settings.copy(theme = FfAppTheme.CherryRed))
                }
                ThemeOption(
                    text = "典雅黑",
                    selected = settings.theme == FfAppTheme.ClassicBlack,
                    modifier = Modifier.weight(1f),
                ) {
                    onUpdateSettings(settings.copy(theme = FfAppTheme.ClassicBlack))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "并发上限", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                FfTextButton(text = "恢复默认", onClick = {
                    onUpdateSettings(settings.copy(concurrencyLimit = FfDefaults.defaultConcurrencyLimit()))
                })
            }
            Text(
                text = "任务并发上限（以目标目录为单位）。默认使用 CPU 数量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FfOutlinedButton(text = " - ", onClick = {
                    val next = (settings.concurrencyLimit - 1).coerceIn(1, FfConstants.MAX_LINK_CONCURRENCY)
                    onUpdateSettings(settings.copy(concurrencyLimit = next))
                })
                Text(text = settings.concurrencyLimit.toString(), style = MaterialTheme.typography.titleLarge)
                FfOutlinedButton(text = " + ", onClick = {
                    val next = (settings.concurrencyLimit + 1).coerceIn(1, FfConstants.MAX_LINK_CONCURRENCY)
                    onUpdateSettings(settings.copy(concurrencyLimit = next))
                })
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "设置会自动保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeOption(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        FfPrimaryButton(text = text, onClick = onClick, modifier = modifier)
    } else {
        FfOutlinedButton(text = text, onClick = onClick, modifier = modifier)
    }
}
