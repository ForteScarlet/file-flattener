package love.forte.tools.ff.ui.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import kotlinx.coroutines.CancellationException
import java.nio.file.Path

object FfFileDialogs {
    /**
     * FileKit's native directory picker selects one directory at a time. Reopen it after each
     * selection so the existing multi-source workflow remains available without Swing.
     */
    suspend fun pickDirectories(title: String): List<Path> {
        val selected = linkedSetOf<Path>()
        val settings = FileKitDialogSettings(title = title)

        while (true) {
            val directory = try {
                FileKit.openDirectoryPicker(dialogSettings = settings)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: break

            selected.add(Path.of(directory.path).toAbsolutePath().normalize())
        }

        return selected.toList()
    }

    suspend fun pickDirectory(title: String): Path? {
        val settings = FileKitDialogSettings(title = title)
        return try {
            FileKit.openDirectoryPicker(dialogSettings = settings)
                ?.let { Path.of(it.path).toAbsolutePath().normalize() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
