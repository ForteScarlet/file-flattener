package love.forte.tools.ff.ui.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import javax.swing.JFileChooser

object FfFileDialogs {
    suspend fun pickDirectories(title: String): List<Path> = withContext(Dispatchers.Swing) {
        val native = pickDirectoriesNativeIfSupported(title)
        if (native.supported) return@withContext native.selected
        pickDirectoriesSwing(title)
    }

    suspend fun pickDirectory(title: String): Path? = withContext(Dispatchers.Swing) {
        val native = pickDirectoryNativeIfSupported(title)
        if (native.supported) return@withContext native.selected
        pickDirectorySwing(title)
    }

    private fun pickDirectoriesSwing(title: String): List<Path> {
        FfSwingUiBootstrap.ensureInitialized()
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = true
        }
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return emptyList()
        return chooser.selectedFiles.orEmpty().map { it.toPath() }
    }

    private fun pickDirectorySwing(title: String): Path? {
        FfSwingUiBootstrap.ensureInitialized()
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
        }
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.toPath()
    }

    private data class NativePickDirectoriesResult(
        val supported: Boolean,
        val selected: List<Path>,
    )

    private data class NativePickDirectoryResult(
        val supported: Boolean,
        val selected: Path?,
    )

    /**
     * 说明：
     * - JDK 标准库里，目录选择的“原生对话框”能力存在平台差异；
     * - macOS 可通过 apple 属性让 AWT FileDialog 以目录模式工作；
     * - Windows / Linux 取决于 JDK/桌面环境是否支持目录模式，若失败则回退 Swing。
     */
    private fun pickDirectoriesNativeIfSupported(title: String): NativePickDirectoriesResult {
        if (!isMacOs() && !isWindows() && !isLinux()) return NativePickDirectoriesResult(supported = false, selected = emptyList())

        return withDirectoryDialogProperty {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
                isMultipleMode = true
                isVisible = true
            }
            val raw = dialog.files.orEmpty()
            val selected = raw
                .asSequence()
                .map { it.toPath() }
                .filter { it.toFile().isDirectory }
                .toList()

            val supported = raw.isEmpty() || selected.isNotEmpty()
            NativePickDirectoriesResult(supported = supported, selected = selected)
        }
    }

    private fun pickDirectoryNativeIfSupported(title: String): NativePickDirectoryResult {
        if (!isMacOs() && !isWindows() && !isLinux()) return NativePickDirectoryResult(supported = false, selected = null)

        return withDirectoryDialogProperty {
            val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
                isMultipleMode = false
                isVisible = true
            }
            val raw = dialog.files.orEmpty()
            val selected = raw
                .firstOrNull()
                ?.toPath()
                ?.takeIf { it.toFile().isDirectory }

            val supported = raw.isEmpty() || selected != null
            NativePickDirectoryResult(supported = supported, selected = selected)
        }
    }

    private inline fun <T> withDirectoryDialogProperty(block: () -> T): T {
        val keys = listOf(
            "apple.awt.fileDialogForDirectories",
            "java.awt.fileDialogForDirectories",
        )
        val old = keys.associateWith { System.getProperty(it) }
        keys.forEach { System.setProperty(it, "true") }
        return try {
            block()
        } finally {
            keys.forEach { key ->
                val value = old[key]
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
        }
    }

    private fun isMacOs(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun isLinux(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("linux")
}
