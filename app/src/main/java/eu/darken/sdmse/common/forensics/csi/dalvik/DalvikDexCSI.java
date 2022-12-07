//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//import java.io.File;
//import java.util.Collection;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import eu.thedarken.sdm.App;
//import eu.thedarken.sdm.tools.forensics.FileForensics;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.forensics.csi.checks.ClutterCheck;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//
//public class CSIDalvikDex extends CSIProcessor {
//    static final String TAG = App.logTag("CSIDalvikDex");
//
//    private final DalvikCandidateGenerator sourceGenerator;
//    private final ClutterCheck clutterCheck;
//    private final CustomDexOptCheck customDexOptCheck;
//    private final SourceDirCheck sourceDirCheck;
//    private final ApkCheck apkCheck;
//    private final ExistCheck existCheck;
//    private final OddOnesCheck oddOnesCheck;
//
//    public CSIDalvikDex(FileForensics fileForensics) {
//        super(fileForensics);
//        sourceGenerator = new DalvikCandidateGenerator(this);
//        clutterCheck = new ClutterCheck(this);
//        customDexOptCheck = new CustomDexOptCheck(this);
//        sourceDirCheck = new SourceDirCheck(this);
//        apkCheck = new ApkCheck(this);
//        existCheck = new ExistCheck(this);
//        oddOnesCheck = new OddOnesCheck(this);
//    }
//
//
//    @Override
//    public boolean hasJurisdiction(@NonNull Location type) {
//        return type == Location.DALVIK_DEX;
//    }
//
//    @Nullable
//    @Override
//    public LocationInfo matchLocation(@NonNull SDMFile file) {
//        Collection<Storage> storages = getStorageManager().getStorages(Location.DALVIK_DEX, true);
//        for (Storage storage : storages) {
//            String base = storage.getFile().getPath() + File.separator;
//            if (file.getPath().startsWith(base)) {
//                return new LocationInfo(file, Location.DALVIK_DEX, base, true, storage);
//            }
//        }
//        return null;
//    }
//
//    @SuppressWarnings("UnnecessaryReturnStatement")
//    @Override
//    public void process(@NonNull OwnerInfo ownerInfo) {
//        Collection<SDMFile> candidates = sourceGenerator.getCandidates(ownerInfo.getItem());
//        if (sourceDirCheck.check(ownerInfo, candidates)) return;
//        if (customDexOptCheck.check(ownerInfo, candidates)) return;
//        if (clutterCheck.check(ownerInfo)) return;
//        if (apkCheck.check(ownerInfo, candidates)) return;
//        if (existCheck.check(ownerInfo, candidates)) return;
//        if (oddOnesCheck.check(ownerInfo)) return;
//    }
//
//}
