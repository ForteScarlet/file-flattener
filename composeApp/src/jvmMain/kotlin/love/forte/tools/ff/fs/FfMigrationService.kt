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

/** 单个迁移任务配置 */
data class FfMigrationTask(
    val targetDir: Path,
    val sources: List<FfFlattenSourceConfig>,
    val expectedTotalFiles: Int,
)

/** 单个迁移任务的执行结果 */
data class FfMigrationTaskResult(
    val task: FfMigrationTask,
    val report: FfFlattenReport?,
    val errorMessage: String?,
)

/** 整体迁移报告 */
data class FfMigrationReport(
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val taskResults: List<FfMigrationTaskResult>,
)

/**
 * 迁移服务：支持多任务并发执行的批量迁移
 *
 * 使用信号量控制并发度，确保系统资源合理利用
 */
class FfMigrationService(
    private val flattener: FfFlattenService = FfFlattenService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * 执行批量迁移
     *
     * @param tasks 待执行的迁移任务列表
     * @param concurrencyLimit 任务并发上限
     * @param linkConcurrencyPerTask 每个任务内的链接并发数
     * @param onTaskProgress 单任务进度回调
     * @return 迁移报告（包含所有任务的结果）
     */
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

