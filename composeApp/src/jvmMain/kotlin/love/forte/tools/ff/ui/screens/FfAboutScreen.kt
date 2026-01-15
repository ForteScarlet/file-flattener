package love.forte.tools.ff.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import love.forte.tools.ff.FfBuildConfig
import love.forte.tools.ff.ui.components.FfOutlinedButton
import java.awt.Desktop
import java.net.URI

@Composable
fun FfAboutScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
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
        Text(text = "开源", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.weight(1f),
                text = FfBuildConfig.GITHUB_URL,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            FfOutlinedButton(text = "打开", onClick = {
                runCatching { Desktop.getDesktop().browse(URI(FfBuildConfig.GITHUB_URL)) }
            })
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "运行时", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        val runtime = Runtime.getRuntime()
        val javaInfo = "${System.getProperty("java.vm.name")} ${System.getProperty("java.runtime.version")}"
        val osInfo = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"
        Text(
            text = "Java：$javaInfo\n系统：$osInfo\nCPU：${runtime.availableProcessors()} 核\n内存：${formatBytes(runtime.totalMemory())} / ${formatBytes(runtime.maxMemory())}（已分配/最大）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "提示：硬链接要求源文件与目标目录位于同一文件系统；若跨磁盘/分区，创建可能失败。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format("%.1f MiB", mib)
}
