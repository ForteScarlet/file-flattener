package love.forte.tools

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import love.forte.tools.ff.FfBuildConfig

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "file-flattener v${FfBuildConfig.VERSION}",
    ) {
        App()
    }
}
