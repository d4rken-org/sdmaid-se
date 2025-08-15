package eu.darken.sdmse.common.cache

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("UsableSpace")
@Singleton
class CacheRepo @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val toplevelDir = context.cacheDir

    val baseCacheDir: File
        get() = File(toplevelDir, "repo").also {
            if (!it.exists()) it.mkdirs()
            log(TAG, VERBOSE) { "usableSpace in $it is ${it.usableSpace}" }
        }


    suspend fun canSpare(size: Long): Boolean {
        val usable = toplevelDir.usableSpace
        val remaining = usable - size
        return remaining > THRESHOLD
    }

    companion object {
        private const val THRESHOLD = 1024 * 1024 * 500L // 500MB
        private val TAG = logTag("Cache", "Repo")
    }
}