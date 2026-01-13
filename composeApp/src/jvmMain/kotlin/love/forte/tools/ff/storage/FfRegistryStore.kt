package love.forte.tools.ff.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class FfRegistryStore(
    private val appDir: Path,
) {
    private val file: Path = FfAppPaths.registryFile(appDir)

    fun loadTargets(): List<Path> {
        if (!file.exists()) return emptyList()
        return Files.readAllLines(file)
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { Path.of(it) }
            .filter { it.exists() && it.isDirectory() }
            .distinctBy { it.normalize().absolutePathString() }
            .toList()
    }

    fun saveTargets(targets: Collection<Path>) {
        Files.createDirectories(appDir)
        val normalized = targets
            .asSequence()
            .map { it.normalize().toAbsolutePath() }
            .distinctBy { it.absolutePathString() }
            .toList()

        val lines = buildList {
            add("# file-flattener registry v1")
            normalized.forEach { add(it.absolutePathString()) }
        }
        Files.write(file, lines)
    }
}

