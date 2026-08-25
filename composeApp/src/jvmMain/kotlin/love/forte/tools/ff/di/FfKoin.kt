package love.forte.tools.ff.di

import org.koin.core.KoinApplication
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.plugin.module.dsl.startKoin

object FfKoin {
    fun start(): KoinApplication {
        return startKoin<FfApplication> {
            logger(PrintLogger(Level.INFO))
        }
    }

    fun stop() {
        stopKoin()
    }
}
