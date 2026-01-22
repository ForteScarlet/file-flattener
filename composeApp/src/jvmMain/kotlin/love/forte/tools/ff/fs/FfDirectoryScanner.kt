package love.forte.tools.ff.fs

import love.forte.tools.ff.FfConstants
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

object FfDirectoryScanner {
    /**
     * 扫描目录下所有文件扩展名及数量。
     *
     * 约定：遇到受管目录（存在 .ff）则跳过整个子树，避免把“输出目录”再次作为输入。
     */
    fun scanExtensions(root: Path): Map<String, Int> {
        if (!root.isDirectory()) return emptyMap()
        val counters = ConcurrentHashMap<String, AtomicInteger>()
        walkFiles(root) { file ->
            val ext = extensionKeyOf(file)
            counters.computeIfAbsent(ext) { AtomicInteger(0) }.incrementAndGet()
        }
        return counters.mapValues { it.value.get() }.toSortedMap(extensionComparator())
    }

    /**
     * 遍历并回调匹配的文件路径（不会进入受管目录）。
     */
    fun forEachMatchingFile(
        root: Path,
        selectedExtensions: Set<String>,
        onFile: (Path) -> Unit,
    ) {
        if (!root.isDirectory()) return
        if (selectedExtensions.isEmpty()) return
        walkFiles(root) { file ->
            val ext = extensionKeyOf(file)
            if (ext in selectedExtensions) {
                onFile(file)
            }
        }
    }

    private fun walkFiles(root: Path, onFile: (Path) -> Unit) {
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir != root && FfMarkerFile.isManagedDirectory(dir)) return FileVisitResult.SKIP_SUBTREE
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile && file.isRegularFile()) onFile(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
    }

    fun extensionKeyOf(file: Path): String {
        val name = file.fileName?.toString().orEmpty()
        val idx = name.lastIndexOf('.')
        if (idx <= 0 || idx == name.lastIndex) return FfConstants.EXTENSION_NONE
        return name.substring(idx + 1).lowercase()
    }

    private fun extensionComparator(): Comparator<String> = Comparator { a, b ->
        when {
            a == b -> 0
            a == FfConstants.EXTENSION_NONE -> -1
            b == FfConstants.EXTENSION_NONE -> 1
            else -> a.compareTo(b)
        }
    }
}

