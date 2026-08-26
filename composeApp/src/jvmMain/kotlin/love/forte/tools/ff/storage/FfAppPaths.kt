package love.forte.tools.ff.storage

import dev.nucleusframework.aot.runtime.AotRuntime
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object FfAppPaths {
    private const val APP_DIR_NAME = ".file-flattener"
    private const val AOT_TRAINING_DIR_PREFIX = "file-flattener-aot-"

    private val aotTrainingAppDir: Path? by lazy {
        if (!AotRuntime.isTraining()) return@lazy null

        Files.createTempDirectory(AOT_TRAINING_DIR_PREFIX).also { trainingDir ->
            Runtime.getRuntime().addShutdownHook(
                Thread({ trainingDir.toFile().deleteRecursively() }, "file-flattener-aot-cleanup")
            )
        }
    }

    fun defaultAppDir(): Path = aotTrainingAppDir
        ?: Paths.get(System.getProperty("user.home")).resolve(APP_DIR_NAME)

    fun bootstrapFile(bootstrapDir: Path): Path = bootstrapDir.resolve("bootstrap.properties")
}
