package love.forte.tools.ff

/**
 * 约定与协议常量集中管理，避免散落的“魔法字符串/数字”。
 */
object FfConstants {
    const val MarkerFileName: String = ".ff"

    /**
     * 用于表示“无扩展名”的内部标识；写入 .ff 时也使用该值。
     */
    const val ExtensionNone: String = "<none>"

    /**
     * 当前 .ff 内容格式版本（可演进）。
     */
    const val MarkerSchemaVersion: Int = 2

    /**
     * 当前扁平化命名算法版本（可演进）。
     */
    const val NamingVersion: Int = 2

    /**
     * 默认并发度。
     */
    const val DefaultConcurrency: Int = 8

    /**
     * 并发上限：避免 UI 层/配置层误设置导致过量线程竞争与磁盘风暴。
     */
    const val MaxLinkConcurrency: Int = 64

    /**
     * 进度上报节流：降低 UI 状态更新频率，避免频繁重组导致卡顿。
     */
    const val ProgressEmitIntervalMs: Long = 200
}
