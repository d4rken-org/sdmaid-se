package eu.darken.sdmse.common.pkgs.sources

import android.content.pm.SharedLibraryInfo
import android.os.Parcel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgDataSource
import eu.darken.sdmse.common.pkgs.container.LibraryPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserManager2
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedLibraryPkgSource @Inject constructor(
    private val pkgOps: PkgOps,
    private val userManager2: UserManager2,
) : PkgDataSource {

    override suspend fun getPkgs(): Collection<Installed> = pkgOps.useRes {
        log(TAG, VERBOSE) { "getPkgs()" }

        val allUsers = userManager2.allUsers().map { it.handle }

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

                allUsers.map { handle ->
                    LibraryPkg(
                        sharedLibraryInfo = libraryInfo,
                        apkPath = apkPath,
                        packageInfo = apkInfo.packageInfo,
                        userHandle = handle,
                    )
                }
            }.flatten()

        log(TAG) { "Found ${libraryPkgs.size} library pkgs" }
        log(TAG, VERBOSE) { libraryPkgs.joinToString("\n") }

        libraryPkgs
    }

    private fun SharedLibraryInfo.clawOutPath(): LocalPath? {
        val path = try {
            val parcel = Parcel.obtain()
            parcel.writeParcelable(this, 0)
            parcel.setDataPosition(0)
            val raw = String(parcel.marshall())
            parcel.recycle()
            if (Bugs.isDebug) log(TAG, VERBOSE) { "Trying to claw: $raw" }
            getClawPatterns(this.name).firstNotNullOfOrNull {
                it.find(raw)?.groupValues?.getOrNull(1)
            }

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
        private val LIBRARY_DATA_PATH_CLAW = Regex("^.+(/data/.+?\\.apk).+\$")
        private val LIBRARY_PRODUCT_PATH_CLAW = Regex("^.+(/product/.+?\\.apk).+\$")
        private val LIBRARY_GENERIC_PATH_CLAW = Regex("((?:/\\w+)+/.+\\.apk)")
        internal fun getClawPatterns(pkgName: String): Set<Regex> {
            return setOf(
                Regex("((?:/\\w+)+/.+\\.apk)(?:\\W+#\\W+)(${Regex.escape(pkgName)})"),
                LIBRARY_DATA_PATH_CLAW,
                LIBRARY_PRODUCT_PATH_CLAW,
                LIBRARY_GENERIC_PATH_CLAW,
            )
        }

        private val TAG = logTag("PkgRepo", "Source", "SharedLibrary")
    }
}