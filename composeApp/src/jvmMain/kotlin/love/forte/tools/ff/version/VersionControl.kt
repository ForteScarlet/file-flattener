package love.forte.tools.ff.version

import dev.hydraulic.conveyor.control.SoftwareUpdateController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

data class FfAppUpdateState(
    val isChecking: Boolean = false,
    val updateAvailable: Boolean = false,
    val canTriggerUpdate: Boolean = false,
    val errorMessage: String? = null,
)

@Single
class FfAppUpdateManager(
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(FfAppUpdateState())
    val state: StateFlow<FfAppUpdateState> = _state.asStateFlow()

    suspend fun checkForUpdates() {
        if (!mutex.tryLock()) return
        try {
            _state.value = _state.value.copy(isChecking = true, errorMessage = null)
            val result = withContext(ioDispatcher) { checkUpdatesInternal() }
            _state.value = when (result) {
                is UpdateCheckResult.Success -> _state.value.copy(
                    isChecking = false,
                    updateAvailable = result.updateAvailable,
                    canTriggerUpdate = result.canTriggerUpdate,
                    errorMessage = null,
                )
                is UpdateCheckResult.Failure -> _state.value.copy(
                    isChecking = false,
                    errorMessage = result.message,
                )
            }
        } finally {
            mutex.unlock()
        }
    }

    fun triggerUpdate(): Boolean {
        val controller = SoftwareUpdateController.getInstance() ?: return false
        if (controller.canTriggerUpdateCheckUI() != SoftwareUpdateController.Availability.AVAILABLE) {
            return false
        }

        return runCatching {
            controller.triggerUpdateCheckUI()
            true
        }.getOrDefault(false)
    }

    private fun checkUpdatesInternal(): UpdateCheckResult {
        val controller = SoftwareUpdateController.getInstance()
            ?: return UpdateCheckResult.Failure("当前运行环境不支持检查更新")

        val currentVersion = controller.currentVersion
            ?: return UpdateCheckResult.Failure("无法获取当前版本信息")

        return try {
            val latestVersion = controller.currentVersionFromRepository
                ?: return UpdateCheckResult.Failure("无法获取最新版本信息")

            val updateAvailable = latestVersion > currentVersion
            val canTriggerUpdate = if (updateAvailable) {
                controller.canTriggerUpdateCheckUI() == SoftwareUpdateController.Availability.AVAILABLE
            } else {
                false
            }

            UpdateCheckResult.Success(updateAvailable, canTriggerUpdate)
        } catch (e: SoftwareUpdateController.UpdateCheckException) {
            UpdateCheckResult.Failure("检查更新失败：${e.message ?: "未知错误"}")
        } catch (e: Exception) {
            UpdateCheckResult.Failure("检查更新失败：${e.message ?: "未知错误"}")
        }
    }
}

private sealed interface UpdateCheckResult {
    data class Success(val updateAvailable: Boolean, val canTriggerUpdate: Boolean) : UpdateCheckResult
    data class Failure(val message: String) : UpdateCheckResult
}
