package love.forte.tools.ff.ui.workspace

import love.forte.tools.ff.fs.FfFlattenProgress
import love.forte.tools.ff.fs.FfFlattenReport
import love.forte.tools.ff.fs.FfTargetValidation
import java.nio.file.Path

sealed interface FfScanState {
    data object Idle : FfScanState
    data object Scanning : FfScanState
    data class Done(val extensionStats: Map<String, Int>) : FfScanState
    data class Failed(val message: String) : FfScanState
}

sealed interface FfRunState {
    data object Idle : FfRunState
    data class Running(val progress: FfFlattenProgress) : FfRunState
    data class Finished(val report: FfFlattenReport) : FfRunState
}

data class FfDraftTask(
    val id: String,
    val sourceDir: Path,
    val targetPathText: String = "",
    val targetDir: Path? = null,
    val targetValidation: FfTargetValidation? = null,
    val scanState: FfScanState = FfScanState.Idle,
    val selectedExtensions: Set<String> = emptySet(),
    val runState: FfRunState = FfRunState.Idle,
    val errorMessage: String? = null,
)

data class FfManagedTargetEntry(
    val targetDir: Path,
    val fileCount: Int,
    val sources: List<String>,
    val extensions: List<String>,
    val updatedAtEpochMillis: Long?,
)
