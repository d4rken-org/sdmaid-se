package eu.darken.sdmse.common.pkgs.sources

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.debug.logging.*
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgDataSource
import eu.darken.sdmse.common.pkgs.container.HiddenPkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.StorageEnvironment
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemManifestPkgSource @Inject constructor(
    private val storageEnvironment: StorageEnvironment,
    private val gatewaySwitch: GatewaySwitch,
) : PkgDataSource {


    override suspend fun getPkgs(): Collection<Installed> = gatewaySwitch.useRes {
        log(TAG) { "getPkgs()" }

        val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!localGateway.hasRoot()) return@useRes emptySet()

        val pkgs = mutableSetOf<HiddenPkg>()

        val target = LocalPath.build(storageEnvironment.dataDir, "/system/packages.xml")

//        try {
//
//            val docFactory = DocumentBuilderFactory.newInstance()
//            val docBuilder = docFactory.newDocumentBuilder()
//
//            val document: Document = localGateway.read(target).buffer().use { fileSource ->
//                docBuilder.parse(fileSource.readUtf8())
//            }
//
//            document.documentElement
//                ?.let { root ->
//                    val children = root.childNodes
//                    for (i in 0 until children.length) {
//                        if (children.item(i) == null) continue
//
//                        if (children.item(i).nodeName == "package") {
//                            val entry = children.item(i) as Element
//
//                            val pkgName = entry.getAttribute("name")
//                            if (pkgName.isNullOrEmpty()) continue
//
//                            val pkgInfo = PackageInfo().apply {
//                                this.packageName = pkgName
//
//                                entry.getAttribute("ut")
//                                    ?.takeIf { it.isNotEmpty() }
//                                    ?.let { this.lastUpdateTime = java.lang.Long.parseLong(it, 16) }
//                                entry.getAttribute("it")
//                                    ?.takeIf { it.isNotEmpty() }
//                                    ?.let { this.firstInstallTime = java.lang.Long.parseLong(it, 16) }
//                            }
//
//                            pkgs.add(HiddenPkg(packageInfo = pkgInfo))
//                        }
//                    }
//
//                }
//
//        } catch (e: Exception) {
//            log(TAG, ERROR) { "Failed to read packages.xml: ${e.asLog()}" }
//        }

        pkgs
    }


    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: SystemManifestPkgSource): PkgDataSource
    }

    companion object {
        private val TAG = logTag("PkgRepo", "Source", "SystemManifest")
    }
}