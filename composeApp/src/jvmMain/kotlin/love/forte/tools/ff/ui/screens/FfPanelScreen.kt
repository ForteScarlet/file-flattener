package love.forte.tools.ff.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn


import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.Image
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import love.forte.tools.ff.storage.FfAppSettings
import love.forte.tools.ff.storage.FfRegistryStore
import love.forte.tools.ff.ui.FfPanelTab
import love.forte.tools.ff.ui.FfSharedKeys
import love.forte.tools.ff.ui.ffSharedNavModifier
import love.forte.tools.ff.ui.components.FfOutlinedButton
import love.forte.tools.ff.ui.components.FfPrimaryButton
import love.forte.tools.ff.FfBuildConfig
import java.nio.file.Path

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FfPanelScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    initialTab: FfPanelTab,
    appDir: Path,
    registryStore: FfRegistryStore,
    settings: FfAppSettings,
    onUpdateSettings: (FfAppSettings) -> Unit,
    onUpdateUserDataDir: (Path) -> Unit,
    onBackToHome: () -> Unit,
) {
    var tab by remember { mutableStateOf(initialTab) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackToHome) {
                Image(painter = painterResource("drawable/ic_arrow_back.svg"), contentDescription = "back")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabButton(
                    text = "工作区",
                    selected = tab == FfPanelTab.Workspace,
                    modifier = with(sharedTransitionScope) { ffSharedNavModifier(FfSharedKeys.NavWorkspace, animatedVisibilityScope) }
                        .widthIn(min = 110.dp),
                ) { tab = FfPanelTab.Workspace }
                Spacer(modifier = Modifier.width(8.dp))
                TabButton(
                    text = "配置",
                    selected = tab == FfPanelTab.Config,
                    modifier = with(sharedTransitionScope) { ffSharedNavModifier(FfSharedKeys.NavConfig, animatedVisibilityScope) }
                        .widthIn(min = 110.dp),
                ) { tab = FfPanelTab.Config }
                Spacer(modifier = Modifier.width(8.dp))
                TabButton(
                    text = "关于",
                    selected = tab == FfPanelTab.About,
                    modifier = with(sharedTransitionScope) { ffSharedNavModifier(FfSharedKeys.NavAbout, animatedVisibilityScope) }
                        .widthIn(min = 110.dp),
                ) { tab = FfPanelTab.About }
                Spacer(modifier = Modifier.width(8.dp))
                TabButton(
                    text = "日志",
                    selected = tab == FfPanelTab.Logs,
                    modifier = Modifier.widthIn(min = 110.dp),
                ) { tab = FfPanelTab.Logs }
            }
        }

        HorizontalDivider()

        AnimatedContent(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            targetState = tab,
            transitionSpec = { tabTransition() },
            label = "panel-tab",
        ) { current ->
            when (current) {
                FfPanelTab.Workspace -> FfWorkspaceScreen(appDir = appDir, registryStore = registryStore, settings = settings)
                FfPanelTab.Config -> FfConfigScreen(
                    appDir = appDir,
                    settings = settings,
                    onUpdateSettings = onUpdateSettings,
                    onUpdateUserDataDir = onUpdateUserDataDir,
                )
                FfPanelTab.About -> FfAboutScreen()
                FfPanelTab.Logs -> FfLogsScreen()
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "file-flattener v${FfBuildConfig.VERSION}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TabButton(
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

private fun tabTransition(): ContentTransform = fadeIn() togetherWith fadeOut()
