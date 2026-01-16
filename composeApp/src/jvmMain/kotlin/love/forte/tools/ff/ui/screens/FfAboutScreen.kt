package love.forte.tools.ff.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import love.forte.tools.ff.FfBuildConfig
import love.forte.tools.ff.ui.components.FfOutlinedButton
import java.awt.Desktop
import java.net.URI

@Composable
fun FfAboutScreen() {
    var showSystemPropertiesWindow by remember { mutableStateOf(false) }
    var showEnvVariablesWindow by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "关于", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "file-flattener：一个用于将目录内文件按类型筛选后，通过硬链接平铺到目标目录的桌面工具。",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "版本", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = FfBuildConfig.VERSION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "开源地址", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            modifier = Modifier.clickable {
                runCatching { Desktop.getDesktop().browse(URI(FfBuildConfig.GITHUB_URL)) }
            },
            text = FfBuildConfig.GITHUB_URL,
            style = MaterialTheme.typography.bodySmall.copy(
                textDecoration = TextDecoration.Underline,
            ),
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "运行时", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        val runtime = Runtime.getRuntime()
        val javaInfo = "${System.getProperty("java.vm.name")} ${System.getProperty("java.runtime.version")}"
        val osInfo =
            "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"
        Text(
            text = "Java：$javaInfo\n系统：$osInfo\nCPU：${runtime.availableProcessors()} 核\n内存：${formatBytes(runtime.totalMemory())} / ${
                formatBytes(
                    runtime.maxMemory()
                )
            }（已分配/最大）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FfOutlinedButton(text = "系统属性", onClick = { showSystemPropertiesWindow = true })
            FfOutlinedButton(text = "环境变量", onClick = { showEnvVariablesWindow = true })
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "提示：硬链接要求源文件与目标目录位于同一文件系统；若跨磁盘/分区，创建可能失败。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showSystemPropertiesWindow) {
        FfSystemPropertiesWindow(onClose = { showSystemPropertiesWindow = false })
    }

    if (showEnvVariablesWindow) {
        FfEnvVariablesWindow(onClose = { showEnvVariablesWindow = false })
    }
}

private fun formatBytes(bytes: Long): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format("%.1f MiB", mib)
}

@Composable
private fun FfSystemPropertiesWindow(onClose: () -> Unit) {
    androidx.compose.ui.window.Window(
        onCloseRequest = onClose,
        title = "系统属性",
        state = androidx.compose.ui.window.rememberWindowState(
            width = 700.dp,
            height = 500.dp,
        ),
    ) {
        val properties = remember {
            System.getProperties().entries
                .map { (k, v) -> k.toString() to v.toString() }
        }

        MaterialTheme {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "系统属性（共 ${properties.size} 项）",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PropertiesTable(items = properties)
            }
        }
    }
}

@Composable
private fun FfEnvVariablesWindow(onClose: () -> Unit) {
    androidx.compose.ui.window.Window(
        onCloseRequest = onClose,
        title = "环境变量",
        state = androidx.compose.ui.window.rememberWindowState(
            width = 700.dp,
            height = 500.dp,
        ),
    ) {
        val envVars = remember {
            System.getenv().entries
                .map { (k, v) -> k to v }
                .sortedBy { it.first }
        }

        MaterialTheme {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "环境变量（共 ${envVars.size} 项）",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PropertiesTable(items = envVars)
            }
        }
    }
}

@Composable
private fun PropertiesTable(items: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (key, value) ->
            SelectionContainer {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        modifier = Modifier.width(200.dp),
                        text = key,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        modifier = Modifier.weight(1f),
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}
