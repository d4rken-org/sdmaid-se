package eu.darken.sdmse.common.shizuku.service

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import eu.darken.sdmse.common.coroutine.CoroutineModule
import javax.inject.Singleton

/**
 * Injected into java process run by Shizuku, see [ShizukuHost]
 */
@Singleton
@Component(
    modules = [
        ShizukuModule::class,
        CoroutineModule::class
    ]
)
interface ShizukuComponent {

    fun inject(main: ShizukuHost)

    @Component.Builder
    interface Builder {
        fun build(): ShizukuComponent

        @BindsInstance fun application(context: Context): Builder
    }

}