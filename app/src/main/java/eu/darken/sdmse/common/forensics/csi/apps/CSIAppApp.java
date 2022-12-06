//package eu.darken.sdmse.common.forensics.csi.apps;
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
//import eu.thedarken.sdm.tools.clutter.Marker;
//import eu.thedarken.sdm.tools.forensics.FileForensics;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.Owner;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSICheck;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.forensics.csi.appapp.DirToPkgCheck;import eu.thedarken.sdm.tools.forensics.csi.appapp.DirectApkCheck;import eu.thedarken.sdm.tools.forensics.csi.appapp.FileToPkgCheck;import eu.thedarken.sdm.tools.forensics.csi.appapp.LuckyPatcherCheck;import eu.thedarken.sdm.tools.forensics.csi.appapp.SimilarityCheck;import eu.thedarken.sdm.tools.forensics.csi.appapp.SubDirToPkgCheck;import eu.thedarken.sdm.tools.forensics.csi.checks.ClutterCheck;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//
//public class CSIAppApp extends CSIProcessor {
//    static final String TAG = App.logTag("CSIAppApp");
//    private final List<CSICheck> checks = new ArrayList<>();
//    private final SimilarityCheck similarityCheck;
//
//    public CSIAppApp(FileForensics fileForensics) {
//        super(fileForensics);
//        checks.add(new DirToPkgCheck(this));
//        if (ApiHelper.hasAndroid11()) checks.add(new SubDirToPkgCheck(this));
//        checks.add(new FileToPkgCheck(this));
//        checks.add(new LuckyPatcherCheck(this));
//        checks.add(new DirectApkCheck(this));
//        checks.add(new ApkDirCheck(this));
//        checks.add(new ClutterCheck(this));
//        similarityCheck = new SimilarityCheck(this);
//    }
//
//    @Override
//    public boolean hasJurisdiction(@NonNull Location type) {
//        return type == Location.APP_APP;
//    }
//
//    @Nullable
//    @Override
//    public LocationInfo matchLocation(@NonNull SDMFile target) {
//        Collection<Storage> storages = getStorageManager().getStorages(Location.APP_APP, true);
//        for (Storage storage : storages) {
//            String base = storage.getFile().getPath() + File.separator;
//            if (target.getPath().startsWith(base)) {
//                return new LocationInfo(target, Location.APP_APP, base, true, storage);
//            }
//        }
//        return null;
//    }
//
//
//    // <5.0 devices have /data/app/<pkg>.apk
//    // 5.0 devices have /data/app/<pkg>/base.apk
//    // 5.1 devices have a mix of both -_-    @Override
//    // 8.0 devices have /data/app/<pkg>-random/base.apk
//    // 11.0 (like 8.0) devices have /data/app/com.google.audio.hearing.visualization.accessibility.scribe-A8Z2KHvb6Tz290E6hedJTw==/base.apk
//    // 11.0 devices have /data/app/~~XQHB35lfqGmyB9HMHDbu-w==/com.android.chrome-ZoTGgkXBDD_n7iNl4-aM7A==/base.apk
//    public void process(@NonNull OwnerInfo ownerInfo) {
//        for (CSICheck check : checks) {
//            if (check.check(ownerInfo)) {
//                boolean hasNonCustodian = false;
//                for (Owner owner : ownerInfo.getOwners()) {
//                    if (!owner.hasFlag(Marker.Flag.CUSTODIAN)) {
//                        // Custodians can't be used to check for false positives via ApplicationInfo.sourceDir
//                        hasNonCustodian = true;
//                        break;
//                    }
//                }
//                // Found a real owner (non custodian)
//                if (hasNonCustodian) break;
//            }
//        }
//        similarityCheck.check(ownerInfo);
//    }
//
//}
