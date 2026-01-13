package love.forte.tools.ff.fs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import love.forte.tools.ff.FfConstants
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

data class FfFlattenSourceConfig(
    val sourceDir: Path,
    val selectedExtensions: Set<String>,
)

data class FfFlattenTaskConfig(
    val targetDir: Path,
    val sources: List<FfFlattenSourceConfig>,
    val expectedTotalFiles: Int? = null,
    val linkConcurrency: Int = FfConstants.LegacyDefaultConcurrency,
)

data class FfFlattenProgress(
    val expectedTotalFiles: Int,
    val completedFiles: Int,
    val createdLinks: Int,
    val skippedExisting: Int,
    val failedLinks: Int,
    val currentFile: String?,
)

data class FfFlattenReport(
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val expectedTotalFiles: Int,
    val completedFiles: Int,
    val createdLinks: Int,
    val skippedExisting: Int,
    val failedLinks: Int,
    val errors: List<String>,
)

class FfFlattenService(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun flatten(
        config: FfFlattenTaskConfig,
        onProgress: (FfFlattenProgress) -> Unit = {},
    ): FfFlattenReport = withContext(ioDispatcher) {
        val sources = normalizeSources(config.sources)
        if (sources.isEmpty()) throw IllegalArgumentException("sources 不能为空")

        val validation = FfTargetValidator.validate(config.targetDir)
        if (validation == FfTargetValidation.NotAllowedNotDirectory || validation == FfTargetValidation.NotAllowedNotEmpty) {
            throw IllegalArgumentException("目标目录不合法：$validation")
        }

        val now = System.currentTimeMillis()
        val existingMarker = FfMarkerFile.read(config.targetDir)
        val createdAt = existingMarker?.createdAtEpochMillis?.takeIf { it > 0 } ?: now
        val namingVersion = existingMarker?.namingVersion?.takeIf { it > 0 } ?: FfConstants.NamingVersion
        val markerConfig = FfMarkerConfig(
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = now,
            namingVersion = namingVersion,
            sources = sources.map {
                FfMarkerSource(
                    path = it.sourceDir.absolutePathString(),
                    extensions = it.selectedExtensions.sorted(),
                )
            },
        )
        FfMarkerFile.write(config.targetDir, markerConfig)

        val completed = AtomicInteger(0)
        val created = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val errors = Collections.synchronizedList(mutableListOf<String>())
        val currentFile = AtomicReference<String?>(null)

        val tasks = buildLinkTasks(
            sources = sources,
            targetDir = config.targetDir,
            namingVersion = namingVersion,
        )
        val expectedTotalFiles = (config.expectedTotalFiles ?: tasks.size).coerceAtLeast(0)
        val progressEmitter = ProgressEmitter(expectedTotalFiles, completed, created, skipped, failed, currentFile, onProgress)

        progressEmitter.tryEmit()

        val concurrency = config.linkConcurrency.coerceIn(1, FfConstants.MaxLinkConcurrency)
        val startedAt = now
        coroutineScope {
            val channel = Channel<LinkTask>(capacity = concurrency * 2)
            val workers = List(concurrency) { workerIndex ->
                launchWorker(workerIndex, channel, completed, created, skipped, failed, errors, currentFile, progressEmitter)
            }

            launch {
                for (task in tasks) {
                    channel.send(task)
                    progressEmitter.tryEmit()
                }
                channel.close()
            }

            workers.joinAll()
        }

        val finishedAt = System.currentTimeMillis()
        progressEmitter.forceEmit()

        val finalMarker = markerConfig.copy(updatedAtEpochMillis = finishedAt)
        FfMarkerFile.write(config.targetDir, finalMarker)

        FfFlattenReport(
            startedAtEpochMillis = startedAt,
            finishedAtEpochMillis = finishedAt,
            expectedTotalFiles = expectedTotalFiles,
            completedFiles = completed.get(),
            createdLinks = created.get(),
            skippedExisting = skipped.get(),
            failedLinks = failed.get(),
            errors = errors.toList(),
        )
    }

    private fun CoroutineScope.launchWorker(
        workerIndex: Int,
        channel: Channel<LinkTask>,
        completed: AtomicInteger,
        created: AtomicInteger,
        skipped: AtomicInteger,
        failed: AtomicInteger,
        errors: MutableList<String>,
        currentFile: AtomicReference<String?>,
        progressEmitter: ProgressEmitter,
    ): Job = launch(ioDispatcher) {
        for (task in channel) {
            runCatching {
                currentFile.set(task.existing.absolutePathString())
                if (task.link.exists()) {
                    skipped.incrementAndGet()
                    completed.incrementAndGet()
                    return@runCatching
                }
                Files.createLink(task.link, task.existing)
                created.incrementAndGet()
                completed.incrementAndGet()
            }.onFailure { e ->
                failed.incrementAndGet()
                completed.incrementAndGet()
                errors.add("[worker-$workerIndex] ${task.existing} -> ${task.link} : ${e.message.orEmpty()}")
            }
            progressEmitter.tryEmit()
        }
    }

    private data class LinkTask(
        val existing: Path,
        val link: Path,
    )

    private class ProgressEmitter(
        private val expectedTotalFiles: Int,
        private val completed: AtomicInteger,
        private val created: AtomicInteger,
        private val skipped: AtomicInteger,
        private val failed: AtomicInteger,
        private val currentFile: AtomicReference<String?>,
        private val onProgress: (FfFlattenProgress) -> Unit,
    ) {
        private val lastEmitAt = AtomicLong(0)

        fun tryEmit() {
            val now = System.currentTimeMillis()
            val last = lastEmitAt.get()
            if (now - last < FfConstants.ProgressEmitIntervalMs) return
            if (lastEmitAt.compareAndSet(last, now)) emit()
        }

        fun forceEmit() {
            lastEmitAt.set(System.currentTimeMillis())
            emit()
        }

        private fun emit() {
            onProgress(
                FfFlattenProgress(
                    expectedTotalFiles = expectedTotalFiles,
                    completedFiles = completed.get(),
                    createdLinks = created.get(),
                    skippedExisting = skipped.get(),
                    failedLinks = failed.get(),
                    currentFile = currentFile.get(),
                ),
            )
        }
    }

    private fun normalizeSources(raw: List<FfFlattenSourceConfig>): List<FfFlattenSourceConfig> {
        return raw
            .asSequence()
            .map { it.copy(sourceDir = it.sourceDir.toAbsolutePath().normalize()) }
            .filter { it.selectedExtensions.isNotEmpty() }
            .groupBy { it.sourceDir.absolutePathString() }
            .mapNotNull { (_, items) ->
                val sourceDir = items.firstOrNull()?.sourceDir ?: return@mapNotNull null
                val selected = items.asSequence().flatMap { it.selectedExtensions.asSequence() }.toSet()
                if (selected.isEmpty()) return@mapNotNull null
                FfFlattenSourceConfig(sourceDir = sourceDir, selectedExtensions = selected)
            }
            .sortedBy { it.sourceDir.absolutePathString() }
            .toList()
    }

    private fun buildLinkTasks(
        sources: List<FfFlattenSourceConfig>,
        targetDir: Path,
        namingVersion: Int,
    ): List<LinkTask> {
        return if (namingVersion <= 1) {
            buildLinkTasksV1(sources, targetDir)
        } else {
            buildLinkTasksV2(sources, targetDir)
        }
    }

    private fun buildLinkTasksV1(
        sources: List<FfFlattenSourceConfig>,
        targetDir: Path,
    ): List<LinkTask> {
        val tasks = mutableListOf<LinkTask>()
        for (source in sources) {
            FfDirectoryScanner.forEachMatchingFile(source.sourceDir, source.selectedExtensions) { file ->
                val link = targetDir.resolve(FfFlattenNaming.outputFileNameV1(source.sourceDir, file))
                tasks.add(LinkTask(existing = file, link = link))
            }
        }
        return tasks
    }

    private data class Candidate(
        val sourceRoot: Path,
        val file: Path,
        val prefixKey: String,
        val fileName: String,
    )

    private data class PrefixSourceKey(
        val prefixKey: String,
        val sourceRoot: Path,
    )

    private fun buildLinkTasksV2(
        sources: List<FfFlattenSourceConfig>,
        targetDir: Path,
    ): List<LinkTask> {
        val candidates = mutableListOf<Candidate>()
        val prefixSources = linkedMapOf<String, MutableSet<Path>>()

        for (source in sources) {
            FfDirectoryScanner.forEachMatchingFile(source.sourceDir, source.selectedExtensions) { file ->
                val prefixKey = FfFlattenNaming.prefixKeyV2(source.sourceDir, file)
                candidates.add(
                    Candidate(
                        sourceRoot = source.sourceDir,
                        file = file,
                        prefixKey = prefixKey,
                        fileName = file.fileName?.toString().orEmpty(),
                    ),
                )
                prefixSources.getOrPut(prefixKey) { linkedSetOf() }.add(source.sourceDir)
            }
        }

        val suffixIndex = HashMap<PrefixSourceKey, Int>(candidates.size)
        for ((prefixKey, roots) in prefixSources) {
            val orderedRoots = roots.sortedBy { it.absolutePathString() }
            orderedRoots.forEachIndexed { idx, root ->
                suffixIndex[PrefixSourceKey(prefixKey, root)] = if (idx == 0) 0 else idx
            }
        }

        return candidates.map { candidate ->
            val suffix = suffixIndex[PrefixSourceKey(candidate.prefixKey, candidate.sourceRoot)] ?: 0
            val fileName = FfFlattenNaming.outputFileNameV2(candidate.prefixKey, suffix, candidate.fileName)
            LinkTask(existing = candidate.file, link = targetDir.resolve(fileName))
        }
    }
}
