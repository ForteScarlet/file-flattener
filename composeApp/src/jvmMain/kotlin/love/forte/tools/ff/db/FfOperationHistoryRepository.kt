package love.forte.tools.ff.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import love.forte.tools.ff.fs.FfFlattenReport
import love.forte.tools.ff.db.migrations.Operation_history
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * 操作历史数据模型。
 */
data class FfOperationRecord(
    val id: Long,
    val targetPath: String,
    val operationType: FfOperationType,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: FfOperationStatus,
    val filesTotal: Int?,
    val filesProcessed: Int?,
    val filesCreated: Int?,
    val filesSkipped: Int?,
    val filesFailed: Int?,
    val errorMessage: String?,
)

/**
 * 操作历史数据访问层。
 */
class FfOperationHistoryRepository(
    private val database: FfDatabase,
) {
    companion object {
        /** 默认保留的历史记录数量 */
        const val DEFAULT_HISTORY_LIMIT = 100

        /** 默认查询限制 */
        const val DEFAULT_QUERY_LIMIT = 50L
    }

    /**
     * 获取最近的操作历史。
     */
    suspend fun getRecentHistory(limit: Long = DEFAULT_QUERY_LIMIT): List<FfOperationRecord> =
        withContext(Dispatchers.IO) {
            database.ffDatabaseQueries.selectRecentHistory(limit)
                .executeAsList()
                .map(::toRecord)
        }

    /**
     * 获取指定目标目录的操作历史。
     */
    suspend fun getHistoryByTarget(
        targetPath: Path,
        limit: Long = DEFAULT_QUERY_LIMIT
    ): List<FfOperationRecord> = withContext(Dispatchers.IO) {
        database.ffDatabaseQueries.selectHistoryByTarget(
            target_path = targetPath.absolutePathString(),
            value_ = limit
        ).executeAsList().map(::toRecord)
    }

    /**
     * 开始新的操作记录。
     *
     * @return 新记录的 ID
     */
    suspend fun startOperation(
        targetPath: Path,
        operationType: FfOperationType
    ): Long = withContext(Dispatchers.IO) {
        database.transactionWithResult {
            database.ffDatabaseQueries.insertHistory(
                target_path = targetPath.absolutePathString(),
                operation_type = operationType.name,
                started_at = System.currentTimeMillis()
            )
            database.ffDatabaseQueries.lastInsertRowId().executeAsOne()
        }
    }

    /**
     * 完成操作记录。
     */
    suspend fun finishOperation(
        id: Long,
        status: FfOperationStatus,
        report: FfFlattenReport? = null,
        errorMessage: String? = null
    ) = withContext(Dispatchers.IO) {
        database.ffDatabaseQueries.updateHistoryStatus(
            status = status.name,
            finished_at = System.currentTimeMillis(),
            files_total = report?.expectedTotalFiles?.toLong(),
            files_processed = report?.completedFiles?.toLong(),
            files_created = report?.createdLinks?.toLong(),
            files_skipped = report?.skippedExisting?.toLong(),
            files_failed = report?.failedLinks?.toLong(),
            error_message = errorMessage ?: report?.errors?.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            id = id
        )
    }

    /**
     * 获取运行中的操作。
     */
    suspend fun getRunningOperations(): List<FfOperationRecord> = withContext(Dispatchers.IO) {
        database.ffDatabaseQueries.selectRunningHistory()
            .executeAsList()
            .map(::toRecord)
    }

    /**
     * 清理旧的历史记录，保留最近的 N 条。
     */
    suspend fun cleanupOldHistory(keepCount: Long = DEFAULT_HISTORY_LIMIT.toLong()) =
        withContext(Dispatchers.IO) {
            database.ffDatabaseQueries.cleanupOldHistory(keepCount)
        }

    private fun toRecord(row: Operation_history): FfOperationRecord {
        return FfOperationRecord(
            id = row.id,
            targetPath = row.target_path,
            operationType = parseOperationType(row.operation_type),
            startedAt = row.started_at,
            finishedAt = row.finished_at,
            status = parseStatus(row.status),
            filesTotal = row.files_total?.toInt(),
            filesProcessed = row.files_processed?.toInt(),
            filesCreated = row.files_created?.toInt(),
            filesSkipped = row.files_skipped?.toInt(),
            filesFailed = row.files_failed?.toInt(),
            errorMessage = row.error_message,
        )
    }

    private fun parseOperationType(raw: String): FfOperationType =
        runCatching { enumValueOf<FfOperationType>(raw) }.getOrDefault(FfOperationType.FLATTEN)

    private fun parseStatus(raw: String): FfOperationStatus =
        runCatching { enumValueOf<FfOperationStatus>(raw) }.getOrDefault(FfOperationStatus.RUNNING)
}
