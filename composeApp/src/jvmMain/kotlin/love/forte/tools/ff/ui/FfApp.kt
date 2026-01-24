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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import love.forte.tools.ff.db.FfDatabaseInitState
import love.forte.tools.ff.db.FfDatabaseManager
import love.forte.tools.ff.storage.FfAppPaths
import love.forte.tools.ff.storage.FfAppSettings
import love.forte.tools.ff.storage.FfBootstrapSettings
import love.forte.tools.ff.storage.FfBootstrapStore
import love.forte.tools.ff.storage.FfRegistryStoreAdapter
import love.forte.tools.ff.storage.FfSettingsStoreAdapter
import love.forte.tools.ff.ui.screens.FfHomeScreen
import love.forte.tools.ff.ui.screens.FfPanelScreen
import love.forte.tools.ff.ui.theme.FfTheme
import org.koin.compose.koinInject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/** 根路由：Home（首页）或 Panel（功能面板） */
enum class FfRootRoute { Home, Panel }

/** 功能面板的标签页 */
enum class FfPanelTab { Workspace, Config, About, Logs }

/** 导航状态：当前路由 + 面板标签页 */
data class FfNavState(
    val route: FfRootRoute = FfRootRoute.Home,
    val panelTab: FfPanelTab = FfPanelTab.Workspace,
)

/**
 * 应用根组件
 *
 * 职责：
 * 1. 初始化数据库和加载引导配置
 * 2. 管理全局导航状态
 * 3. 管理应用设置并持久化
 *
 * @param onExit 退出应用回调
 */
@Preview
@Composable
fun FfApp(onExit: () -> Unit = {}) {
    val bootstrapDir = remember { FfAppPaths.defaultAppDir() }
    val bootstrapStore: FfBootstrapStore = koinInject()
    val databaseManager: FfDatabaseManager = koinInject()
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(FfAppSettings()) }
    var navState by remember { mutableStateOf(FfNavState()) }
    var userDataDir by remember { mutableStateOf<Path?>(null) }
    var dbInitState by remember { mutableStateOf<FfDatabaseInitState>(FfDatabaseInitState.NotInitialized) }

    // 仅在数据库初始化完成后创建 adapter
    val settingsStoreAdapter = remember(dbInitState) {
        if (dbInitState is FfDatabaseInitState.Ready) {
            FfSettingsStoreAdapter.fromManager(databaseManager)
        } else null
    }
    val registryStoreAdapter = remember(dbInitState) {
        if (dbInitState is FfDatabaseInitState.Ready) {
            FfRegistryStoreAdapter.fromManager(databaseManager)
        } else null
    }

    LaunchedEffect(Unit) {
        // 第一步：读取引导设置
        val bootstrap = withContext(Dispatchers.IO) { bootstrapStore.load() }
        val resolved = bootstrap.userDataDir?.toAbsolutePath()?.normalize() ?: bootstrapDir
        userDataDir = resolved
    }

    LaunchedEffect(userDataDir) {
        if (userDataDir == null) return@LaunchedEffect

        // 第二步：初始化数据库
        val initResult = withContext(Dispatchers.IO) {
            databaseManager.initialize(userDataDir!!)
        }
        dbInitState = initResult

        // 第三步：加载设置
        if (initResult is FfDatabaseInitState.Ready) {
            settings = databaseManager.settingsRepository.load()
        }
    }

    fun updateSettings(newSettings: FfAppSettings) {
        settings = newSettings
        scope.launch(Dispatchers.IO) {
            databaseManager.settingsRepository.save(newSettings)
        }
    }

    fun updateUserDataDir(newDir: Path) {
        val normalized = newDir.toAbsolutePath().normalize()
        if (normalized == userDataDir?.toAbsolutePath()?.normalize()) return
        scope.launch(Dispatchers.IO) {
            migrateDatabaseIfNeeded(from = userDataDir, to = normalized)
            bootstrapStore.save(FfBootstrapSettings(userDataDir = normalized))

            // 切换到新目录
            val initResult = databaseManager.switchDataDir(normalized)
            dbInitState = initResult

            withContext(Dispatchers.Main) {
                userDataDir = normalized
                // 重新加载设置
                if (initResult is FfDatabaseInitState.Ready) {
                    settings = databaseManager.settingsRepository.load()
                }
            }
        }
    }

    FfTheme(theme = settings.theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (dbInitState) {
                    is FfDatabaseInitState.NotInitialized,
                    is FfDatabaseInitState.Initializing -> {
                        // 显示加载指示器
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    is FfDatabaseInitState.Failed -> {
                        // 显示错误信息
                        Box(modifier = Modifier.fillMaxSize()) {
                            // TODO: 实现错误显示界面
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    is FfDatabaseInitState.Ready -> {
                        val registryStoreAdapter = registryStoreAdapter
                        val settingsStoreAdapter = settingsStoreAdapter
                        if (registryStoreAdapter != null && settingsStoreAdapter != null && userDataDir != null) {
                            RootSharedTransition(
                                navState = navState,
                                appDir = userDataDir!!,
                                registryStoreAdapter = registryStoreAdapter,
                                settings = settings,
                                onUpdateSettings = ::updateSettings,
                                onUpdateUserDataDir = ::updateUserDataDir,
                                onBackToHome = { navState = navState.copy(route = FfRootRoute.Home) },
                                onNavigate = { route, tab -> navState = navState.copy(route = route, panelTab = tab) },
                                onExit = onExit,
                            )
                        }
                    }
                }
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
    registryStoreAdapter: FfRegistryStoreAdapter,
    settings: FfAppSettings,
    onUpdateSettings: (FfAppSettings) -> Unit,
    onUpdateUserDataDir: (Path) -> Unit,
    onBackToHome: () -> Unit,
    onNavigate: (FfRootRoute, FfPanelTab) -> Unit,
    onExit: () -> Unit,
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
                        onExit = onExit,
                    )
                }

                FfRootRoute.Panel -> {
                    FfPanelScreen(
                        sharedTransitionScope = this@shared,
                        animatedVisibilityScope = this,
                        initialTab = navState.panelTab,
                        appDir = appDir,
                        registryStoreAdapter = registryStoreAdapter,
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

private fun migrateDatabaseIfNeeded(from: Path?, to: Path) {
    val source = from?.toAbsolutePath()?.normalize()
    val target = to.toAbsolutePath().normalize()

    if (source == target) return
    if (target.exists() && !target.isDirectory()) return

    Files.createDirectories(target)

    // 源目录可能不存在（全新安装）
    if (source == null || !source.exists()) return

    // 检查源目录是否有数据库文件
    val sourceDb = source.resolve("ff_data.db")
    val targetDb = target.resolve("ff_data.db")

    if (!sourceDb.exists()) return
    if (targetDb.exists()) return // 目标已存在数据库，不覆盖

    // 复制数据库文件
    runCatching {
        Files.copy(sourceDb, targetDb)
    }

    // 如果存在备份的旧文件，也复制它们（可选）
    val sourceBackups = source.toFile().listFiles { file ->
        file.name.matches(Regex(".*\\.bak\\.\\d+$"))
    } ?: emptyArray()

    sourceBackups.forEach { backup ->
        runCatching {
            val destBackup = target.resolve(backup.name)
            if (!destBackup.exists()) {
                Files.copy(backup.toPath(), destBackup)
            }
        }
    }
}
