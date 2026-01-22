package love.forte.tools.ff.fs

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import love.forte.tools.ff.FfConstants
import love.forte.tools.ff.FfNamingVersion
import love.forte.tools.ff.serialization.FfJson
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

@Serializable
data class FfMarkerSource(
    val path: String,
    val extensions: List<String> = emptyList(),
)

@Serializable
data class FfMarkerConfig(
    val schemaVersion: Int = FfConstants.MARKER_SCHEMA_VERSION,
    val namingVersion: FfNamingVersion = FfConstants.NAMING_VERSION,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val sources: List<FfMarkerSource>,
) {
}

object FfMarkerFile {
    fun markerPath(directory: Path): Path = directory.resolve(FfConstants.MARKER_FILE_NAME)

    fun isManagedDirectory(directory: Path): Boolean {
        if (!directory.exists() || !directory.isDirectory()) return false
        return markerPath(directory).isRegularFile()
    }

    fun read(directory: Path): FfMarkerConfig? {
        val marker = markerPath(directory)
        if (!marker.exists() || !marker.isRegularFile()) return null
        val raw = Files.readString(marker, StandardCharsets.UTF_8)
        return runCatching { FfJson.instance.decodeFromString(FfMarkerConfig.serializer(), raw.trimStart()) }
            .getOrNull()
            ?.takeIf { it.sources.isNotEmpty() && it.createdAtEpochMillis > 0 }
    }

    fun write(directory: Path, config: FfMarkerConfig) {
        Files.createDirectories(directory)
        val marker = markerPath(directory)
        val text = FfJson.instance.encodeToString(config)
        Files.writeString(
            marker,
            text,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        trySetHiddenOnWindows(marker)
    }

    private fun trySetHiddenOnWindows(path: Path) {
        if (isWindows()) {
            runCatching {
                Files.setAttribute(path, "dos:hidden", true)
            }
        }

    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")
}
