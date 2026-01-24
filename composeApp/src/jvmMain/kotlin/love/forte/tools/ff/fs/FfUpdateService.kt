package love.forte.tools.ff.fs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import love.forte.tools.ff.FfConstants
import org.koin.core.annotation.Single
import java.awt.Desktop
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name

/**
 * 更新操作的结果
 */
sealed interface FfUpdateResult {
    data class Success(val report: FfFlattenReport) : FfUpdateResult
    data class Failed(val message: String) : FfUpdateResult
    data class TrashFailed(val tempDir: Path, val message: String) : FfUpdateResult
}

/**
 * 临时文件夹清理选项
 */
enum class FfTempCleanupChoice {
    DeleteDirectly,
    KeepForManualHandling,
}

/**
 * 更新服务：安全地更新受管目录
 */
@Single
class FfUpdateService(
    private val flattenService: FfFlattenService,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * 执行更新操作
     *
     * 流程：
     * 1. 锁定 .ff 文件
     * 2. 将匹配扩展名的文件移动到临时目录
     * 3. 重新执行 flatten
     * 4. 成功后将临时目录移动到回收站
     */
    suspend fun update(
        targetDir: Path,
        linkConcurrency: Int,
        ffFlattenProgressStates: FfFlattenProgressStates,
        onProgress: (FfFlattenProgress) -> Unit = {},
    ): FfUpdateResult = withContext(ioDispatcher) {
        val markerPath = FfMarkerFile.markerPath(targetDir)
        if (!markerPath.exists() || !markerPath.isRegularFile()) {
            return@withContext FfUpdateResult.Failed(".ff 配置文件不存在")
        }

        val marker = FfMarkerFile.read(targetDir)
            ?: return@withContext FfUpdateResult.Failed(".ff 配置读取失败")

        // 收集所有配置的扩展名
        val allExtensions = marker.sources
            .flatMap { it.extensions }
            .toSet()

        if (allExtensions.isEmpty()) {
            return@withContext FfUpdateResult.Failed("未配置任何扩展名")
        }

        // 使用文件锁保护更新过程
        try {
            RandomAccessFile(markerPath.toFile(), "rw").use { raf ->
                val lock: FileLock? = raf.channel.tryLock()
                if (lock == null) {
                    return@withContext FfUpdateResult.Failed("无法锁定 .ff 文件，可能正在被其他操作使用")
                }

                try {
                    performUpdate(targetDir, marker, allExtensions, linkConcurrency, ffFlattenProgressStates, onProgress)
                } finally {
                    lock.release()
                }
            }
        } catch (e: Exception) {
            FfUpdateResult.Failed("更新失败：${e.message}")
        }
    }

    private suspend fun performUpdate(
        targetDir: Path,
        marker: FfMarkerConfig,
        allExtensions: Set<String>,
        linkConcurrency: Int,
        progressStates: FfFlattenProgressStates,
        onProgress: (FfFlattenProgress) -> Unit,
    ): FfUpdateResult {
        // 生成临时目录名
        val tempDirName = ".update_tmp_${UUID.randomUUID().toString().take(8)}"
        val tempDir = targetDir.resolve(tempDirName)

        // 步骤1：移动匹配文件到临时目录
        val moveResult = moveFilesToTemp(targetDir, tempDir, allExtensions)
        if (moveResult != null) {
            // 移动失败，尝试恢复
            restoreFromTemp(tempDir, targetDir)
            return FfUpdateResult.Failed("移动文件到临时目录失败：$moveResult")
        }

        // 步骤2：重新执行 flatten
        val sources = marker.sources.mapNotNull { src ->
            val path = runCatching { Path.of(src.path) }.getOrNull() ?: return@mapNotNull null
            val exts = src.extensions.toSet()
            if (exts.isEmpty()) return@mapNotNull null
            FfFlattenSourceConfig(sourceDir = path, selectedExtensions = exts)
        }

        if (sources.isEmpty()) {
            // flatten 配置无效，恢复文件
            restoreFromTemp(tempDir, targetDir)
            return FfUpdateResult.Failed("源目录配置无效")
        }

        val flattenResult = runCatching {
            flattenService.flatten(
                config = FfFlattenTaskConfig(
                    targetDir = targetDir,
                    sources = sources,
                    expectedTotalFiles = null,
                    linkConcurrency = linkConcurrency,
                ),
                onProgress = onProgress,
            )
        }

        return flattenResult.fold(
            onSuccess = { report ->
                if (report.failedLinks > 0) {
                    // 有失败的链接，保留临时目录供用户检查
                    FfUpdateResult.TrashFailed(
                        tempDir = tempDir,
                        message = "更新完成但有 ${report.failedLinks} 个文件链接失败，临时目录已保留"
                    )
                } else {
                    // 成功，尝试移动临时目录到回收站
                    val trashResult = moveToTrash(tempDir)
                    if (trashResult) {
                        FfUpdateResult.Success(report)
                    } else {
                        FfUpdateResult.TrashFailed(
                            tempDir = tempDir,
                            message = "更新成功，但无法将临时目录移动到回收站"
                        )
                    }
                }
            },
            onFailure = { e ->
                // flatten 失败，恢复文件
                restoreFromTemp(tempDir, targetDir)
                FfUpdateResult.Failed("重新 flatten 失败：${e.message}")
            }
        )
    }

    /**
     * 将目标目录中匹配扩展名的文件移动到临时目录（非递归）
     * @return 错误信息，null 表示成功
     */
    private fun moveFilesToTemp(
        targetDir: Path,
        tempDir: Path,
        extensions: Set<String>,
    ): String? {
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            return "目标目录不存在"
        }

        // 创建临时目录
        runCatching { Files.createDirectories(tempDir) }
            .onFailure { return "无法创建临时目录：${it.message}" }

        // 获取目标目录下的所有文件（非递归）
        val files = runCatching { targetDir.listDirectoryEntries() }
            .getOrElse { return "无法读取目标目录：${it.message}" }

        for (file in files) {
            // 跳过目录、.ff 文件和临时目录本身
            if (file.isDirectory()) continue
            if (file.name == FfConstants.MARKER_FILE_NAME) continue

            // 检查扩展名是否匹配
            val ext = FfDirectoryScanner.extensionKeyOf(file)
            if (ext !in extensions) continue

            // 移动文件
            val destFile = tempDir.resolve(file.name)
            runCatching {
                file.moveTo(destFile, StandardCopyOption.ATOMIC_MOVE)
            }.onFailure {
                return "无法移动文件 ${file.name}：${it.message}"
            }
        }

        return null
    }

    /**
     * 从临时目录恢复文件到目标目录
     */
    private fun restoreFromTemp(tempDir: Path, targetDir: Path) {
        if (!tempDir.exists() || !tempDir.isDirectory()) return

        runCatching {
            tempDir.listDirectoryEntries().forEach { file ->
                if (file.isRegularFile()) {
                    val destFile = targetDir.resolve(file.name)
                    file.moveTo(destFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            // 删除临时目录
            tempDir.deleteIfExists()
        }
    }

    /**
     * 将目录移动到回收站
     * @return true 表示成功
     */
    private fun moveToTrash(dir: Path): Boolean {
        if (!dir.exists()) return true

        // 尝试使用系统回收站
        return if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            runCatching {
                Desktop.getDesktop().moveToTrash(dir.toFile())
            }.getOrDefault(false)
        } else {
            false
        }
    }

    /**
     * 根据用户选择清理临时目录
     */
    suspend fun cleanupTempDir(
        tempDir: Path,
        choice: FfTempCleanupChoice,
    ): Boolean = withContext(ioDispatcher) {
        when (choice) {
            FfTempCleanupChoice.DeleteDirectly -> {
                runCatching {
                    deleteDirectoryRecursively(tempDir)
                    true
                }.getOrDefault(false)
            }
            FfTempCleanupChoice.KeepForManualHandling -> true
        }
    }

    private fun deleteDirectoryRecursively(dir: Path) {
        if (!dir.exists()) return
        if (dir.isDirectory()) {
            dir.listDirectoryEntries().forEach { entry ->
                if (entry.isDirectory()) {
                    deleteDirectoryRecursively(entry)
                } else {
                    entry.deleteIfExists()
                }
            }
        }
        dir.deleteIfExists()
    }
}
