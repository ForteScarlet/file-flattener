package love.forte.tools.ff.fs

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

enum class FfTargetValidation {
    OkManaged,
    OkEmpty,
    OkNew,
    NotAllowedNotEmpty,
    NotAllowedNotDirectory,
}

object FfTargetValidator {
    fun validate(target: Path): FfTargetValidation {
        if (!target.exists()) return FfTargetValidation.OkNew
        if (!target.isDirectory()) return FfTargetValidation.NotAllowedNotDirectory
        if (FfMarkerFile.isManagedDirectory(target)) return FfTargetValidation.OkManaged
        return if (isEmptyDirectory(target)) FfTargetValidation.OkEmpty else FfTargetValidation.NotAllowedNotEmpty
    }

    private fun isEmptyDirectory(dir: Path): Boolean =
        Files.newDirectoryStream(dir).use { it.iterator().hasNext().not() }
}
