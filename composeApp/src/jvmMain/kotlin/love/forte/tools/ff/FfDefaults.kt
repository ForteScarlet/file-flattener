package love.forte.tools.ff

object FfDefaults {
    fun defaultConcurrencyLimit(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(1, FfConstants.MaxLinkConcurrency)

    fun defaultLinkConcurrencyPerTask(taskConcurrencyLimit: Int): Int {
        val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val perTask = (cpu / taskConcurrencyLimit.coerceAtLeast(1)).coerceAtLeast(1)
        return perTask.coerceIn(1, FfConstants.MaxLinkConcurrency)
    }
}
