//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.List;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import eu.thedarken.sdm.App;
//import eu.thedarken.sdm.tools.ApiHelper;
//import eu.thedarken.sdm.tools.forensics.FileForensics;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSICheck;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.forensics.csi.checks.ClutterCheck;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//
//public class CSIDalvikProfile extends CSIProcessor {
//    static final String TAG = App.logTag("CSIDalvikProfile");
//    private final List<CSICheck> checks = new ArrayList<>();
//
//    public CSIDalvikProfile(FileForensics fileForensics) {
//        super(fileForensics);
//        checks.add(new DirNameCheck(this));
//        checks.add(new ClutterCheck(this));
//    }
//
//    @Override
//    public boolean hasJurisdiction(@NonNull Location type) {
//        return type == Location.DALVIK_PROFILE;
//    }
//
//    @Nullable
//    @Override
//    public LocationInfo matchLocation(@NonNull SDMFile file) {
//        if (ApiHelper.hasLolliPop()) {
//            Collection<Storage> storages = getStorageManager().getStorages(Location.DALVIK_PROFILE, true);
//            for (Storage storage : storages) {
//                String base = storage.getFile().getPath() + File.separator;
//                if (file.getPath().startsWith(base)) {
//                    return new LocationInfo(file, Location.DALVIK_PROFILE, base, true, storage);
//                }
//            }
//        }
//        return null;
//    }
//
//    @Override
//    public void process(@NonNull OwnerInfo ownerInfo) {
//        for (CSICheck check : checks) {
//            if (check.check(ownerInfo)) return;
//        }
//    }
//}
