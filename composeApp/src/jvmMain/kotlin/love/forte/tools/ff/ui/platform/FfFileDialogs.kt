package love.forte.tools.ff.ui.platform

import com.formdev.flatlaf.util.SystemFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.nio.file.Path

object FfFileDialogs {
    suspend fun pickDirectories(title: String): List<Path> = withContext(Dispatchers.Swing) {
        FfSwingUiBootstrap.ensureInitialized()
        val chooser = directoryChooser(title, multiSelectionEnabled = true)
        if (chooser.showOpenDialog(null) != SystemFileChooser.APPROVE_OPTION) return@withContext emptyList()
        chooser.selectedFiles.orEmpty().map { it.toPath() }
    }

    suspend fun pickDirectory(title: String): Path? = withContext(Dispatchers.Swing) {
        FfSwingUiBootstrap.ensureInitialized()
        val chooser = directoryChooser(title, multiSelectionEnabled = false)
        if (chooser.showOpenDialog(null) != SystemFileChooser.APPROVE_OPTION) return@withContext null
        chooser.selectedFiles.orEmpty().firstOrNull()?.toPath()
    }

    private fun directoryChooser(title: String, multiSelectionEnabled: Boolean): SystemFileChooser =
        SystemFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = SystemFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = multiSelectionEnabled
        }
}
