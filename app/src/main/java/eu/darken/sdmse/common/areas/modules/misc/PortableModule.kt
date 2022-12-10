package eu.darken.sdmse.common.areas.modules.misc

import android.annotation.SuppressLint
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.StorageEnvironment
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.user.UserManager2
import javax.inject.Inject


@Reusable
class PortableModule @Inject constructor(
    private val environment: StorageEnvironment,
    private val userManager2: UserManager2,
    private val gatewaySwitch: GatewaySwitch,
) : DataAreaModule {

    override suspend fun firstPass(): Collection<DataArea> {
        val gateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway

        if (!gateway.hasRoot()) {
            log(TAG, INFO) { "LocalGateway has no root, skipping." }
            return emptySet()
        }

        return loadPossibleUsbStorageLocations()
            .mapNotNull {
                if (!gateway.exists(it, mode = LocalGateway.Mode.AUTO)) {
                    return@mapNotNull null
                }
                log(TAG, INFO) { "Path exists: $it" }

                gateway.lookup(it, mode = LocalGateway.Mode.AUTO)
            }
            .map {
                DataArea(
                    type = DataArea.Type.PORTABLE,
                    path = it,
                    userHandle = userManager2.currentUser,
                )
            }


    }

    @SuppressLint("SdCardPath")
    private fun loadPossibleUsbStorageLocations(): Collection<LocalPath> {
        val maybes: MutableSet<LocalPath> = HashSet()
        // https://stackoverflow.com/a/35225140/1251958
        maybes.add(LocalPath.build("/mnt/usb_storage"))
        maybes.add(LocalPath.build("/mnt/sdcard/usbStorage"))
        maybes.add(LocalPath.build("/mnt/usbdrive0")) // Acer DA241HL Tablet @ 4.2.1
        maybes.add(LocalPath.build("/storage/removable/usbdisk"))
        maybes.add(LocalPath.build("/storage/usbdisk"))

        // (all Samsung devices)
        maybes.add(LocalPath.build("/storage/UsbDriveA"))
        maybes.add(LocalPath.build("/storage/UsbDriveB"))
        maybes.add(LocalPath.build("/storage/UsbDriveC"))
        maybes.add(LocalPath.build("/storage/USBstorage1")) // LG G4, V10, G3, G2, other LG devices
        maybes.add(LocalPath.build("/storage/usbdisk")) // Moto Maxx, Turbo 2, Moto X Pure, other Motorola devices
        maybes.add(LocalPath.build("/storage/usbotg")) // Sony Xperia devices, Lenovo Tabs
        maybes.add(LocalPath.build("/storage/UDiskA")) // Oppo devices
        maybes.add(LocalPath.build("/storage/usb-storage")) // Acer Iconia Tabs
        maybes.add(LocalPath.build("/storage/usbcard")) // Dell Venue--Vanilla Android 4.3 tablet
        maybes.add(LocalPath.build("/storage/usb")) // HTC One M7, and some Vanilla Android devices

        for (variable in ENVIRONMENT_VARIABLES_USB) {
            val path = environment.getVariable(variable)
            if (path != null && path.isNotEmpty() && !path.contains(":")) {
                maybes.add(LocalPath.build(path))
            }
        }

        return maybes
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PortableModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "Portable")
        private val ENVIRONMENT_VARIABLES_USB = arrayOf(
            "USBHOST_STORAGE",
            "THIRD_VOLUME_STORAGE",
            "USBOTG_STORAGE",
            "SECONDARY_STORAGE_USB"
        )
    }
}