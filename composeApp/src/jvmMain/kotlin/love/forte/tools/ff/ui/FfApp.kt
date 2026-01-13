package love.forte.tools.ff.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import love.forte.tools.ff.storage.FfAppPaths
import love.forte.tools.ff.storage.FfAppSettings
import love.forte.tools.ff.storage.FfBootstrapSettings
import love.forte.tools.ff.storage.FfBootstrapStore
import love.forte.tools.ff.storage.FfRegistryStore
import love.forte.tools.ff.storage.FfSettingsStore
import love.forte.tools.ff.ui.screens.FfHomeScreen
import love.forte.tools.ff.ui.screens.FfPanelScreen
import love.forte.tools.ff.ui.theme.FfTheme
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

enum class FfRootRoute { Home, Panel }

enum class FfPanelTab { Workspace, Config, About, Logs }

data class FfNavState(
    val route: FfRootRoute = FfRootRoute.Home,
    val panelTab: FfPanelTab = FfPanelTab.Workspace,
)

@Composable
fun FfApp() {
    val bootstrapDir = remember { FfAppPaths.defaultAppDir() }
    val bootstrapStore = remember(bootstrapDir) { FfBootstrapStore(bootstrapDir) }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(FfAppSettings()) }
    var navState by remember { mutableStateOf(FfNavState()) }
    var userDataDir by remember { mutableStateOf(bootstrapDir) }

    val settingsStore = remember(userDataDir) { FfSettingsStore(userDataDir) }
    val registryStore = remember(userDataDir) { FfRegistryStore(userDataDir) }

    LaunchedEffect(Unit) {
        val bootstrap = withContext(Dispatchers.IO) { bootstrapStore.load() }
        val resolved = bootstrap.userDataDir?.toAbsolutePath()?.normalize() ?: bootstrapDir
        userDataDir = resolved
    }

    LaunchedEffect(userDataDir) {
        settings = withContext(Dispatchers.IO) { settingsStore.load() }
    }

    fun updateSettings(newSettings: FfAppSettings) {
        settings = newSettings
        scope.launch(Dispatchers.IO) { settingsStore.save(newSettings) }
    }

    fun updateUserDataDir(newDir: Path) {
        val normalized = newDir.toAbsolutePath().normalize()
        if (normalized == userDataDir.toAbsolutePath().normalize()) return
        scope.launch(Dispatchers.IO) {
            migrateAppDataIfNeeded(from = userDataDir, to = normalized)
            bootstrapStore.save(FfBootstrapSettings(userDataDir = normalized))
            withContext(Dispatchers.Main) {
                userDataDir = normalized
            }
        }
    }

    FfTheme(theme = settings.theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                RootSharedTransition(
                    navState = navState,
                    appDir = userDataDir,
                    registryStore = registryStore,
                    settings = settings,
                    onUpdateSettings = ::updateSettings,
                    onUpdateUserDataDir = ::updateUserDataDir,
                    onBackToHome = { navState = navState.copy(route = FfRootRoute.Home) },
                    onNavigate = { route, tab -> navState = navState.copy(route = route, panelTab = tab) },
                )
            }
        }
    }
}

private fun rootTransition(): ContentTransform =
    fadeIn() togetherWith fadeOut()

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RootSharedTransition(
    navState: FfNavState,
    appDir: Path,
    registryStore: FfRegistryStore,
    settings: FfAppSettings,
    onUpdateSettings: (FfAppSettings) -> Unit,
    onUpdateUserDataDir: (Path) -> Unit,
    onBackToHome: () -> Unit,
    onNavigate: (FfRootRoute, FfPanelTab) -> Unit,
) {
    SharedTransitionLayout shared@{
        AnimatedContent(
            targetState = navState.route,
            transitionSpec = { rootTransition() },
            label = "root-route",
        ) { route ->
            when (route) {
                FfRootRoute.Home -> {
                    FfHomeScreen(
                        sharedTransitionScope = this@shared,
                        animatedVisibilityScope = this,
                        onOpenWorkspace = { onNavigate(FfRootRoute.Panel, FfPanelTab.Workspace) },
                        onOpenConfig = { onNavigate(FfRootRoute.Panel, FfPanelTab.Config) },
                        onOpenAbout = { onNavigate(FfRootRoute.Panel, FfPanelTab.About) },
                    )
                }

                FfRootRoute.Panel -> {
                    FfPanelScreen(
                        sharedTransitionScope = this@shared,
                        animatedVisibilityScope = this,
                        initialTab = navState.panelTab,
                        appDir = appDir,
                        registryStore = registryStore,
                        settings = settings,
                        onUpdateSettings = onUpdateSettings,
                        onUpdateUserDataDir = onUpdateUserDataDir,
                        onBackToHome = onBackToHome,
                    )
                }
            }
        }
    }
}

private fun migrateAppDataIfNeeded(from: Path, to: Path) {
    val source = from.toAbsolutePath().normalize()
    val target = to.toAbsolutePath().normalize()
    if (source == target) return
    if (target.exists() && !target.isDirectory()) return

    val filesToCopy = listOf(
        FfAppPaths.registryFile(source),
        FfAppPaths.settingsFile(source),
    )

    Files.createDirectories(target)
    for (file in filesToCopy) {
        if (!file.exists()) continue
        val dest = target.resolve(file.fileName.toString())
        if (dest.exists()) continue
        runCatching {
            Files.copy(file, dest)
        }
    }
}
