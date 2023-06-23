package eu.darken.sdmse.common.root.service

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import eu.darken.sdmse.common.coroutine.CoroutineModule
import javax.inject.Singleton

/**
 * Injected into java process run by root via su shell, see [RootHost]
 */
@Singleton
@Component(
    modules = [
        RootModule::class,
        CoroutineModule::class
    ]
)
interface RootComponent {

    fun inject(main: RootHost)

    @Component.Builder
    interface Builder {
        fun build(): RootComponent

        @BindsInstance fun application(context: Context): Builder
    }

}