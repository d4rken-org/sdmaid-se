//package eu.thedarken.sdmse.tools.storage.modules.misc
//
//import android.annotation.SuppressLint
//import eu.thedarken.sdmse.tools.forensics.Location
//import eu.thedarken.sdmse.tools.io.JavaFile
//import eu.thedarken.sdmse.tools.io.SDMFile
//import eu.thedarken.sdmse.tools.storage.FileSystem
//import eu.thedarken.sdmse.tools.storage.Storage
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactory
//import eu.thedarken.sdmse.tools.storage.modules.StorageFactoryModule
//import java.util.*
//
//internal class PortableModule(storageFactory: StorageFactory) : StorageFactoryModule(storageFactory) {
//    public override fun build(storageMap: Map<Location, Collection<Storage>>): Collection<Storage> {
//        val results = mutableSetOf<Storage>()
//
//        // Search for external sdcards in mountpoints
//        val knownUSBStorages = loadPossibleUsbStorageLocations()
//        for (mount in mounts) {
//            // https://github.com/d4rken/sdmaid-public/issues/1277
//            if (mount.fileSystemType == FileSystem.TMPFS) continue
//            val canidate = mount.mountpoint
//            if (knownUSBStorages.contains(canidate)) {
//                var skip = false
//                for ((file) in results) {
//                    if (file == canidate) skip = true
//                }
//                if (skip) continue
//                Storage(
//                        location = Location.PORTABLE,
//                        mount = mount,
//                        file = mount.mountpoint
//                ).let { results.add(it) }
//            }
//        }
//        return results
//    }
//
//    @SuppressLint("SdCardPath") private fun loadPossibleUsbStorageLocations(): Collection<SDMFile> {
//        val possibleUsbStorages: MutableSet<SDMFile> = HashSet()
//        // https://stackoverflow.com/a/35225140/1251958
//        possibleUsbStorages.add(JavaFile.build("/mnt/usb_storage"))
//        possibleUsbStorages.add(JavaFile.build("/mnt/sdcard/usbStorage"))
//        possibleUsbStorages.add(JavaFile.build("/mnt/usbdrive0")) // Acer DA241HL Tablet @ 4.2.1
//        possibleUsbStorages.add(JavaFile.build("/storage/removable/usbdisk"))
//        possibleUsbStorages.add(JavaFile.build("/storage/usbdisk"))
//
//        // (all Samsung devices)
//        possibleUsbStorages.add(JavaFile.build("/storage/UsbDriveA"))
//        possibleUsbStorages.add(JavaFile.build("/storage/UsbDriveB"))
//        possibleUsbStorages.add(JavaFile.build("/storage/UsbDriveC"))
//        possibleUsbStorages.add(JavaFile.build("/storage/USBstorage1")) // LG G4, V10, G3, G2, other LG devices
//        possibleUsbStorages.add(JavaFile.build("/storage/usbdisk")) // Moto Maxx, Turbo 2, Moto X Pure, other Motorola devices
//        possibleUsbStorages.add(JavaFile.build("/storage/usbotg")) // Sony Xperia devices, Lenovo Tabs
//        possibleUsbStorages.add(JavaFile.build("/storage/UDiskA")) // Oppo devices
//        possibleUsbStorages.add(JavaFile.build("/storage/usb-storage")) // Acer Iconia Tabs
//        possibleUsbStorages.add(JavaFile.build("/storage/usbcard")) // Dell Venue--Vanilla Android 4.3 tablet
//        possibleUsbStorages.add(JavaFile.build("/storage/usb")) // HTC One M7, and some Vanilla Android devices
//        for (variable in ENVIRONMENT_VARIABLES_USB) {
//            val path = environment.getVariable(variable)
//            if (path != null && path.isNotEmpty() && !path.contains(":")) {
//                possibleUsbStorages.add(JavaFile.build(path))
//            }
//        }
//        return possibleUsbStorages
//    }
//
//    companion object {
//        private val ENVIRONMENT_VARIABLES_USB = arrayOf(
//                "USBHOST_STORAGE",
//                "THIRD_VOLUME_STORAGE",
//                "USBOTG_STORAGE",
//                "SECONDARY_STORAGE_USB"
//        )
//    }
//}