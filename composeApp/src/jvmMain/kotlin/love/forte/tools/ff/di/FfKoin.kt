package love.forte.tools.ff.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.ksp.generated.*

object FfKoin {
    fun start(): KoinApplication {
        return startKoin {
            logger(PrintLogger(Level.INFO))
            modules(FfCoreModule().module)
        }
    }

    fun stop() {
        stopKoin()
    }
}
