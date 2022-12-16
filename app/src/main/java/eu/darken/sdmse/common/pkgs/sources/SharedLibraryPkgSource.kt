package eu.darken.sdmse.common.pkgs.sources

import android.content.pm.PackageManager
import android.content.pm.SharedLibraryInfo
import android.os.Parcel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgDataSource
import eu.darken.sdmse.common.pkgs.container.LibraryPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedLibraryPkgSource @Inject constructor(
    private val pkgOps: PkgOps,
    private val packageManager: PackageManager,
) : PkgDataSource {

    override suspend fun getPkgs(): Collection<Installed> = pkgOps.useSharedResource {
        log(TAG, VERBOSE) { "getPkgs()" }
        val libraryPkgs = pkgOps.getSharedLibraries(0)
            .onEach { log(TAG, VERBOSE) { "Checking $it" } }
            .filter { it.type != 0 } // Built in types like .jars
            .mapNotNull { libraryInfo ->
                val apkPath = libraryInfo.clawOutPath()
                val apkInfo = apkPath?.let { path ->
                    try {
                        pkgOps.viewArchive(path, 0)
                    } catch (e: Exception) {
                        log(TAG) { "PkgInfo lookup on clawed path failed $libraryInfo: ${e.asLog()}" }
                        null
                    }
                } ?: return@mapNotNull null

                LibraryPkg(
                    sharedLibraryInfo = libraryInfo,
                    apkPath = apkPath,
                    packageInfo = apkInfo.packageInfo,
                )
            }
        log(TAG) { "Found ${libraryPkgs.size} library pkgs" }
        log(TAG, VERBOSE) { libraryPkgs.joinToString { "$it\n" } }

        libraryPkgs
    }


    private fun SharedLibraryInfo.clawOutPath(): LocalPath? {
        val path = try {
            val parcel = Parcel.obtain()
            parcel.writeParcelable(this, 0)
            parcel.setDataPosition(0)
            val raw = String(parcel.marshall())
            parcel.recycle()
            LIBRARY_PATH_CLAW.find(raw)?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            log(TAG) { "Library claw failed on $this: ${e.asLog()}" }
            null
        }
        log(TAG) { "Clawed out library path: $path" }
        return path?.let { LocalPath.build(path) }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SharedLibraryPkgSource): PkgDataSource
    }

    companion object {
        private val LIBRARY_PATH_CLAW = Regex("^.+(/data/.+?\\.apk).+\$")
        private val TAG = logTag("PkgRepo", "Source", "SharedLibrary")
    }
}