package love.forte.tools.ff.ui.workspace

import love.forte.tools.ff.FfConstants
import love.forte.tools.ff.fs.FfMarkerFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

object FfWorkspaceLoader {
    fun loadManagedTargets(targetDirs: List<Path>): List<FfManagedTargetEntry> {
        return targetDirs
            .asSequence()
            .filter { it.isDirectory() }
            .mapNotNull(::toEntryOrNull)
            .sortedBy { it.targetDir.toAbsolutePath().toString() }
            .toList()
    }

    private fun toEntryOrNull(targetDir: Path): FfManagedTargetEntry? {
        val marker = FfMarkerFile.read(targetDir) ?: return null
        val fileCount = countFlattenedFiles(targetDir)
        val sources = marker.sources.map { it.path }
        val extensions = marker.sources
            .asSequence()
            .flatMap { it.extensions.asSequence() }
            .distinct()
            .sortedWith(extensionComparator())
            .toList()
        return FfManagedTargetEntry(
            targetDir = targetDir,
            fileCount = fileCount,
            sources = sources,
            extensions = extensions,
            updatedAtEpochMillis = marker.updatedAtEpochMillis,
        )
    }

    private fun countFlattenedFiles(targetDir: Path): Int {
        if (!targetDir.isDirectory()) return 0
        return Files.newDirectoryStream(targetDir).use { dir ->
            dir.asSequence()
                .filter { it.fileName?.toString() != FfConstants.MarkerFileName }
                .count { it.isRegularFile() }
        }
    }

    private fun extensionComparator(): Comparator<String> = Comparator { a, b ->
        when {
            a == b -> 0
            a == FfConstants.ExtensionNone -> -1
            b == FfConstants.ExtensionNone -> 1
            else -> a.compareTo(b)
        }
    }
}
