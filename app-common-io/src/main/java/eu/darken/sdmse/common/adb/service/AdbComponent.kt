package eu.darken.sdmse.common.adb.service

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import eu.darken.sdmse.common.coroutine.CoroutineModule
import javax.inject.Singleton

/**
 * Injected into java process run by Shizuku, see [AdbHost]
 */
@Singleton
@Component(
    modules = [
        AdbModule::class,
        CoroutineModule::class
    ]
)
interface AdbComponent {

    fun inject(main: AdbHost)

    @Component.Builder
    interface Builder {
        fun build(): AdbComponent

        @BindsInstance fun application(context: Context): Builder
    }

}