package love.forte.tools.ff.fs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.asIntState
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.updateAndGet
import love.forte.tools.ff.FfConstants
import love.forte.tools.ff.FfNamingVersion
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
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
    val linkConcurrency: Int = FfConstants.DEFAULT_CONCURRENCY,
)

class FfFlattenProgressStates(
    expectedTotalFiles: MutableStateFlow<Int> = MutableStateFlow(0),
    completedFiles: MutableStateFlow<Int> = MutableStateFlow(0),
    createdLinks: MutableStateFlow<Int> = MutableStateFlow(0),
    skippedExisting: MutableStateFlow<Int> = MutableStateFlow(0),
    failedLinks: MutableStateFlow<Int> = MutableStateFlow(0),
    currentFile: MutableStateFlow<String?> = MutableStateFlow(null)
) {
    val expectedTotalFiles: StateFlow<Int>
        field: MutableStateFlow<Int> = expectedTotalFiles
    val completedFiles: StateFlow<Int>
        field: MutableStateFlow<Int> = completedFiles
    val createdLinks: StateFlow<Int>
        field: MutableStateFlow<Int> = createdLinks
    val skippedExisting: StateFlow<Int>
        field: MutableStateFlow<Int> = skippedExisting
    val failedLinks: StateFlow<Int>
        field: MutableStateFlow<Int> = failedLinks
    val currentFile: StateFlow<String?>
        field: MutableStateFlow<String?> = currentFile

    fun expectedTotalFilesInc(): Int = expectedTotalFiles.incrementAndGet()
    fun completedFilesInc(): Int = completedFiles.incrementAndGet()
    fun createdLinksInc(): Int = createdLinks.incrementAndGet()
    fun skippedExistingInc(): Int = skippedExisting.incrementAndGet()
    fun failedLinksInc(): Int = failedLinks.incrementAndGet()
    fun currentFileSet(file: String?) {
        currentFile.value = file
    }
}

class FfFlattenProgressCollectedStates(
    val expectedTotalFiles: IntState,
    val completedFiles: IntState,
    val createdLinks: IntState,
    val skippedExisting: IntState,
    val failedLinks: IntState,
    val currentFile: State<String?>,
)

@Composable
fun FfFlattenProgressStates.collectAsStates(): FfFlattenProgressCollectedStates {
    return FfFlattenProgressCollectedStates(
        expectedTotalFiles = expectedTotalFiles.collectAsState().asIntState(),
        completedFiles = completedFiles.collectAsState().asIntState(),
        createdLinks = createdLinks.collectAsState().asIntState(),
        skippedExisting = skippedExisting.collectAsState().asIntState(),
        failedLinks = failedLinks.collectAsState().asIntState(),
        currentFile = currentFile.collectAsState(),
    )
}

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
    ): FfFlattenReport {

        val sources = normalizeSources(config.sources)
        if (sources.isEmpty()) throw IllegalArgumentException("sources 不能为空")

        val validation = FfTargetValidator.validate(config.targetDir)
        if (validation == FfTargetValidation.NotAllowedNotDirectory || validation == FfTargetValidation.NotAllowedNotEmpty) {
            throw IllegalArgumentException("目标目录不合法：$validation")
        }

        val now = System.currentTimeMillis()
        val existingMarker = FfMarkerFile.read(config.targetDir)
        val createdAt = existingMarker?.createdAtEpochMillis?.takeIf { it > 0 } ?: now
        val namingVersion = existingMarker?.namingVersion ?: FfConstants.NAMING_VERSION
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

        val completed = MutableStateFlow(0)
        val created = MutableStateFlow(0)
        val skipped = MutableStateFlow(0)
        val failed = MutableStateFlow(0)
        val errors = ConcurrentLinkedQueue<String>()
        val currentFile = AtomicReference<String?>(null)

        val tasks = buildLinkTasks(
            sources = sources,
            targetDir = config.targetDir,
            namingVersion = namingVersion,
        )
        val expectedTotalFiles = (config.expectedTotalFiles ?: tasks.size).coerceAtLeast(0)
        val progressEmitter =
            ProgressEmitter(expectedTotalFiles, completed, created, skipped, failed, currentFile, onProgress)

        progressEmitter.tryEmit()

        val concurrency = config.linkConcurrency.coerceIn(1, FfConstants.MAX_LINK_CONCURRENCY)
        val limitedDispatcher = ioDispatcher.limitedParallelism(concurrency)

        val startedAt = now
        coroutineScope {
            for (task in tasks) {
                launchWorker(
                    limitedDispatcher,
                    task,
                    completed,
                    created,
                    skipped,
                    failed,
                    errors,
                    currentFile,
                    progressEmitter
                )
                progressEmitter.tryEmit()
            }
        }

        val finishedAt = System.currentTimeMillis()
        progressEmitter.forceEmit()

        val finalMarker = markerConfig.copy(updatedAtEpochMillis = finishedAt)
        FfMarkerFile.write(config.targetDir, finalMarker)

        return FfFlattenReport(
            startedAtEpochMillis = startedAt,
            finishedAtEpochMillis = finishedAt,
            expectedTotalFiles = expectedTotalFiles,
            completedFiles = completed.value,
            createdLinks = created.value,
            skippedExisting = skipped.value,
            failedLinks = failed.value,
            errors = errors.toList(),
        )
    }

    private fun CoroutineScope.launchWorker(
        dispatcher: CoroutineDispatcher,
        task: LinkTask,
        completed: MutableStateFlow<Int>,
        created: MutableStateFlow<Int>,
        skipped: MutableStateFlow<Int>,
        failed: MutableStateFlow<Int>,
        errors: MutableCollection<String>,
        currentFile: AtomicReference<String?>,
        progressEmitter: ProgressEmitter,
    ): Job = launch(dispatcher) {
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
            errors.add("${task.existing} -> ${task.link} : ${e.message.orEmpty()}")
        }

        progressEmitter.tryEmit()
    }

    private data class LinkTask(
        val existing: Path,
        val link: Path,
    )

    private class ProgressEmitter(
        private val expectedTotalFiles: Int,
        private val completed: MutableStateFlow<Int>,
        private val created: MutableStateFlow<Int>,
        private val skipped: MutableStateFlow<Int>,
        private val failed: MutableStateFlow<Int>,
        private val currentFile: AtomicReference<String?>,
        private val onProgress: (FfFlattenProgress) -> Unit,
    ) {
        private val lastEmitAt = AtomicLong(0)

        fun tryEmit() {
            val now = System.currentTimeMillis()
            val last = lastEmitAt.get()
            if (now - last < FfConstants.PROGRESS_EMIT_INTERVAL_MS) {
                return
            }
            if (lastEmitAt.compareAndSet(last, now)) {
                emit()
            }
        }

        fun forceEmit() {
            lastEmitAt.set(System.currentTimeMillis())
            emit()
        }

        private fun emit() {
            onProgress(
                FfFlattenProgress(
                    expectedTotalFiles = expectedTotalFiles,
                    completedFiles = completed.value,
                    createdLinks = created.value,
                    skippedExisting = skipped.value,
                    failedLinks = failed.value,
                    currentFile = currentFile.get(),
                ),
            )
        }
    }

    /**
     * 根据路径和扩展规范化源配置
     */
    private fun normalizeSources(raw: List<FfFlattenSourceConfig>): List<FfFlattenSourceConfig> {
        return raw
            .asSequence()
            .map { it.copy(sourceDir = it.sourceDir.toAbsolutePath().normalize()) }
            .filter { it.selectedExtensions.isNotEmpty() }
            .groupByTo(linkedMapOf()) { it.sourceDir.absolutePathString() }
            .mapNotNull { (_, items) ->
                val sourceDir = items.firstOrNull()?.sourceDir ?: return@mapNotNull null
                val selected = items.flatMapTo(linkedSetOf()) { it.selectedExtensions.asSequence() }
                if (selected.isEmpty()) return@mapNotNull null
                FfFlattenSourceConfig(sourceDir = sourceDir, selectedExtensions = selected)
            }
            .toList()
    }

    private fun buildLinkTasks(
        sources: List<FfFlattenSourceConfig>,
        targetDir: Path,
        namingVersion: FfNamingVersion,
    ): List<LinkTask> {
        return when (namingVersion) {
            FfNamingVersion.V1 -> buildLinkTasksV1(sources, targetDir)
            FfNamingVersion.V2 -> buildLinkTasksV2(sources, targetDir)
            FfNamingVersion.V3 -> buildLinkTasksV3(sources, targetDir)
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

    private fun buildLinkTasksV3(
        sources: List<FfFlattenSourceConfig>,
        targetDir: Path,
    ): List<LinkTask> {
        // TODO
        val candidates = mutableListOf<Candidate>()
        val prefixSources = linkedMapOf<String, MutableSet<Path>>()

        for (source in sources) {
            FfDirectoryScanner.forEachMatchingFile(source.sourceDir, source.selectedExtensions) { file ->
                val prefixKey = FfFlattenNaming.prefixKeyV3(source.sourceDir, file)
                candidates.add(
                    Candidate(
                        sourceRoot = source.sourceDir,
                        file = file,
                        prefixKey = prefixKey,
                        fileName = file.fileName?.toString().orEmpty(),
                    ),
                )

                prefixSources.computeIfAbsent(prefixKey) { linkedSetOf() }.add(source.sourceDir)
            }
        }

        val suffixIndex = LinkedHashMap<PrefixSourceKey, Int>(candidates.size)
        for ((prefixKey, roots) in prefixSources) {
            val orderedRoots = roots.sortedBy { it.absolutePathString() }
            orderedRoots.forEachIndexed { idx, root ->
                suffixIndex[PrefixSourceKey(prefixKey, root)] = if (idx == 0) 0 else idx
            }
        }

        return candidates.map { candidate ->
            val suffix = suffixIndex[PrefixSourceKey(candidate.prefixKey, candidate.sourceRoot)] ?: 0
            val fileName = FfFlattenNaming.outputFileNameV3(candidate.prefixKey, suffix, candidate.fileName)
            LinkTask(existing = candidate.file, link = targetDir.resolve(fileName))
        }
    }
}

internal fun MutableStateFlow<Int>.incrementAndGet(): Int = updateAndGet { it + 1 }