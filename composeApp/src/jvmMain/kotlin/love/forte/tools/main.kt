package love.forte.tools

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import love.forte.tools.ff.FfBuildConfig
import love.forte.tools.file_flattener.composeapp.generated.resources.Res
import love.forte.tools.file_flattener.composeapp.generated.resources.icon
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "file-flattener v${FfBuildConfig.VERSION}",
        icon = painterResource(Res.drawable.icon)
    ) {
        App(onExit = ::exitApplication)
    }
}
