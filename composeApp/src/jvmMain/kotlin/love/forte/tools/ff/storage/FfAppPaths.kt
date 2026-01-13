package love.forte.tools.ff.storage

import java.nio.file.Path
import java.nio.file.Paths

object FfAppPaths {
    private const val AppDirName = ".file-flattener"

    fun defaultAppDir(): Path = Paths.get(System.getProperty("user.home")).resolve(AppDirName)

    fun bootstrapFile(bootstrapDir: Path): Path = bootstrapDir.resolve("bootstrap.properties")

    fun registryFile(appDir: Path): Path = appDir.resolve("registry.txt")

    fun settingsFile(appDir: Path): Path = appDir.resolve("settings.properties")
}
