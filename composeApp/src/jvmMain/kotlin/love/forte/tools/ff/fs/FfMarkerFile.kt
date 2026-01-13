package love.forte.tools.ff.fs

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import love.forte.tools.ff.FfConstants
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
    val schemaVersion: Int = FfConstants.MarkerSchemaVersion,
    val namingVersion: Int = FfConstants.NamingVersion,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val sources: List<FfMarkerSource>,
)

object FfMarkerFile {
    fun markerPath(directory: Path): Path = directory.resolve(FfConstants.MarkerFileName)

    fun isManagedDirectory(directory: Path): Boolean {
        if (!directory.exists() || !directory.isDirectory()) return false
        return markerPath(directory).isRegularFile()
    }

    fun read(directory: Path): FfMarkerConfig? {
        val marker = markerPath(directory)
        if (!marker.exists() || !marker.isRegularFile()) return null
        val raw = Files.readString(marker, StandardCharsets.UTF_8)
        val trimmed = raw.trimStart()
        return if (trimmed.startsWith("{")) {
            readJson(trimmed)
        } else {
            readLegacyKv(raw)
        }
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

    private fun readJson(trimmedJson: String): FfMarkerConfig? {
        return runCatching { FfJson.instance.decodeFromString(FfMarkerConfig.serializer(), trimmedJson) }
            .getOrNull()
            ?.takeIf { it.sources.isNotEmpty() && it.createdAtEpochMillis > 0 }
    }

    private fun readLegacyKv(raw: String): FfMarkerConfig? {
        val kv = raw
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull(::parseLine)
            .groupBy({ it.first }, { it.second })

        val schemaVersion = kv[Keys.SchemaVersion]?.firstOrNull()?.toIntOrNull() ?: 1
        val namingVersion = kv[Keys.NamingVersion]?.firstOrNull()?.toIntOrNull() ?: 1
        val createdAt = kv[Keys.CreatedAt]?.firstOrNull()?.toLongOrNull() ?: 0L
        val updatedAt = kv[Keys.UpdatedAt]?.firstOrNull()?.toLongOrNull() ?: createdAt
        val sources = kv[Keys.Source].orEmpty()
        val extensions = kv[Keys.Extension].orEmpty()

        if (sources.isEmpty()) return null
        if (createdAt <= 0L) return null

        return FfMarkerConfig(
            schemaVersion = schemaVersion,
            namingVersion = namingVersion,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = updatedAt,
            sources = sources.map { FfMarkerSource(path = it, extensions = extensions) },
        )
    }

    private fun parseLine(line: String): Pair<String, String>? {
        val idx = line.indexOf('=')
        if (idx <= 0) return null
        val key = line.substring(0, idx).trim()
        val value = line.substring(idx + 1).trim()
        if (key.isEmpty() || value.isEmpty()) return null
        return key to value
    }

    private fun trySetHiddenOnWindows(path: Path) {
        if (!isWindows()) return
        runCatching {
            Files.setAttribute(path, "dos:hidden", true)
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private object Keys {
        const val SchemaVersion: String = "schemaVersion"
        const val NamingVersion: String = "namingVersion"
        const val CreatedAt: String = "createdAt"
        const val UpdatedAt: String = "updatedAt"
        const val Source: String = "source"
        const val Extension: String = "ext"
    }
}
