package love.forte.tools.ff.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import love.forte.tools.ff.db.FfDatabaseManager
import love.forte.tools.ff.storage.FfAppPaths
import love.forte.tools.ff.storage.FfBootstrapStore
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

object FfDiPackages {
    const val BASE: String = "love.forte.tools.ff"
}

@Module
@ComponentScan(FfDiPackages.BASE)
class FfCoreModule {
    @Single
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Single
    fun provideDatabaseManager(): FfDatabaseManager = FfDatabaseManager.getInstance()

    @Single
    fun provideBootstrapStore(): FfBootstrapStore = FfBootstrapStore(FfAppPaths.defaultAppDir())
}
