package love.forte.tools.ff.fs

import love.forte.tools.ff.FfConstants
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.invariantSeparatorsPathString

object FfFlattenNaming {
    private const val HashAlgorithm: String = "SHA-256"
    private const val HashHexChars: Int = 10
    private const val MaxBaseLength: Int = 140
    private const val Separator: String = "__"
    private const val V2PathSeparator: String = "-"

    /**
     * 命名规则 v1：相对路径 + 短哈希，稳定且天然去重。
     */
    fun outputFileNameV1(sourceRoot: Path, file: Path): String {
        val relative = sourceRoot.relativize(file).invariantSeparatorsPathString
        val extKey = FfDirectoryScanner.extensionKeyOf(file)
        val base = sanitize(stripExtension(relative))
        val trimmedBase = base.take(MaxBaseLength)
        val id = shortHash("${sourceRoot.toAbsolutePath().normalize()}|$relative")
        val extSuffix = extSuffix(extKey)
        return "$trimmedBase$Separator$id$extSuffix"
    }

    /**
     * 命名规则 v2：
     * - 源根目录到目标文件之间的层级使用 "-" 拼接；
     * - 再拼接文件名（含扩展名）。
     *
     * 若同一目标目录下出现“前缀（层级部分）”冲突，则通过 suffixIndex 生成 `prefix-<n>` 变体。
     * - suffixIndex == 0：不加后缀
     * - suffixIndex > 0：追加 `-<suffixIndex>`
     */
    fun outputFileNameV2(prefixKey: String, suffixIndex: Int, fileName: String): String {
        val safePrefix = if (prefixKey.isBlank()) "" else sanitizeV2Token(prefixKey)
        val safeName = sanitizeV2Token(fileName)

        val normalizedSuffix = suffixIndex.coerceAtLeast(0)
        val prefixWithSuffix = when {
            safePrefix.isBlank() -> if (normalizedSuffix == 0) "" else normalizedSuffix.toString()
            normalizedSuffix == 0 -> safePrefix
            else -> "$safePrefix$V2PathSeparator$normalizedSuffix"
        }

        return if (prefixWithSuffix.isBlank()) {
            safeName
        } else {
            "$prefixWithSuffix$V2PathSeparator$safeName"
        }
    }

    /**
     * v2 前缀（层级部分）：
     * - file 位于 sourceRoot 下；
     * - 取相对路径的 parent（没有则为空）；
     * - 将每一层级进行安全化后用 "-" 拼接。
     */
    fun prefixKeyV2(sourceRoot: Path, file: Path): String {
        val relative = sourceRoot.relativize(file)
        val parent = relative.parent ?: return ""
        val parts = parent.invariantSeparatorsPathString
            .split('/')
            .filter { it.isNotBlank() }
            .map(::sanitizeV2Token)
            .filter { it.isNotBlank() }
        return parts.joinToString(V2PathSeparator)
    }

    private fun extSuffix(extKey: String): String =
        if (extKey == FfConstants.ExtensionNone) "" else ".$extKey"

    private fun stripExtension(pathLike: String): String {
        val lastSlash = pathLike.lastIndexOf('/')
        val fileNameStart = if (lastSlash >= 0) lastSlash + 1 else 0
        val lastDot = pathLike.lastIndexOf('.')
        val isDotInFileName = lastDot >= fileNameStart
        return if (isDotInFileName) pathLike.substring(0, lastDot) else pathLike
    }

    private fun sanitize(raw: String): String {
        return raw
            .replace("/", Separator)
            .replace("\\", Separator)
            .replace(":", "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^0-9A-Za-z_\\-\\.]+"), "_")
            .trim('_')
            .ifEmpty { "file" }
    }

    private fun sanitizeV2Token(raw: String): String {
        return raw
            .replace("/", V2PathSeparator)
            .replace("\\", V2PathSeparator)
            .replace(":", "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^0-9A-Za-z_\\-\\.]+"), "_")
            .trim('_', '-')
            .ifEmpty { "file" }
    }

    private fun shortHash(text: String): String {
        val digest = MessageDigest.getInstance(HashAlgorithm)
            .digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.toHex().take(HashHexChars)
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (b in this@toHex) {
            append(((b.toInt() shr 4) and 0xF).toString(16))
            append((b.toInt() and 0xF).toString(16))
        }
    }
}
