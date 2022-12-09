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
//public class CSIDataSystem extends CSIProcessor {
//    static final String TAG = App.logTag("CSIDataSystem");
//
//    public CSIDataSystem(FileForensics fileForensics) {
//        super(fileForensics);
//    }
//
//    @Override
//    public boolean hasJurisdiction(@NonNull Location type) {
//        return type == Location.DATA_SYSTEM;
//    }
//
//    @Nullable
//    @Override
//    public LocationInfo matchLocation(@NonNull SDMFile target) {
//        Collection<Storage> storages = getStorageManager().getStorages(Location.DATA_SYSTEM, true);
//        for (Storage storage : storages) {
//            String base = storage.getFile().getPath() + File.separator;
//            if (target.getPath().startsWith(base)) {
//                return new LocationInfo(target, Location.DATA_SYSTEM, base, false, storage);
//            }
//        }
//        return null;
//    }
//
//}
