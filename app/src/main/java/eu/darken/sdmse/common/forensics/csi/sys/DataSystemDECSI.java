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
//public class CSIDataSystemDE extends CSIProcessor {
//    static final String TAG = App.logTag("CSIDataSystemDE");
//
//    public CSIDataSystemDE(FileForensics fileForensics) {
//        super(fileForensics);
//    }
//
//    @Override
//    public boolean hasJurisdiction(@NonNull Location type) {
//        return type == Location.DATA_SYSTEM_DE;
//    }
//
//    @Nullable
//    @Override
//    public LocationInfo matchLocation(@NonNull SDMFile target) {
//        Collection<Storage> storages = getStorageManager().getStorages(Location.DATA_SYSTEM_DE, true);
//        for (Storage storage : storages) {
//            String base = storage.getFile().getPath() + File.separator;
//            if (target.getPath().startsWith(base)) {
//                return new LocationInfo(target, Location.DATA_SYSTEM_DE, base, false, storage);
//            }
//        }
//        return null;
//    }
//
//}
