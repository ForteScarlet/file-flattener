package love.forte.tools.ff.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import love.forte.tools.ff.ui.FfSharedKeys
import love.forte.tools.ff.ui.ffSharedNavModifier
import love.forte.tools.ff.ui.components.FfOutlinedButton
import love.forte.tools.ff.ui.components.FfPrimaryButton
import love.forte.tools.ff.ui.components.FfTextButton

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FfHomeScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenWorkspace: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenAbout: () -> Unit,
    onExit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 360.dp)
                .padding(horizontal = 24.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "File Flattener",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "将目录中的文件按类型筛选并硬链接平铺到目标目录",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            with(sharedTransitionScope) {
                FfPrimaryButton(
                    text = "工作区",
                    onClick = onOpenWorkspace,
                    modifier = ffSharedNavModifier(FfSharedKeys.NavWorkspace, animatedVisibilityScope).fillMaxWidth(),
                )
                FfOutlinedButton(
                    text = "配置",
                    onClick = onOpenConfig,
                    modifier = ffSharedNavModifier(FfSharedKeys.NavConfig, animatedVisibilityScope).fillMaxWidth(),
                )
                FfOutlinedButton(
                    text = "关于",
                    onClick = onOpenAbout,
                    modifier = ffSharedNavModifier(FfSharedKeys.NavAbout, animatedVisibilityScope).fillMaxWidth(),
                )
            }

            HorizontalDivider(thickness = 0.6.dp, modifier = Modifier.padding(vertical = 4.dp))
            FfTextButton(text = "退出", onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
    }
}
