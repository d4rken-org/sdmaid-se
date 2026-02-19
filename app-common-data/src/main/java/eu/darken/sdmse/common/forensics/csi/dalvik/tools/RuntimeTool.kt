package eu.darken.sdmse.common.forensics.csi.dalvik.tools

import android.annotation.SuppressLint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeTool @Inject constructor() {

    private val mutex = Mutex()
    private var _cache: Info? = null

    suspend fun getRuntimeInfo(): Info = mutex.withLock {
        _cache?.let { return@withLock it }

        val rawType = getRawType()
        val type = when {
            LIB_DALVIK == rawType -> Info.Type.DALVIK
            LIB_ART == rawType -> Info.Type.ART
            LIB_ART_D == rawType -> Info.Type.ART
            rawType.startsWith("1") -> Info.Type.DALVIK
            rawType.startsWith("2") -> Info.Type.ART
            else -> Info.Type.UNKNOWN
        }
        return Info(type, rawType).also {
            _cache = it
        }
    }

    @SuppressLint("PrivateApi")
    private fun getRawType(): String {
        var ret = UNKNOWN

        val vmVersion = System.getProperty("java.vm.version")
        if (vmVersion != null) {
            ret = vmVersion
        }

        if (UNKNOWN == ret) {
            try {
                val systemProperties = Class.forName("android.os.SystemProperties")
                val get: Method? = systemProperties.getMethod("get", String::class.java, String::class.java)
                if (get != null) ret = get.invoke(systemProperties, SELECT_RUNTIME_PROPERTY, UNKNOWN) as String
            } catch (ignore: Exception) {
            }
        }
        return ret
    }

    data class Info constructor(
        val type: Type,
        val raw: String
    ) {
        enum class Type {
            DALVIK, ART, UNKNOWN
        }
    }

    companion object {
        private const val SELECT_RUNTIME_PROPERTY = "persist.sys.dalvik.vm.lib"
        private const val LIB_DALVIK = "libdvm.so"
        private const val LIB_ART = "libart.so"
        private const val LIB_ART_D = "libartd.so"
        private const val UNKNOWN = "Unknown"
    }
}