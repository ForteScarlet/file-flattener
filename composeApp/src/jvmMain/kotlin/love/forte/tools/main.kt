package love.forte.tools

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import love.forte.tools.ff.FfBuildConfig
import love.forte.tools.ff.di.FfKoin
import love.forte.tools.file_flattener.composeapp.generated.resources.Res
import love.forte.tools.file_flattener.composeapp.generated.resources.icon
import org.jetbrains.compose.resources.painterResource
import java.util.Locale

fun main() {
    FfKoin.start()
    application {
        val onExit = {
            FfKoin.stop()
            exitApplication()
        }

        Window(
            onCloseRequest = onExit,
            title = "File Flattener",
            icon = painterResource(Res.drawable.icon)
        ) {
            this.window.locale = Locale.getDefault(Locale.Category.DISPLAY)
            App(onExit = onExit)
        }
    }
}
