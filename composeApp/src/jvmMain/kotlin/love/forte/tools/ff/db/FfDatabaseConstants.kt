package love.forte.tools.ff.db

/**
 * 数据库元数据键常量。
 */
object FfDatabaseMetaKeys {
    /** 数据库创建时间戳 */
    const val CREATED_AT = "created_at"
}

/**
 * 设置键常量。
 */
object FfSettingsKeys {
    /** 主题设置 */
    const val THEME = "theme"

    /** 并发限制 */
    const val CONCURRENCY_LIMIT = "concurrency_limit"
}

/**
 * 操作类型枚举。
 */
enum class FfOperationType {
    /** 扁平化操作 */
    FLATTEN,

    /** 同步操作 */
    SYNC,

    /** 删除操作 */
    DELETE
}

/**
 * 操作状态枚举。
 */
enum class FfOperationStatus {
    /** 运行中 */
    RUNNING,

    /** 成功完成 */
    SUCCESS,

    /** 失败 */
    FAILED,

    /** 已取消 */
    CANCELLED
}
