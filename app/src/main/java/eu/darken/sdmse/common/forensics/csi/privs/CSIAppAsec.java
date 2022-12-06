//package eu.darken.sdmse.common.forensics.csi.privs;
//
//import java.io.File;
//import java.util.Collection;
//import java.util.HashSet;
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
//import eu.thedarken.sdm.tools.storage.Mount;
//import eu.thedarken.sdm.tools.storage.Storage;
//
//public class CSIAppAsec extends CSIProcessor {
//    static final String TAG = App.logTag("CSIAppAsec");
//    public static final String DIRNAME = "app-asec";
//
//    public CSIAppAsec(FileForensics fileForensics) {
//        super(fileForensics);
//    }
//
//    @Override
//    public boolean hasJurisdiction(@NonNull Location type) {
//        return type == Location.APP_ASEC;
//    }
//
//    @Nullable
//    @Override
//    public LocationInfo matchLocation(@NonNull SDMFile target) {
//        Collection<Storage> storages = getStorageManager().getStorages(Location.APP_ASEC, true);
//        for (Storage storage : storages) {
//            String base = storage.getFile().getPath() + File.separator;
//            if (target.getPath().startsWith(base)) {
//                return new LocationInfo(target, Location.APP_ASEC, base, true, storage);
//            }
//        }
//        return null;
//    }
//
//    private static final Pattern ASEC_FILE = Pattern.compile("^([\\w.\\-]+)(?:\\-[0-9]{1,4}.asec)$");
//
//    @Override
//    public void process(@NonNull OwnerInfo ownerInfo) {
//        String dirName = FileOpsHelper.getFirstDirName(ownerInfo.getLocationInfo().getPrefixFreePath());
//        boolean confirmed = false;
//        Matcher m = ASEC_FILE.matcher(dirName);
//        if (m.matches() && getAppMap().containsKey(m.group(1))) {
//            SDMPkgInfo packageInfo = getAppMap().get(m.group(1));
//            ownerInfo.addOwner(new Owner(packageInfo.getPackageName()));
//            confirmed = true;
//        }
//        if (!confirmed) {
//            Collection<Marker.Match> matches = getClutterRepository().match(ownerInfo.getLocationInfo().getLocation(), dirName);
//            ownerInfo.addOwners(matches);
//
//            Collection<String> candidates = new HashSet<>();
//            candidates.add(ownerInfo.getItem().getName());
//            candidates.add(ownerInfo.getItem().getName().replace(".asec", ""));
//            for (Mount p : getStorageManager().getAllMounts()) {
//                String mountPath = p.getMountpoint().getPath();
//                for (String canidate : candidates) {
//                    if (mountPath.contains(canidate)) {
//                        ownerInfo.setUnknownOwner(true);
//                        break;
//                    }
//                }
//                if (ownerInfo.hasUnknownOwner()) break;
//            }
//        }
//    }
//
//}
