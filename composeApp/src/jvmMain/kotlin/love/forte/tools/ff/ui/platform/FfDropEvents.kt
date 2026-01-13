package love.forte.tools.ff.ui.platform

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.file.Path

object FfDropEvents {
    private val _droppedDirectories = MutableSharedFlow<List<Path>>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val droppedDirectories = _droppedDirectories.asSharedFlow()

    fun emitDirectories(directories: List<Path>) {
        if (directories.isEmpty()) return
        _droppedDirectories.tryEmit(directories)
    }
}

