package love.forte.tools.ff.version

import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GitHubProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

data class FfAppUpdateState(
    val isChecking: Boolean = false,
    val updateAvailable: Boolean = false,
    val canTriggerUpdate: Boolean = false,
    val isInstalling: Boolean = false,
    val downloadProgress: Double? = null,
    val errorMessage: String? = null,
)

@Single
class FfAppUpdateManager(
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val updater = NucleusUpdater {
        provider = GitHubProvider(
            owner = "ForteScarlet",
            repo = "file-flattener",
        )
    }
    private var pendingUpdateInfo: UpdateInfo? = null
    private val _state = MutableStateFlow(FfAppUpdateState())
    val state: StateFlow<FfAppUpdateState> = _state.asStateFlow()

    suspend fun checkForUpdates() {
        if (!mutex.tryLock()) return
        try {
            _state.value = _state.value.copy(isChecking = true, errorMessage = null)
            val result = withContext(ioDispatcher) { updater.checkForUpdates() }
            _state.value = when (result) {
                is UpdateResult.Available -> {
                    pendingUpdateInfo = result.info
                    _state.value.copy(
                        isChecking = false,
                        updateAvailable = true,
                        canTriggerUpdate = updater.isUpdateSupported(),
                        errorMessage = null,
                    )
                }
                UpdateResult.NotAvailable -> {
                    pendingUpdateInfo = null
                    _state.value.copy(
                        isChecking = false,
                        updateAvailable = false,
                        canTriggerUpdate = false,
                        errorMessage = null,
                    )
                }
                is UpdateResult.Error -> {
                    pendingUpdateInfo = null
                    _state.value.copy(
                        isChecking = false,
                        updateAvailable = false,
                        canTriggerUpdate = false,
                        errorMessage = "检查更新失败：${result.exception.message ?: "未知错误"}",
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pendingUpdateInfo = null
            _state.value = _state.value.copy(
                isChecking = false,
                updateAvailable = false,
                canTriggerUpdate = false,
                errorMessage = "检查更新失败：${e.message ?: "未知错误"}",
            )
        } finally {
            mutex.unlock()
        }
    }

    suspend fun triggerUpdate() {
        val updateInfo = pendingUpdateInfo ?: return
        if (!updater.isUpdateSupported()) {
            _state.value = _state.value.copy(
                canTriggerUpdate = false,
                errorMessage = "当前运行环境不支持自动更新",
            )
            return
        }

        if (!mutex.tryLock()) return
        try {
            _state.value = _state.value.copy(
                canTriggerUpdate = false,
                isInstalling = true,
                downloadProgress = 0.0,
                errorMessage = null,
            )

            var installerFile: java.io.File? = null
            withContext(ioDispatcher) {
                updater.downloadUpdate(updateInfo).collect { progress ->
                    installerFile = progress.file ?: installerFile
                    _state.value = _state.value.copy(downloadProgress = progress.percent)
                }
            }

            val installer = installerFile
                ?: error("更新下载未生成安装包")
            withContext(ioDispatcher) {
                updater.installAndRestart(installer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isInstalling = false,
                canTriggerUpdate = true,
                errorMessage = "更新失败：${e.message ?: "未知错误"}",
            )
        } finally {
            mutex.unlock()
        }
    }
}
