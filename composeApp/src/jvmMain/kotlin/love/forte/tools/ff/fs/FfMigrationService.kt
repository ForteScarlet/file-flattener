package love.forte.tools.ff.fs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import love.forte.tools.ff.FfConstants
import java.nio.file.Path

data class FfMigrationTask(
    val targetDir: Path,
    val sources: List<FfFlattenSourceConfig>,
    val expectedTotalFiles: Int,
)

data class FfMigrationTaskResult(
    val task: FfMigrationTask,
    val report: FfFlattenReport?,
    val errorMessage: String?,
)

data class FfMigrationReport(
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val taskResults: List<FfMigrationTaskResult>,
)

class FfMigrationService(
    private val flattener: FfFlattenService = FfFlattenService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun migrate(
        tasks: List<FfMigrationTask>,
        concurrencyLimit: Int,
        linkConcurrencyPerTask: Int,
        onTaskProgress: (Path, FfFlattenProgress) -> Unit = { _, _ -> },
    ): FfMigrationReport = withContext(ioDispatcher) {
        val normalizedLimit = concurrencyLimit.coerceIn(1, FfConstants.MAX_LINK_CONCURRENCY)
        val normalizedLinkConcurrency = linkConcurrencyPerTask.coerceIn(1, FfConstants.MAX_LINK_CONCURRENCY)
        val semaphore = Semaphore(normalizedLimit)

        val startedAt = System.currentTimeMillis()
        val results = coroutineScope {
            tasks.map { task ->
                async(ioDispatcher) {
                    semaphore.withPermit {
                        runCatching {
                            val report = flattener.flatten(
                                config = FfFlattenTaskConfig(
                                    targetDir = task.targetDir,
                                    sources = task.sources,
                                    expectedTotalFiles = task.expectedTotalFiles,
                                    linkConcurrency = normalizedLinkConcurrency,
                                ),
                                onProgress = { progress -> onTaskProgress(task.targetDir, progress) },
                            )
                            FfMigrationTaskResult(task = task, report = report, errorMessage = null)
                        }.getOrElse { e ->
                            FfMigrationTaskResult(task = task, report = null, errorMessage = e.message ?: "迁移失败")
                        }
                    }
                }
            }.awaitAll()
        }
        val finishedAt = System.currentTimeMillis()
        FfMigrationReport(
            startedAtEpochMillis = startedAt,
            finishedAtEpochMillis = finishedAt,
            taskResults = results,
        )
    }
}

