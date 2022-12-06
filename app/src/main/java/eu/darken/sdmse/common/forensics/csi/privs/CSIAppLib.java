//package eu.darken.sdmse.common.forensics.csi.privs;
//
//import java.io.File;
//import java.util.Collection;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import eu.darken.sdmse.common.forensics.CSIProcessor;
//import eu.thedarken.sdm.App;
//import eu.thedarken.sdm.tools.apps.SDMPkgInfo;
//import eu.thedarken.sdm.tools.clutter.Marker;
//import eu.thedarken.sdm.tools.forensics.FileForensics;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.Owner;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.io.FileOpsHelper;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//
//public class CSIAppLib extends CSIProcessor {
//    static final String TAG = App.logTag("CSIAppLib");
//    public static final String DIRNAME = "app-lib";
//
//    public CSIAppLib(FileForensics fileForensics) {
//        super(fileForensics);
//    }
//
//    @Override
//    public boolean hasJurisdiction(@NonNull Location type) {
//        return type == Location.APP_LIB;
//    }
//
//    @Nullable
//    @Override
//    public LocationInfo matchLocation(@NonNull SDMFile file) {
//        Collection<Storage> storages = getStorageManager().getStorages(Location.APP_LIB, true);
//        for (Storage storage : storages) {
//            String base = storage.getFile().getPath() + File.separator;
//            if (file.getPath().startsWith(base)) {
//                return new LocationInfo(file, Location.APP_LIB, base, true, storage);
//            }
//        }
//        return null;
//    }
//
//    private static final Pattern APPLIB_DIR = Pattern.compile("^([\\w.\\-]+)(?:\\-[0-9]{1,4})$");
//
//    @Override
//    public void process(@NonNull OwnerInfo ownerInfo) {
//        boolean confirmed = false;
//
//        String dirName = FileOpsHelper.getFirstDirName(ownerInfo.getLocationInfo().getPrefixFreePath());
//        Matcher m = APPLIB_DIR.matcher(dirName);
//        if (m.matches() && getAppMap().containsKey(m.group(1))) {
//            SDMPkgInfo packageInfo = getAppMap().get(m.group(1));
//            ownerInfo.addOwner(new Owner(packageInfo.getPackageName()));
//            confirmed = true;
//        }
//        if (!confirmed) {
//            for (SDMPkgInfo app : getApps()) {
//                if (app.getApplicationInfo() == null) continue;
//                if (ownerInfo.getItem().getPath().equals(app.getApplicationInfo().nativeLibraryDir) || ownerInfo.getItem().getPath().startsWith(app.getApplicationInfo().nativeLibraryDir + File.separator)) {
//                    ownerInfo.addOwner(new Owner(app.getPackageName()));
//                    confirmed = true;
//                }
//            }
//        }
//        if (!confirmed) {
//            Collection<Marker.Match> matches = getClutterRepository().match(ownerInfo.getLocationInfo().getLocation(), dirName);
//            ownerInfo.addOwners(matches);
//        }
//    }
//
//}
