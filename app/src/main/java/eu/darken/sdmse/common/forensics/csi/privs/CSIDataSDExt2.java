//package eu.darken.sdmse.common.forensics.csi.privs;
//
//import java.io.File;
//import java.util.Collection;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import eu.darken.sdmse.common.forensics.CSIProcessor;
//import eu.thedarken.sdm.App;
//import eu.thedarken.sdm.tools.forensics.FileForensics;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//
//public class CSIDataSDExt2 extends CSIProcessor {
//    static final String TAG = App.logTag("CSIDataSDExt2");
//
//    public CSIDataSDExt2(FileForensics fileForensics) {
//        super(fileForensics);
//    }
//
//    @Override
//    public boolean hasJurisdiction(@NonNull Location type) {
//        return type == Location.DATA_SDEXT2;
//    }
//
//    @Nullable
//    @Override
//    public LocationInfo matchLocation(@NonNull SDMFile target) {
//        Collection<Storage> storages = getStorageManager().getStorages(Location.DATA_SDEXT2, true);
//        for (Storage storage : storages) {
//            String base = storage.getFile().getPath() + File.separator;
//            if (target.getPath().startsWith(base)) {
//                return new LocationInfo(target, Location.DATA_SDEXT2, base, false, storage);
//            }
//        }
//        return null;
//    }
//
//}
