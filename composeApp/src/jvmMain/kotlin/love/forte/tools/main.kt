package love.forte.tools

import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import love.forte.tools.ff.di.FfKoin
import java.util.Locale

fun main(args: Array<String>) {
    FfKoin.start()
    nucleusApplication(
        args = args,
        backend = NucleusBackend.Tao,
        defaultLocale = Locale.getDefault(Locale.Category.DISPLAY),
    ) {
        val onExit = {
            FfKoin.stop()
            exitApplication()
        }
        FfWindow(onExit = onExit)
    }
}
