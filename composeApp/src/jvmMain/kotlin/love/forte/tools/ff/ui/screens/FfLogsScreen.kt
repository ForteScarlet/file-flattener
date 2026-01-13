package love.forte.tools.ff.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FfLogsScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "日志", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "本期仅保留页面布局；后续可接入操作流、失败原因聚合、导出等能力。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

