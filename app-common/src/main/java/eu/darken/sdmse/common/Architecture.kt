package eu.darken.sdmse.common

import android.os.Build
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Architecture @Inject constructor() {

    val folderNames: Collection<String>
        get() = when (mainType) {
            Type.X86 -> listOf("x86", "x64")
            else -> listOf("arm", "arm64")
        }

    @Suppress("DEPRECATION")
    val architectures: List<Type> by lazy {
        log(TAG) { "Architectures: Build.CPU_ABI=${Build.CPU_ABI}, Build.CPU_ABI2=${Build.CPU_ABI2}" }
        log(TAG) { "Architectures: Build.SUPPORTED_ABIS=${Build.SUPPORTED_ABIS.toList()}" }
        log(TAG) { "Architectures: Build.SUPPORTED_32_BIT_ABIS=${Build.SUPPORTED_32_BIT_ABIS.toList()}" }
        log(TAG) { "Architectures: Build.SUPPORTED_64_BIT_ABIS=${Build.SUPPORTED_64_BIT_ABIS.toList()}" }

        val rawMain: String = Build.SUPPORTED_ABIS[0].lowercase()
        val detectedMain: Type = if (rawMain.contains("x86")) Type.X86 else Type.ARM

        mutableListOf(*Type.values()).apply {
            remove(detectedMain)
            add(0, detectedMain)
            log(TAG) { "Preferred architecture: raw=$rawMain, detected=$detectedMain. Order: $this." }
        }
    }

    val mainType: Type
        get() = architectures[0]

    enum class Type {
        X86, ARM
    }

    companion object {
        private val TAG = logTag("Architecture")
    }
}