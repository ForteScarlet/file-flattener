package love.forte.tools

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import love.forte.tools.ff.ui.platform.FfDropEvents
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "file-flattener",
    ) {
        val awtWindow = window
        DisposableEffect(awtWindow) {
            val dropTarget = object : DropTarget() {
                override fun drop(dtde: DropTargetDropEvent) {
                    runCatching {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        val files = dtde.transferable
                            .getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                        val directories = files
                            .orEmpty()
                            .filterIsInstance<java.io.File>()
                            .asSequence()
                            .filter { it.isDirectory }
                            .map { it.toPath() }
                            .toList()
                        FfDropEvents.emitDirectories(directories)
                    }
                    dtde.dropComplete(true)
                }
            }
            awtWindow.dropTarget = dropTarget
            onDispose {
                awtWindow.dropTarget = null
            }
        }
        App()
    }
}
