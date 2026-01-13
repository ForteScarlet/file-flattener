package love.forte.tools.ff.storage

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.absolutePathString

data class FfBootstrapSettings(
    val userDataDir: Path?,
)

class FfBootstrapStore(
    private val bootstrapDir: Path,
) {
    private val file: Path = FfAppPaths.bootstrapFile(bootstrapDir)

    fun load(): FfBootstrapSettings {
        val props = Properties()
        if (Files.exists(file)) {
            Files.newInputStream(file).use(props::load)
        }
        val userDataDir = props.getProperty(Keys.UserDataDir)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Path.of(it) }.getOrNull() }

        return FfBootstrapSettings(userDataDir = userDataDir)
    }

    fun save(settings: FfBootstrapSettings) {
        Files.createDirectories(bootstrapDir)
        val props = Properties()
        if (settings.userDataDir != null) {
            props[Keys.UserDataDir] = settings.userDataDir.toAbsolutePath().normalize().absolutePathString()
        }
        Files.newOutputStream(file).use { out ->
            props.store(out, "file-flattener bootstrap")
        }
    }

    private object Keys {
        const val UserDataDir: String = "userDataDir"
    }
}

